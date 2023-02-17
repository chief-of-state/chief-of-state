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
import com.github.chiefofstate.interceptors.MetadataInterceptor
import com.github.chiefofstate.protobuf.v1.internal.{ MigrationFailed, MigrationSucceeded }
import com.github.chiefofstate.protobuf.v1.readside_manager.ReadSideManagerServiceGrpc.ReadSideManagerService
import com.github.chiefofstate.protobuf.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.readside.{ ReadSideBootstrap, ReadSideManager }
import com.github.chiefofstate.services.{ ReadSideManagerServiceImpl, ServiceImpl }
import com.github.chiefofstate.utils.{ NettyHelper, ProtosValidator, Util }
import com.typesafe.config.Config
import io.grpc._
import io.grpc.netty.NettyServerBuilder
import org.slf4j.{ Logger, LoggerFactory }

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.sys.ShutdownHookThread

/**
 * This helps init the required engines needed to smoothly run the ChiefOfState sevice.
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
        // We only proceed when the data stores and various migrations are done successfully.
        log.info("Data store migration complete. About to start...")

        val channel: ManagedChannel =
          NettyHelper
            .builder(cosConfig.writeSideConfig.host, cosConfig.writeSideConfig.port, cosConfig.writeSideConfig.useTls)
            .build()

        val writeHandler: WriteSideHandlerServiceBlockingStub =
          new WriteSideHandlerServiceBlockingStub(channel)

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

        // create an instance of the readSide manager
        val readSideManager = new ReadSideManager(context.system, cosConfig.eventsConfig.numShards)

        // read side service
        startReadSides(context.system, cosConfig, readSideManager)

        // start the service
        startServices(context.system, sharding, cosConfig, readSideManager)

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
   * @param cosConfig the cos specific configuration
   */
  private def startServices(
      system: ActorSystem[_],
      clusterSharding: ClusterSharding,
      cosConfig: CosConfig,
      readSideManager: ReadSideManager): ShutdownHookThread = {
    implicit val askTimeout: Timeout = cosConfig.askTimeout

    // create the traced execution context for grpc
    val grpcEc: ExecutionContext = system.executionContext

    // instantiate the grpc service, bind to the execution context
    val serviceImpl: ServiceImpl =
      new ServiceImpl(clusterSharding, cosConfig.writeSideConfig)

    // create an instance of the read side state manager service
    val readSideStateServiceImpl = new ReadSideManagerServiceImpl(readSideManager)(grpcEc)

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
  private def startReadSides(system: ActorSystem[_], cosConfig: CosConfig, readSideManager: ReadSideManager): Unit = {
    // if read side is enabled
    if (cosConfig.enableReadSide) {
      // instantiate a read side manager
      val readSideBootstrap: ReadSideBootstrap =
        ReadSideBootstrap(
          system = system,
          numShards = cosConfig.eventsConfig.numShards,
          readSideManager,
          cosConfig.grpcConfig)
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
    ServerInterceptors.intercept(serviceDefinition, MetadataInterceptor)
  }
}
