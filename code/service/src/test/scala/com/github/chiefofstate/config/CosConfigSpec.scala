/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class CosConfigSpec extends BaseSpec {
  "Loading main config" should {
    "be successful when all settings are set" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 2
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeout = 5
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              server {
                protocol = "grpc"
              }
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              http {
                server {
                  address = "0.0.0.0"
                  port = 9001
                }
              }
              write-side {
                host = "localhost"
                port = 1000
                use-tls = false
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
                persisted-headers = ""
              }
              read-side {
                # set this value to true whenever a readSide config is set
                enabled = false
              }
              telemetry {
                namespace = ""
                otlp_endpoint = ""
                trace_propagators = "b3multi"
              }
            }
          """)
      noException shouldBe thrownBy(CosConfig(config))
    }

    "honour an explicit enableSubscription = true" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 2
            chiefofstate {
             	service-name = "cos"
              ask-timeout = 5
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
              subscription { enabled = true }
              telemetry { namespace = "", otlp_endpoint = "", trace_propagators = "b3multi" }
            }
          """)
      CosConfig(config).enableSubscription shouldBe true
    }

    "default enableSubscription to false when path is missing" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 2
            chiefofstate {
             	service-name = "cos"
              ask-timeout = 5
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
              telemetry { namespace = "", otlp_endpoint = "", trace_propagators = "b3multi" }
            }
          """)
      CosConfig(config).enableSubscription shouldBe false
    }

    "fail when service-name is empty" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 1
            chiefofstate {
              service-name = ""
              ask-timeout = 5
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
            }
          """)
      an[IllegalArgumentException] shouldBe thrownBy(CosConfig(config))
    }

    "fail when service-name exceeds 64 characters" in {
      val longName = "x" * 65
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 1
            chiefofstate {
              service-name = "$longName"
              ask-timeout = 5
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
            }
          """)
      an[IllegalArgumentException] shouldBe thrownBy(CosConfig(config))
    }

    "fail when ask-timeout is too large" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 1
            chiefofstate {
              service-name = "cos"
              ask-timeout = 301
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
            }
          """)
      an[IllegalArgumentException] shouldBe thrownBy(CosConfig(config))
    }

    "load returns Left for a ConfigException (missing setting)" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              service-name = "cos"
              ask-timeout = 5
            }
          """)
      val res = CosConfig.load(config)
      res.isLeft shouldBe true
      res.left.toOption.value should include("Configuration")
    }

    "fail when ask-timeout is non-positive" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 1
            chiefofstate {
              service-name = "cos"
              ask-timeout = 0
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
            }
          """)
      an[IllegalArgumentException] shouldBe thrownBy(CosConfig(config))
    }

    "load returns Left for invalid configuration" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              service-name = ""
              ask-timeout = 5
            }
          """)
      CosConfig.load(config).isLeft shouldBe true
    }

    "load returns Right for a valid configuration" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 1
            chiefofstate {
             	service-name = "cos"
              ask-timeout = 5
              snapshot-criteria { disable-snapshot = true, retention-frequency = 1, retention-number = 1, delete-events-on-snapshot = false }
              events { tagname: "cos" }
              server { protocol = "grpc" }
              grpc { client { deadline-timeout = 100 }, server { address = "0.0.0.0", port = 9000 } }
              http { server { address = "0.0.0.0", port = 9001 } }
              write-side { host = "h", port = 1, use-tls = false, enable-protos-validation = false, states-protos = "", events-protos = "", propagated-headers = "", persisted-headers = "" }
              read-side { enabled = false }
            }
          """)
      CosConfig.load(config).isRight shouldBe true
    }

    "fail when there is either a missing setting or a wrong naming" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 2
            chiefofstate {
              service-name = "chiefofstate"
              ask-timeouts = 5 # wrong naming
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 1000
                use-tls = false
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
              }
              read-side {
                # set this value to true whenever a readSide config is set
                enabled = false
              }
            }
          """)
      an[ConfigException] shouldBe thrownBy(CosConfig(config))
    }
  }
}
