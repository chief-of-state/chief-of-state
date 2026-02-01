/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protocol.ServerProtocol
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class ServerConfigSpec extends BaseSpec {
  "creating ServerConfig from config" should {
    "be successful with grpc protocol" in {
      val config: Config = ConfigFactory.parseString("""
        server.protocol = "grpc"
        grpc.server {
          address = "0.0.0.0"
          port = 9000
        }
        http.server {
          address = "0.0.0.0"
          port = 9001
        }
      """)

      noException shouldBe thrownBy(ServerConfig(config))

      val serverConfig = ServerConfig(config)
      serverConfig.protocol shouldBe ServerProtocol.Grpc
      serverConfig.grpc.address shouldBe "0.0.0.0"
      serverConfig.grpc.port shouldBe 9000
    }

    "be successful with http protocol" in {
      val config: Config = ConfigFactory.parseString("""
        server.protocol = "http"
        grpc.server {
          address = "0.0.0.0"
          port = 9000
        }
        http.server {
          address = "0.0.0.0"
          port = 9001
        }
      """)

      val serverConfig = ServerConfig(config)
      serverConfig.protocol shouldBe ServerProtocol.Http
      serverConfig.http shouldBe a[HttpServer]
      serverConfig.http.address shouldBe "0.0.0.0"
      serverConfig.http.port shouldBe 9001
    }

    "be successful with both protocol" in {
      val config: Config = ConfigFactory.parseString("""
        server.protocol = "both"
        grpc.server {
          address = "0.0.0.0"
          port = 9000
        }
        http.server {
          address = "0.0.0.0"
          port = 9001
        }
      """)

      val serverConfig = ServerConfig(config)
      serverConfig.protocol shouldBe ServerProtocol.Both
    }

    "default to grpc when protocol not specified" in {
      val config: Config = ConfigFactory.parseString("""
        grpc.server {
          address = "0.0.0.0"
          port = 9000
        }
        http.server {
          address = "0.0.0.0"
          port = 9001
        }
      """)

      val serverConfig = ServerConfig(config)
      serverConfig.protocol shouldBe ServerProtocol.Grpc
    }

    "fail when grpc config is missing" in {
      val config: Config = ConfigFactory.parseString("""
        server.protocol = "grpc"
        http.server {
          address = "0.0.0.0"
          port = 9001
        }
      """)

      an[ConfigException] shouldBe thrownBy(ServerConfig(config))
    }

    "fail when http config is missing" in {
      val config: Config = ConfigFactory.parseString("""
        server.protocol = "http"
        grpc.server {
          address = "0.0.0.0"
          port = 9000
        }
      """)

      an[ConfigException] shouldBe thrownBy(ServerConfig(config))
    }
  }
}
