/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class SnapshotConfigSpec extends BaseSpec {

  "Loading snapshot config" should {
    "be successful when all settings are set" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
                delete-events-on-snapshot = false
              }
            }
          """)
      noException shouldBe thrownBy(SnapshotConfig(config))
    }

    "fail when some settings are missing" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
              }
            }
          """)
      an[ConfigException] shouldBe thrownBy(SnapshotConfig(config))
    }

    "fail when some settings are wrong" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
                delete-events-onsnapshot = false // wrong setting
              }
            }
          """)
      an[ConfigException] shouldBe thrownBy(SnapshotConfig(config))
    }
  }
}
