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

    "fail when number-of-shards is non-positive" in {
      val config: Config =
        ConfigFactory.parseString("pekko.cluster.sharding.number-of-shards = 0")
      an[IllegalArgumentException] shouldBe thrownBy(ShardConfig(config))
    }

    "fail when number-of-shards exceeds 10000" in {
      val config: Config =
        ConfigFactory.parseString("pekko.cluster.sharding.number-of-shards = 20000")
      an[IllegalArgumentException] shouldBe thrownBy(ShardConfig(config))
    }

    "fail when number-of-shards is not a power of 2" in {
      val config: Config =
        ConfigFactory.parseString("pekko.cluster.sharding.number-of-shards = 5")
      an[IllegalArgumentException] shouldBe thrownBy(ShardConfig(config))
    }
  }
}
