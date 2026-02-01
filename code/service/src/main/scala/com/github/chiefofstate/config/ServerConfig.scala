/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.protocol.ServerProtocol
import com.typesafe.config.Config

/**
 * ServerConfig defines the server configuration
 *
 * @param protocol which protocol(s) to expose (grpc, http, or both)
 * @param grpc the gRPC server configuration
 * @param http the HTTP server configuration
 */
final case class ServerConfig(
    protocol: ServerProtocol,
    grpc: GrpcServer,
    http: HttpServer
)

object ServerConfig {

  /**
   * creates a new instance of ServerConfig
   *
   * @param config the config object (expects chiefofstate config root)
   * @return a new instance of ServerConfig
   */
  def apply(config: Config): ServerConfig = {
    val protocolStr: String = if (config.hasPath("server.protocol")) {
      config.getString("server.protocol")
    } else {
      "grpc" // default to grpc for backward compatibility
    }

    val protocol = ServerProtocol.fromString(protocolStr)
    val grpc = GrpcServer(
      config.getString("grpc.server.address"),
      config.getInt("grpc.server.port")
    )
    val http = HttpServer(
      config.getString("http.server.address"),
      config.getInt("http.server.port")
    )

    ServerConfig(protocol, grpc, http)
  }
}
