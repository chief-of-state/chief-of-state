/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protocol.ServerProtocol
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class WriteSideConfigSpec extends BaseSpec {
  "Loading write side config" should {
    "be successful when all settings are set" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      noException shouldBe thrownBy(WriteSideConfig(config))
    }

    "fail when any of the settings is missing or not properly set" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-proto = ""
                events-protos = ""
              }
            }
          """)
      an[ConfigException] shouldBe thrownBy(WriteSideConfig(config))
    }

    "default to gRPC protocol when not specified" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      val writeSideConfig = WriteSideConfig(config)
      writeSideConfig.protocol shouldBe ServerProtocol.Grpc
    }

    "parse grpc protocol correctly" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                protocol = "grpc"
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      val writeSideConfig = WriteSideConfig(config)
      writeSideConfig.protocol shouldBe ServerProtocol.Grpc
    }

    "parse http protocol correctly" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                protocol = "http"
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      val writeSideConfig = WriteSideConfig(config)
      writeSideConfig.protocol shouldBe ServerProtocol.Http
    }

    "parse both protocol correctly" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                protocol = "both"
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      val writeSideConfig = WriteSideConfig(config)
      writeSideConfig.protocol shouldBe ServerProtocol.Both
    }

    "be case-insensitive when parsing protocol" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                protocol = "GRPC"
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      val writeSideConfig = WriteSideConfig(config)
      writeSideConfig.protocol shouldBe ServerProtocol.Grpc
    }

    "fail with invalid protocol" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                protocol = "invalid"
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
            }
          """)
      val exception = the[IllegalArgumentException] thrownBy WriteSideConfig(config)
      exception.getMessage should include("Invalid write-side protocol: invalid")
      exception.getMessage should include("Must be 'grpc', 'http', or 'both'")
    }
  }
}
