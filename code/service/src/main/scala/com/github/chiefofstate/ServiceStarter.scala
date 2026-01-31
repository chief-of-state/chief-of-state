/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import com.github.chiefofstate.config.CosConfig
import com.github.chiefofstate.interceptors.MetadataInterceptor
import com.github.chiefofstate.protobuf.v1.internal.{MigrationFailed, MigrationSucceeded}
import com.github.chiefofstate.protobuf.v1.manager.ReadSideManagerServiceGrpc.ReadSideManagerService
import com.github.chiefofstate.protobuf.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.readside.{ReadSideManager, ReadSideServiceStarter}
import com.github.chiefofstate.services.{CosReadSideManagerService, CosService}
import com.github.chiefofstate.utils.{Netty, Validator, Util}
import com.github.chiefofstate.writeside.{CommandHandler, EventHandler}
import com.typesafe.config.Config
import io.grpc._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.pattern.CircuitBreaker
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.util.Timeout
import org.slf4j.{Logger, LoggerFactory}

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
object ServiceStarter {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[scalapb.GeneratedMessage] =
    Behaviors.setup[scalapb.GeneratedMessage] { context =>
      // get the  COS config
      val cosConfig: CosConfig = CosConfig(config)

      Behaviors.receiveMessage[scalapb.GeneratedMessage] {
        // handle successful migration proceed with the rest of startup
        case _: MigrationSucceeded =>
          // We only proceed when the data stores and various migrations are done successfully.
          log.info("Data store migration complete. About to start...")

          val channel: ManagedChannel =
            Netty
              .channelBuilder(
                cosConfig.writeSideConfig.host,
                cosConfig.writeSideConfig.port,
                cosConfig.writeSideConfig.useTls
              )
              .build()

          val writeHandler: WriteSideHandlerServiceBlockingStub =
            new WriteSideHandlerServiceBlockingStub(channel)

          // Create circuit breaker for write-side if enabled
          val writeSideCircuitBreaker: Option[CircuitBreaker] =
            if (cosConfig.writeSideConfig.circuitBreakerConfig.enabled) {
              val cbConfig = cosConfig.writeSideConfig.circuitBreakerConfig
              log.info(
                s"Initializing write-side circuit breaker: maxFailures=${cbConfig.maxFailures}, " +
                  s"callTimeout=${cbConfig.callTimeout}, resetTimeout=${cbConfig.resetTimeout}"
              )
              val breaker = new CircuitBreaker(
                context.system.classicSystem.scheduler,
                maxFailures = cbConfig.maxFailures,
                callTimeout = cbConfig.callTimeout,
                resetTimeout = cbConfig.resetTimeout
              )(context.executionContext)
                .onOpen(log.warn("Write-side circuit breaker opened"))
                .onClose(log.info("Write-side circuit breaker closed"))
                .onHalfOpen(log.info("Write-side circuit breaker half-open"))
              Some(breaker)
            } else {
              log.info("Write-side circuit breaker disabled")
              None
            }

          val remoteCommandHandler: CommandHandler =
            CommandHandler(cosConfig.grpcConfig, writeHandler, writeSideCircuitBreaker)
          val remoteEventHandler: EventHandler =
            EventHandler(cosConfig.grpcConfig, writeHandler, writeSideCircuitBreaker)

          // instance of eventsAndStatesProtoValidation
          val eventsAndStateProtoValidation: Validator =
            Validator(cosConfig.writeSideConfig)

          // initialize the sharding extension
          val sharding: ClusterSharding = ClusterSharding(context.system)

          // initialize the shard region
          sharding.init(Entity(typeKey = com.github.chiefofstate.Entity.TypeKey) { entityContext =>
            com.github.chiefofstate.Entity(
              PersistenceId.ofUniqueId(entityContext.entityId),
              Util.getShardIndex(entityContext.entityId, cosConfig.shardConfig.numShards),
              cosConfig,
              remoteCommandHandler,
              remoteEventHandler,
              eventsAndStateProtoValidation
            )
          })

          // create an instance of the readSide manager
          val readSideManager =
            new ReadSideManager(context.system, cosConfig.shardConfig.numShards)

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
      readSideManager: ReadSideManager
  ): ShutdownHookThread = {
    implicit val askTimeout: Timeout = cosConfig.askTimeout

    // create the traced execution context for grpc
    val grpcEc: ExecutionContext = system.executionContext

    // instantiate the grpc service, bind to the execution context
    val coSService: CosService =
      new CosService(clusterSharding, cosConfig.writeSideConfig)(askTimeout, grpcEc)

    // create an instance of the read side state manager service
    val readSideManagerService = new CosReadSideManagerService(readSideManager)(grpcEc)

    // create the server builder
    var builder = Netty
      .serverBuilder(cosConfig.grpcConfig.server.host, cosConfig.grpcConfig.server.port)
      .addService(setServiceWithInterceptors(ChiefOfStateService.bindService(coSService, grpcEc)))

    // only start the read side manager if readSide is enabled
    if (cosConfig.enableReadSide)
      builder = builder.addService(
        setServiceWithInterceptors(
          ReadSideManagerService.bindService(readSideManagerService, grpcEc)
        )
      )

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
   * sets gRPC service definitions with the default interceptors
   *
   * @param serviceDefinition the service definition
   * @return a new ServerServiceDefinition with the various interceptors
   */
  private def setServiceWithInterceptors(
      serviceDefinition: ServerServiceDefinition
  ): ServerServiceDefinition = {
    ServerInterceptors.intercept(serviceDefinition, MetadataInterceptor)
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
      readSideManager: ReadSideManager
  ): Unit = {
    // if read side is enabled
    if (cosConfig.enableReadSide) {
      // instantiate a read side manager
      val readsideEntrypoint: ReadSideServiceStarter =
        ReadSideServiceStarter(
          system = system,
          numShards = cosConfig.shardConfig.numShards,
          readSideManager
        )
      // initialize all configured read sides
      readsideEntrypoint.init()
    }
  }
}
