/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config

import java.time.Duration

/**
 * GrpcConfig reads the gRPC settings from the config
 *
 * @param client the grpc client general settings
 * @param server the grpc server setting
 */
case class GrpcConfig(client: GrpcClient, server: GrpcServer)

case class GrpcClient(
    timeout: Int,
    keepalive: Option[GrpcClientKeepalive] = None,
    poolSize: Int = 1
)

/**
 * HTTP/2 keepalive settings for outbound gRPC channels.
 *
 * @param timeSeconds     interval between keepalive pings (seconds)
 * @param timeoutSeconds  how long to wait for ping ack before closing (seconds)
 * @param withoutCalls    send pings even when there are no active RPCs
 */
case class GrpcClientKeepalive(timeSeconds: Long, timeoutSeconds: Long, withoutCalls: Boolean)

case class GrpcServer(address: String, port: Int)

object GrpcConfig {

  private val ClientKey       = "chiefofstate.grpc.client"
  private val KeepaliveKey    = s"$ClientKey.keepalive"
  private val DeadlineTimeout = s"$ClientKey.deadline-timeout"
  private val PoolSizeKey     = s"$ClientKey.pool-size"

  /**
   * creates a new instance of rhe GrpcConfig
   *
   * @param config the configuration object
   * @return a new instance of GrpcConfig
   */
  def apply(config: Config): GrpcConfig = {
    val keepalive: Option[GrpcClientKeepalive] =
      if (config.hasPath(s"$KeepaliveKey.enabled") && config.getBoolean(s"$KeepaliveKey.enabled")) {
        val time: Duration        = config.getDuration(s"$KeepaliveKey.time")
        val timeout: Duration     = config.getDuration(s"$KeepaliveKey.timeout")
        val withoutCalls: Boolean = config.getBoolean(s"$KeepaliveKey.without-calls")
        Some(
          GrpcClientKeepalive(
            timeSeconds = time.getSeconds,
            timeoutSeconds = timeout.getSeconds,
            withoutCalls = withoutCalls
          )
        )
      } else None

    val poolSize: Int = if (config.hasPath(PoolSizeKey)) {
      val n = config.getInt(PoolSizeKey)
      require(n >= 1, s"$PoolSizeKey must be >= 1, got $n")
      n
    } else 1

    GrpcConfig(
      GrpcClient(
        timeout = config.getInt(DeadlineTimeout),
        keepalive = keepalive,
        poolSize = poolSize
      ),
      GrpcServer(
        config.getString("chiefofstate.grpc.server.address"),
        config.getInt("chiefofstate.grpc.server.port")
      )
    )
  }
}
