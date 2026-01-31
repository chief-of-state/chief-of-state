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
   * creates a  new instance of EventsConfig with validation
   * @param config the config object
   * @return the new instance created
   * @throws IllegalArgumentException if configuration is invalid
   */
  def apply(config: Config): ShardConfig = {
    val numShards = config.getInt(numShardsKey)
    require(numShards > 0, s"$numShardsKey must be positive, got $numShards")
    require(numShards <= 10000, s"$numShardsKey is too large (max 10000), got $numShards")
    require(
      (numShards & (numShards - 1)) == 0,
      s"$numShardsKey should be a power of 2 for optimal distribution, got $numShards"
    )
    ShardConfig(numShards = numShards)
  }
}
