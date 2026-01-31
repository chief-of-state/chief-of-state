/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config

/**
 * HttpConfig reads the HTTP settings from the config
 *
 * @param server the http server setting
 */
case class HttpConfig(server: HttpServer)

case class HttpServer(address: String, port: Int)

object HttpConfig {

  /**
   * creates a new instance of the HttpConfig
   *
   * @param config the configuration object
   * @return a new instance of HttpConfig
   */
  def apply(config: Config): HttpConfig = {
    HttpConfig(
      HttpServer(
        config.getString("chiefofstate.http.server.address"),
        config.getInt("chiefofstate.http.server.port")
      )
    )
  }
}
