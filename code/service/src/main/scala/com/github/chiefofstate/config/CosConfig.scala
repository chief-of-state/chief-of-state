/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config
import org.apache.pekko.util.Timeout

import scala.concurrent.duration.DurationInt

/**
 * Main config
 *
 * @param serviceName the service name
 * @param askTimeout the timeout needed by the aggreegate to reply when handling a command
 * @param snapshotConfig the snapshot configuration
 * @param shardConfig the events configuration
 * @param grpcConfig the grpc config
 * @param writeSideConfig the commands/events handler config
 */
final case class CosConfig(
    serviceName: String,
    askTimeout: Timeout,
    snapshotConfig: SnapshotConfig,
    shardConfig: ShardConfig,
    grpcConfig: GrpcConfig,
    writeSideConfig: WriteSideConfig,
    enableReadSide: Boolean
)

object CosConfig {
  private val serviceNameKey: String    = "chiefofstate.service-name"
  private val askTimeoutKey: String     = "chiefofstate.ask-timeout"
  private val enableReadSideKey: String = "chiefofstate.read-side.enabled"

  /**
   * creates a new CosConfig instance
   *
   * @param config the config object
   * @return the newly created instance
   */
  def apply(config: Config): CosConfig = {
    CosConfig(
      config.getString(serviceNameKey),
      Timeout(config.getInt(askTimeoutKey).seconds),
      SnapshotConfig(config),
      ShardConfig(config),
      GrpcConfig(config),
      WriteSideConfig(config),
      config.getBoolean(enableReadSideKey)
    )
  }
}
