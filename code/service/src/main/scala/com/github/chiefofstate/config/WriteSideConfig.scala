/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config

/**
 * WriteHandler configuration
 *
 * @param protocol the protocol to use: "grpc" or "http"
 * @param host the write handler host
 * @param port the write handler port
 * @param useTls enable TLS for outbound write handler calls (gRPC: TLS, HTTP: https)
 * @param eventsProtos the list of the events proto messages package names
 * @param statesProtos the list of the states proto messages package names
 * @param propagatedHeaders the list of gRPC headers to propagate
 * @param persistedHeaders the list of gRPC headers to persist
 * @param circuitBreakerConfig circuit breaker configuration
 */
case class WriteSideConfig(
    protocol: String,
    host: String,
    port: Int,
    useTls: Boolean,
    enableProtoValidation: Boolean,
    eventsProtos: Seq[String],
    statesProtos: Seq[String],
    propagatedHeaders: Seq[String],
    persistedHeaders: Seq[String],
    circuitBreakerConfig: CircuitBreakerConfig
)

object WriteSideConfig {

  private val protocolKey: String          = "chiefofstate.write-side.protocol"
  private val hostKey: String              = "chiefofstate.write-side.host"
  private val portKey: String              = "chiefofstate.write-side.port"
  private val useTlsKey: String            = "chiefofstate.write-side.use-tls"
  private val protoValidationKey: String   = "chiefofstate.write-side.enable-protos-validation"
  private val eventsProtosKey: String      = "chiefofstate.write-side.events-protos"
  private val statesProtosKey: String      = "chiefofstate.write-side.states-protos"
  private val propagatedHeadersKey: String = "chiefofstate.write-side.propagated-headers"
  private val persistedHeadersKey: String  = "chiefofstate.write-side.persisted-headers"
  private val circuitBreakerKey: String    = "chiefofstate.write-side.circuit-breaker"

  /**
   * creates an instancee of WriteSideConfig
   *
   * @param config the configuration object
   * @return a new instance of WriteSideConfig
   */
  def apply(config: Config): WriteSideConfig = {
    // Circuit breaker config is optional - if missing, use disabled
    val cbConfig = if (config.hasPath(circuitBreakerKey)) {
      CircuitBreakerConfig(config.getConfig(circuitBreakerKey))
    } else {
      CircuitBreakerConfig.disabled()
    }

    // Protocol defaults to grpc for backward compatibility
    val protocol = if (config.hasPath(protocolKey)) {
      config.getString(protocolKey)
    } else {
      "grpc"
    }

    WriteSideConfig(
      protocol = protocol,
      host = config.getString(hostKey),
      port = config.getInt(portKey),
      useTls = config.getBoolean(useTlsKey),
      enableProtoValidation = config.getBoolean(protoValidationKey),
      eventsProtos = csvSplitDistinct(config.getString(eventsProtosKey)),
      statesProtos = csvSplitDistinct(config.getString(statesProtosKey)),
      propagatedHeaders = csvSplitDistinct(config.getString(propagatedHeadersKey)),
      persistedHeaders = csvSplitDistinct(config.getString(persistedHeadersKey)),
      circuitBreakerConfig = cbConfig
    )
  }

  /**
   * split config string on a comma
   *
   * @param s a string
   * @return a sequence of values
   */
  private def csvSplitDistinct(s: String): Seq[String] = {
    s.trim.split(",").toSeq.map(_.trim).filter(_.nonEmpty).distinct
  }
}
