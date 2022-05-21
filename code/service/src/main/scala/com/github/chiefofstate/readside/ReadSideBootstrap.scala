/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import akka.actor.typed.ActorSystem
import com.github.chiefofstate.config.{ ReadSideConfig, ReadSideConfigReader }
import com.github.chiefofstate.protobuf.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.github.chiefofstate.utils.NettyHelper
import com.typesafe.config.Config
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import io.grpc.ClientInterceptor
import org.slf4j.{ Logger, LoggerFactory }

/**
 * Used to configure and start all read side processors
 *
 * @param system actor system
 * @param interceptors sequence of interceptors for the gRPC client
 * @param dbConfig the DB config for creating a hikari data source
 * @param readSideConfigs sequence of configs for specific read sides
 * @param numShards number of shards for projections/tags
 * @param readSideManager specifies the readSide manager
 */
class ReadSideBootstrap(
    system: ActorSystem[_],
    interceptors: Seq[ClientInterceptor],
    dbConfig: ReadSideBootstrap.DbConfig,
    readSideConfigs: Seq[ReadSideConfig],
    numShards: Int,
    readSideManager: ReadSideManager) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[readside] lazy val dataSource: HikariDataSource =
    ReadSideBootstrap.getDataSource(dbConfig)

  def init(): Unit = {
    logger.info(s"initializing read sides, count=${readSideConfigs.size}")

    // configure each read side
    readSideConfigs.foreach { config =>
      logger.info(s"starting read side, id=${config.readSideId}")

      // construct a remote gRPC read side client for this read side
      // and register interceptors
      val rpcClient: ReadSideHandlerServiceBlockingStub = new ReadSideHandlerServiceBlockingStub(
        NettyHelper.builder(config.host, config.port, config.useTls).build).withInterceptors(interceptors: _*)
      // instantiate a remote read side processor with the gRPC client
      val remoteReadSideProcessor: ReadSideHandlerImpl =
        new ReadSideHandlerImpl(config.readSideId, rpcClient)
      // instantiate the read side projection with the remote processor
      val projection =
        new ReadSideProjection(system, config.readSideId, dataSource, remoteReadSideProcessor, numShards)
      // start the sharded daemon process
      projection.start()

      // pause readSide for all shards after starting it if pause on start is enable
      if (config.pausedOnStart) {
        readSideManager.pauseForAll(config.readSideId)
      }
    }
  }
}

object ReadSideBootstrap {

  def apply(
      system: ActorSystem[_],
      interceptors: Seq[ClientInterceptor],
      numShards: Int,
      readSideManager: ReadSideManager): ReadSideBootstrap = {

    val dbConfig: DbConfig = {
      // read the jdbc-default settings
      val jdbcCfg: Config = system.settings.config.getConfig("jdbc-default")

      DbConfig(jdbcCfg)
    }

    // get the individual read side configs
    val configs: Seq[ReadSideConfig] = ReadSideConfigReader.getReadSideSettings
    // make the manager
    new ReadSideBootstrap(
      system = system,
      interceptors = interceptors,
      dbConfig = dbConfig,
      readSideConfigs = configs,
      numShards = numShards,
      readSideManager)
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
      maxLifetimeMs: Long)

  private[readside] object DbConfig {
    def apply(jdbcCfg: Config): DbConfig =
      DbConfig(
        jdbcUrl = jdbcCfg.getString("url"),
        username = jdbcCfg.getString("user"),
        password = jdbcCfg.getString("password"),
        maxPoolSize = jdbcCfg.getInt("hikari-settings.max-pool-size"),
        minIdleConnections = jdbcCfg.getInt("hikari-settings.min-idle-connections"),
        idleTimeoutMs = jdbcCfg.getLong("hikari-settings.idle-timeout-ms"),
        maxLifetimeMs = jdbcCfg.getLong("hikari-settings.max-lifetime-ms"))
  }
}
