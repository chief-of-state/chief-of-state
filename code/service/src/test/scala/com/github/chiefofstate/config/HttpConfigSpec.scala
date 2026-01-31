/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class HttpConfigSpec extends BaseSpec {

  "creating HttpConfig" should {
    "be successful when all required settings are provided" in {
      val config: Config = ConfigFactory.parseString("""
        chiefofstate.http.server {
          address = "0.0.0.0"
          port = 9001
        }
      """)

      noException shouldBe thrownBy(HttpConfig(config))

      val httpConfig = HttpConfig(config)
      httpConfig.server.address shouldBe "0.0.0.0"
      httpConfig.server.port shouldBe 9001
    }

    "fail when address is missing" in {
      val config: Config = ConfigFactory.parseString("""
        chiefofstate.http.server {
          port = 9001
        }
      """)

      an[ConfigException] shouldBe thrownBy(HttpConfig(config))
    }

    "fail when port is missing" in {
      val config: Config = ConfigFactory.parseString("""
        chiefofstate.http.server {
          address = "0.0.0.0"
        }
      """)

      an[ConfigException] shouldBe thrownBy(HttpConfig(config))
    }
  }
}
