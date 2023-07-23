/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object JdbcConfig {

  /**
   * returns the akka journal database config
   *
   * @param config the main application config
   */
  def journalConfig(config: Config): DatabaseConfig[JdbcProfile] = {
    DatabaseConfig.forConfig[JdbcProfile]("write-side-slick", config)
  }

  /**
   * returns the akka projection database config
   * @param config the main application config
   */
  def projectionConfig(
      config: Config,
      key: String = "write-side-slick"
  ): DatabaseConfig[JdbcProfile] = {
    DatabaseConfig.forConfig[JdbcProfile](key, config)
  }

  /**
   * get the Jdbc profile
   *
   * @param config the main application config
   */
  def journalJdbcProfile(config: Config): JdbcProfile = journalConfig(config).profile
}
