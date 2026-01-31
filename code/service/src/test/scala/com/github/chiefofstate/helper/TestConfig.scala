/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.helper

import com.github.chiefofstate.config.CosConfig
import com.typesafe.config.{Config, ConfigFactory}

object TestConfig {
  val config: Config = ConfigFactory.parseString(s"""
    pekko.cluster.sharding.number-of-shards = 1
    chiefofstate {
      service-name = "chiefofstate"
      ask-timeout = 5
      snapshot-criteria {
        disable-snapshot = false
        retention-frequency = 1
        retention-number = 1
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
          deadline-timeout = 3000
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
        port = 6000
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
    }
  """)

  val cosConfig = CosConfig(config)
}
