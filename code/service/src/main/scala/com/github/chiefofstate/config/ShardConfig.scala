/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config

/**
 * Events settings
 */
final case class ShardConfig(numShards: Int)
object ShardConfig {
  private val numShardsKey = "pekko.cluster.sharding.number-of-shards"

  /**
   * creates a  new instance of EventsConfif
   * @param config the config object
   * @return the new instance created
   */
  def apply(config: Config): ShardConfig = {
    ShardConfig(numShards = config.getInt(numShardsKey))
  }
}
