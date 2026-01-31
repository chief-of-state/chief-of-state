/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.config.{CircuitBreakerConfig, ReadSideConfig, ReadSideConfigReader}
import com.github.chiefofstate.protobuf.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.github.chiefofstate.utils.Netty
import com.typesafe.config.{Config, ConfigException}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.pattern.CircuitBreaker
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * Used to configure and start all read side processors
 *
 * @param system          actor system
 * @param dbConfig        the DB config for creating a hikari data source
 * @param readSideConfigs sequence of configs for specific read sides
 * @param numShards       number of shards for projections/tags
 * @param readSideManager specifies the readSide manager
 * @param circuitBreakerConfig circuit breaker configuration for read-side
 */
class ReadSideServiceStarter(
    system: ActorSystem[_],
    dbConfig: ReadSideServiceStarter.DbConfig,
    readSideConfigs: Seq[ReadSideConfig],
    numShards: Int,
    readSideManager: ReadSideManager,
    circuitBreakerConfig: CircuitBreakerConfig
) {
  private[readside] lazy val dataSource: HikariDataSource =
    ReadSideServiceStarter.getDataSource(dbConfig)
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // grab the execution context from the actor system
  implicit val ec: ExecutionContextExecutor = system.executionContext

  def init(): Unit = {
    logger.info(s"initializing read sides, count=${readSideConfigs.size}")

    // configure enabled read sides
    readSideConfigs.filter(c => c.enabled).foreach { config =>
      logger.info(s"starting read side: ${config.toString}")

      // Create circuit breaker for this read-side if enabled
      val readSideCircuitBreaker: Option[CircuitBreaker] =
        if (circuitBreakerConfig.enabled) {
          val cbConfig = circuitBreakerConfig
          logger.info(
            s"Initializing read-side circuit breaker for ${config.readSideId}: " +
              s"maxFailures=${cbConfig.maxFailures}, callTimeout=${cbConfig.callTimeout}, " +
              s"resetTimeout=${cbConfig.resetTimeout}"
          )
          val breaker = new CircuitBreaker(
            system.classicSystem.scheduler,
            maxFailures = cbConfig.maxFailures,
            callTimeout = cbConfig.callTimeout,
            resetTimeout = cbConfig.resetTimeout
          )(ec)
            .onOpen(logger.warn(s"Read-side circuit breaker opened for ${config.readSideId}"))
            .onClose(logger.info(s"Read-side circuit breaker closed for ${config.readSideId}"))
            .onHalfOpen(
              logger.info(s"Read-side circuit breaker half-open for ${config.readSideId}")
            )
          Some(breaker)
        } else {
          logger.info(s"Read-side circuit breaker disabled for ${config.readSideId}")
          None
        }

      // construct a remote gRPC read side client for this read side
      // and register interceptors
      val rpcClient: ReadSideHandlerServiceBlockingStub =
        new ReadSideHandlerServiceBlockingStub(
          Netty.channelBuilder(config.host, config.port, config.useTls).build
        )
      // instantiate a remote read side processor with the gRPC client
      val remoteReadSideProcessor: HandlerImpl =
        new HandlerImpl(config.readSideId, rpcClient, readSideCircuitBreaker)
      // instantiate the read side projection with the remote processor
      val projection =
        new ReadSide(
          system,
          config.readSideId,
          dataSource,
          remoteReadSideProcessor,
          numShards,
          config.failurePolicy
        )

      Try {
        // start the projection
        projection.start()
        // check auto start
        if (config.autoStart) {
          // check whether the read side is paused or not
          readSideManager
            .isReadSidePaused(config.readSideId)
            .map(res => {
              // resume the read Side if it is paused in previous run
              if (res) {
                // attempt to resume a maybe paused read side
                readSideManager.resumeForAll(config.readSideId)
              }
            })
        } else {
          // pause readSide for all shards after starting it if pause on start is enable
          readSideManager.pauseForAll(config.readSideId)
        }
      } match {
        case Failure(exception) =>
          logger
            .error(s"fail to start read side=${config.readSideId}, cause=${exception.getMessage}")
        case Success(_) => logger.info(s"read side=${config.readSideId} started successfully.")
      }
    }
  }
}

object ReadSideServiceStarter {
  def apply(
      system: ActorSystem[_],
      numShards: Int,
      readSideManager: ReadSideManager
  ): ReadSideServiceStarter = {

    val dbConfig: DbConfig = {
      // read the jdbc-default settings
      val jdbcCfg: Config = system.settings.config.getConfig("jdbc-default")
      DbConfig(jdbcCfg)
    }

    // Circuit breaker config for read-side is optional - if missing, use disabled
    val circuitBreakerConfig: CircuitBreakerConfig = {
      val cbKey = "chiefofstate.read-side.circuit-breaker"
      if (system.settings.config.hasPath(cbKey)) {
        CircuitBreakerConfig(system.settings.config.getConfig(cbKey))
      } else {
        CircuitBreakerConfig.disabled()
      }
    }

    // fetch the read side config
    val configs: Seq[ReadSideConfig] = {
      // get the readside config file
      Try {
        system.settings.config.getString("chiefofstate.read-side.config-file")
      } match {
        case Success(fh) =>
          if (fh.nonEmpty) ReadSideConfigReader.read(fh) else ReadSideConfigReader.readFromEnvVars
        case Failure(_: ConfigException.Missing) => ReadSideConfigReader.readFromEnvVars
        case Failure(exception)                  => throw exception
      }
    }

    // make the manager
    new ReadSideServiceStarter(
      system = system,
      dbConfig = dbConfig,
      readSideConfigs = configs,
      numShards = numShards,
      readSideManager,
      circuitBreakerConfig
    )
  }

  /**
   * create a hikari data source using a dbconfig class
   *
   * @param dbConfig database configs
   * @return a hikari data source instance
   */
  def getDataSource(dbConfig: DbConfig): HikariDataSource = {
    // make a hikari config for this db
    val hikariCfg: HikariConfig = new HikariConfig()
    // apply settings
    hikariCfg.setPoolName("cos-readside-pool")
    // FIXME, apply schema here as a hikari setting?
    hikariCfg.setJdbcUrl(dbConfig.jdbcUrl)
    hikariCfg.setUsername(dbConfig.username)
    hikariCfg.setPassword(dbConfig.password)
    // turn off autocommit so that akka can control it
    hikariCfg.setAutoCommit(false)
    // set max pool size
    hikariCfg.setMaximumPoolSize(dbConfig.maxPoolSize)
    // set min pool size
    hikariCfg.setMinimumIdle(dbConfig.minIdleConnections)
    // set pool idle timeout
    hikariCfg.setIdleTimeout(dbConfig.idleTimeoutMs)
    // connection lifetime after close
    hikariCfg.setMaxLifetime(dbConfig.maxLifetimeMs)
    // return the data source
    new HikariDataSource(hikariCfg)
  }

  // convenience case class for passing around the hikari settings
  private[readside] case class DbConfig(
      jdbcUrl: String,
      username: String,
      password: String,
      maxPoolSize: Int,
      minIdleConnections: Int,
      idleTimeoutMs: Long,
      maxLifetimeMs: Long
  )

  private[readside] object DbConfig {
    def apply(config: Config): DbConfig =
      DbConfig(
        jdbcUrl = config.getString("url"),
        username = config.getString("user"),
        password = config.getString("password"),
        maxPoolSize = config.getInt("hikari-settings.max-pool-size"),
        minIdleConnections = config.getInt("hikari-settings.min-idle-connections"),
        idleTimeoutMs = config.getLong("hikari-settings.idle-timeout-ms"),
        maxLifetimeMs = config.getLong("hikari-settings.max-lifetime-ms")
      )
  }
}
