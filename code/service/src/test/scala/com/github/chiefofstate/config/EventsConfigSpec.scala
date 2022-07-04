/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{ Config, ConfigException, ConfigFactory }

class EventsConfigSpec extends BaseSpec {
  "Loading events config" should {
    "be successful when all is set" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 2
          """)

      EventsConfig(config) shouldBe EventsConfig(2)
    }

    "fail when any of the settings is missing or not properly set" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-poops = 2
          """)
      an[ConfigException] shouldBe thrownBy(EventsConfig(config))
    }
  }
}
