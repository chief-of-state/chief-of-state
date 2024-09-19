/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class ShardConfigSpec extends BaseSpec {
  "Loading events config" should {
    "be successful when all is set" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-shards = 2
          """)

      ShardConfig(config) shouldBe ShardConfig(2)
    }

    "fail when any of the settings is missing or not properly set" in {
      val config: Config = ConfigFactory.parseString(s"""
            pekko.cluster.sharding.number-of-poops = 2
          """)
      an[ConfigException] shouldBe thrownBy(ShardConfig(config))
    }
  }
}
