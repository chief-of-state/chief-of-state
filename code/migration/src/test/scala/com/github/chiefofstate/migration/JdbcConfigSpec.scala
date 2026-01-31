/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

import com.typesafe.config.ConfigFactory
import slick.jdbc.JdbcProfile

class JdbcConfigSpec extends BaseSpec {

  "JdbcConfig" should {
    "load journal config" in {
      val config   = ConfigFactory.load("migration")
      val dbConfig = JdbcConfig.journalConfig(config)
      dbConfig should not be null
      dbConfig.profile shouldBe a[JdbcProfile]
    }

    "load projection config with default key" in {
      val config   = ConfigFactory.load("migration")
      val dbConfig = JdbcConfig.projectionConfig(config)
      dbConfig should not be null
      dbConfig.profile shouldBe a[JdbcProfile]
    }

    "load projection config with custom key" in {
      val config   = ConfigFactory.load("migration")
      val dbConfig = JdbcConfig.projectionConfig(config, "write-side-slick")
      dbConfig should not be null
    }

    "return journal JdbcProfile" in {
      val config  = ConfigFactory.load("migration")
      val profile = JdbcConfig.journalJdbcProfile(config)
      profile shouldBe a[JdbcProfile]
    }
  }
}
