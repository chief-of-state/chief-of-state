/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class GrpcConfigSpec extends BaseSpec {
  "Loading gRPC config" should {
    "be successful when all settings are done" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
            }
          """)

      noException shouldBe thrownBy(GrpcConfig(config))
    }

    "parse keepalive as None when keepalive block is missing" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
            }
          """)
      val grpcConfig = GrpcConfig(config)
      grpcConfig.client.keepalive shouldBe None
    }

    "parse keepalive as None when keepalive.enabled is false" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                  keepalive {
                    enabled = false
                    time = 120s
                    timeout = 60s
                    without-calls = true
                  }
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
            }
          """)
      val grpcConfig = GrpcConfig(config)
      grpcConfig.client.keepalive shouldBe None
    }

    "parse keepalive as Some when keepalive.enabled is true" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                  keepalive {
                    enabled = true
                    time = 90s
                    timeout = 30s
                    without-calls = true
                  }
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
            }
          """)
      val grpcConfig = GrpcConfig(config)
      grpcConfig.client.keepalive shouldBe Some(
        GrpcClientKeepalive(timeSeconds = 90L, timeoutSeconds = 30L, withoutCalls = true)
      )
    }
  }

  "fail when settings are missing or having invalid names" in {
    val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                }
                # server {
                #  port = 9000
                # }
              }
            }
          """)
    an[ConfigException] shouldBe thrownBy(GrpcConfig(config))
  }
}
