/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity }
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.github.chiefofstate.config.CosConfig
import com.github.chiefofstate.handlers.{ RemoteCommandHandler, RemoteEventHandler }
import com.github.chiefofstate.services.{ CoSServiceImpl, ReadManagerServiceImpl }
import com.github.chiefofstate.protobuf.v1.internal.{ MigrationFailed, MigrationSucceeded }
import com.github.chiefofstate.protobuf.v1.readside_manager.ReadSideManagerServiceGrpc.ReadSideManagerService
import com.github.chiefofstate.protobuf.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.readside.{ ReadSideBootstrap, ReadSideManager }
import com.github.chiefofstate.utils.{ NettyHelper, ProtosValidator, Util }
import com.typesafe.config.Config
import io.grpc._
import io.grpc.netty.NettyServerBuilder
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.grpc.v1_5.GrpcTracing
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.superflat.otel.tools._
import org.slf4j.{ Logger, LoggerFactory }

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.sys.ShutdownHookThread

/**
 * This helps setup the required engines needed to smoothly run the ChiefOfState sevice.
 * The following engines are started on boot.
 * <ul>
 *   <li> the akka cluster sharding engine
 *   <li> loads the various ChiefOfState plugins
 *   <li> the telemetry tools and the various gRPC interceptors
 *   <li> the gRPC service
 * </ul>
 */
object ServiceBootstrapper {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[scalapb.GeneratedMessage] = Behaviors.setup[scalapb.GeneratedMessage] { context =>
    // get the  COS config
    val cosConfig: CosConfig = CosConfig(config)

    Behaviors.receiveMessage[scalapb.GeneratedMessage] {
      // handle successful migration proceed with the rest of startup
      case _: MigrationSucceeded =>
        // start the telemetry tools and register global tracer
        val otelSdk: OpenTelemetrySdk = TelemetryTools(cosConfig.telemetryConfig).start()
        GlobalOpenTelemetry.set(otelSdk)

        // We only proceed when the data stores and various migrations are done successfully.
        log.info("Data store migration complete. About to start...")

        val channel: ManagedChannel =
          NettyHelper
            .builder(cosConfig.writeSideConfig.host, cosConfig.writeSideConfig.port, cosConfig.writeSideConfig.useTls)
            .build()

        val grpcClientInterceptors: Seq[ClientInterceptor] =
          Seq(GrpcTracing.create(GlobalOpenTelemetry.get()).newClientInterceptor(), new StatusClientInterceptor())

        val writeHandler: WriteSideHandlerServiceBlockingStub =
          new WriteSideHandlerServiceBlockingStub(channel).withInterceptors(grpcClientInterceptors: _*)

        val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(cosConfig.grpcConfig, writeHandler)
        val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandler)

        // instance of eventsAndStatesProtoValidation
        val eventsAndStateProtoValidation: ProtosValidator = ProtosValidator(cosConfig.writeSideConfig)

        // initialize the sharding extension
        val sharding: ClusterSharding = ClusterSharding(context.system)

        // initialize the shard region
        sharding.init(Entity(typeKey = AggregateRoot.TypeKey) { entityContext =>
          AggregateRoot(
            PersistenceId.ofUniqueId(entityContext.entityId),
            Util.getShardIndex(entityContext.entityId, cosConfig.eventsConfig.numShards),
            cosConfig,
            remoteCommandHandler,
            remoteEventHandler,
            eventsAndStateProtoValidation)
        })

        // read side service
        startReadSides(context.system, cosConfig, grpcClientInterceptors)

        // start the service
        startServices(context.system, sharding, config, cosConfig)

        Behaviors.same

      // handle failed migration
      case msg: MigrationFailed =>
        log.error(s"migration failed: ${msg.errorMessage}")
        Behaviors.stopped

      // handle unknown message
      case unhandled =>
        log.warn(s"unhandled message ${unhandled.companion.scalaDescriptor.fullName}")

        Behaviors.stopped
    }
  }

  /**
   * starts the main application
   *
   * @param clusterSharding the akka cluster sharding
   * @param config the application configuration
   * @param cosConfig the cos specific configuration
   */
  private def startServices(
      system: ActorSystem[_],
      clusterSharding: ClusterSharding,
      config: Config,
      cosConfig: CosConfig): ShutdownHookThread = {
    implicit val askTimeout: Timeout = cosConfig.askTimeout

    // create the traced execution context for grpc
    val grpcEc: ExecutionContext = TracedExecutorService.get()

    // instantiate the grpc service, bind to the execution context
    val serviceImpl: CoSServiceImpl =
      new CoSServiceImpl(clusterSharding, cosConfig.writeSideConfig)

    // create an instance of the read side state manager service
    val readSideStateManager = new ReadSideManager(system, cosConfig.eventsConfig.numShards)
    val readSideStateServiceImpl = new ReadManagerServiceImpl(readSideStateManager)(grpcEc)

    // create the server builder
    var builder = NettyServerBuilder
      .forAddress(new InetSocketAddress(cosConfig.grpcConfig.server.host, cosConfig.grpcConfig.server.port))
      .addService(setServiceWithInterceptors(ChiefOfStateService.bindService(serviceImpl, grpcEc)))

    // only start the read side manager if readSide is enabled
    if (cosConfig.enableReadSide)
      builder = builder.addService(
        setServiceWithInterceptors(ReadSideManagerService.bindService(readSideStateServiceImpl, grpcEc)))

    // attach service to netty server
    val server: Server = builder.build().start()

    log.info("ChiefOfState Services started, listening on " + cosConfig.grpcConfig.server.port)
    server.awaitTermination()
    sys.addShutdownHook {
      log.info("shutting down ChiefOfState Services....")
      server.shutdown()
    }
  }

  /**
   * Start all the read side processors (akka projections)
   *
   * @param system actor system
   * @param cosConfig the chief of state config
   * @param interceptors gRPC client interceptors for remote calls
   */
  private def startReadSides(
      system: ActorSystem[_],
      cosConfig: CosConfig,
      interceptors: Seq[ClientInterceptor]): Unit = {
    // if read side is enabled
    if (cosConfig.enableReadSide) {
      // instantiate a read side manager
      val readSideBootstrap: ReadSideBootstrap =
        ReadSideBootstrap(system = system, interceptors = interceptors, numShards = cosConfig.eventsConfig.numShards)
      // initialize all configured read sides
      readSideBootstrap.init()
    }
  }

  /**
   * sets gRPC service definitions with the default interceptors
   *
   * @param serviceDefinition the service definition
   * @return a new ServerServiceDefinition with the various interceptors
   */
  private def setServiceWithInterceptors(serviceDefinition: ServerServiceDefinition): ServerServiceDefinition = {
    val tracingServerInterceptor: ServerInterceptor =
      GrpcTracing.create(GlobalOpenTelemetry.get()).newServerInterceptor()
    ServerInterceptors.intercept(
      serviceDefinition,
      tracingServerInterceptor,
      new StatusServerInterceptor(),
      GrpcHeadersInterceptor)
  }
}
