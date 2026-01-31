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
   * creates a new CosConfig instance with validation
   *
   * @param config the config object
   * @return the newly created instance
   * @throws IllegalArgumentException if configuration is invalid
   */
  def apply(config: Config): CosConfig = {
    // Validate service name
    val serviceName = config.getString(serviceNameKey)
    require(serviceName.nonEmpty, s"$serviceNameKey cannot be empty")
    require(serviceName.length <= 64, s"$serviceNameKey is too long (max 64 characters)")

    // Validate ask timeout
    val askTimeoutSeconds = config.getInt(askTimeoutKey)
    require(askTimeoutSeconds > 0, s"$askTimeoutKey must be positive, got $askTimeoutSeconds")
    require(
      askTimeoutSeconds <= 300,
      s"$askTimeoutKey is too large (max 300 seconds), got $askTimeoutSeconds"
    )

    CosConfig(
      serviceName,
      Timeout(askTimeoutSeconds.seconds),
      SnapshotConfig(config),
      ShardConfig(config),
      GrpcConfig(config),
      WriteSideConfig(config),
      config.getBoolean(enableReadSideKey)
    )
  }

  /**
   * Load and validate configuration, providing detailed error messages on failure
   *
   * @param config the config object
   * @return Either an error message or a valid CosConfig
   */
  def load(config: Config): Either[String, CosConfig] = {
    try {
      Right(apply(config))
    } catch {
      case ex: IllegalArgumentException =>
        Left(s"Configuration validation failed: ${ex.getMessage}")
      case ex: com.typesafe.config.ConfigException =>
        Left(s"Configuration error: ${ex.getMessage}")
      case ex: Exception =>
        Left(s"Unexpected error loading configuration: ${ex.getMessage}")
    }
  }
}
