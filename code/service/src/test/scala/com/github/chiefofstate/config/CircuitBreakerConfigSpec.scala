/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class CircuitBreakerConfigSpec extends BaseSpec {
  "CircuitBreakerConfig" should {
    "load valid configuration" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = true
          |  max-failures = 5
          |  call-timeout = 10s
          |  reset-timeout = 1m
          |}
          |""".stripMargin

      val config   = ConfigFactory.parseString(configStr)
      val cbConfig = CircuitBreakerConfig(config.getConfig("circuit-breaker"))

      cbConfig.enabled shouldBe true
      cbConfig.maxFailures shouldBe 5
      cbConfig.callTimeout shouldBe 10.seconds
      cbConfig.resetTimeout shouldBe 1.minute
    }

    "load disabled configuration" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = false
          |  max-failures = 5
          |  call-timeout = 10s
          |  reset-timeout = 1m
          |}
          |""".stripMargin

      val config   = ConfigFactory.parseString(configStr)
      val cbConfig = CircuitBreakerConfig(config.getConfig("circuit-breaker"))

      cbConfig.enabled shouldBe false
    }

    "create disabled config" in {
      val cbConfig = CircuitBreakerConfig.disabled()

      cbConfig.enabled shouldBe false
      cbConfig.maxFailures shouldBe 5
      cbConfig.callTimeout shouldBe 10.seconds
      cbConfig.resetTimeout shouldBe 1.minute
    }

    "validate positive max-failures" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = true
          |  max-failures = 0
          |  call-timeout = 10s
          |  reset-timeout = 1m
          |}
          |""".stripMargin

      val config = ConfigFactory.parseString(configStr)

      an[IllegalArgumentException] shouldBe thrownBy {
        CircuitBreakerConfig(config.getConfig("circuit-breaker"))
      }
    }

    "validate max-failures upper bound" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = true
          |  max-failures = 101
          |  call-timeout = 10s
          |  reset-timeout = 1m
          |}
          |""".stripMargin

      val config = ConfigFactory.parseString(configStr)

      an[IllegalArgumentException] shouldBe thrownBy {
        CircuitBreakerConfig(config.getConfig("circuit-breaker"))
      }
    }

    "validate positive call-timeout" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = true
          |  max-failures = 5
          |  call-timeout = 0s
          |  reset-timeout = 1m
          |}
          |""".stripMargin

      val config = ConfigFactory.parseString(configStr)

      an[IllegalArgumentException] shouldBe thrownBy {
        CircuitBreakerConfig(config.getConfig("circuit-breaker"))
      }
    }

    "validate reset-timeout minimum" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = true
          |  max-failures = 5
          |  call-timeout = 10s
          |  reset-timeout = 500ms
          |}
          |""".stripMargin

      val config = ConfigFactory.parseString(configStr)

      an[IllegalArgumentException] shouldBe thrownBy {
        CircuitBreakerConfig(config.getConfig("circuit-breaker"))
      }
    }

    "skip validation when disabled" in {
      val configStr =
        """
          |circuit-breaker {
          |  enabled = false
          |  max-failures = 0
          |  call-timeout = 0s
          |  reset-timeout = 0s
          |}
          |""".stripMargin

      val config = ConfigFactory.parseString(configStr)

      // Should not throw when disabled
      noException shouldBe thrownBy {
        CircuitBreakerConfig(config.getConfig("circuit-breaker"))
      }
    }
  }
}
