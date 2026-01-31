/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.types

import com.github.chiefofstate.helper.BaseSpec
import org.scalatest.EitherValues

class FailurePolicySpec extends BaseSpec with EitherValues {

  "FailurePolicy.fromString" should {
    "parse STOP" in {
      FailurePolicy.fromString("STOP") shouldBe Right(FailurePolicy.Stop)
      FailurePolicy.fromString("stop") shouldBe Right(FailurePolicy.Stop)
    }

    "parse SKIP" in {
      FailurePolicy.fromString("SKIP") shouldBe Right(FailurePolicy.Skip)
      FailurePolicy.fromString("skip") shouldBe Right(FailurePolicy.Skip)
    }

    "parse REPLAY_STOP and REPLAY-STOP" in {
      FailurePolicy.fromString("REPLAY_STOP") shouldBe Right(FailurePolicy.RetryAndStop(5, 5))
      FailurePolicy.fromString("REPLAY-STOP") shouldBe Right(FailurePolicy.RetryAndStop(5, 5))
    }

    "parse REPLAY_SKIP and REPLAY-SKIP" in {
      FailurePolicy.fromString("REPLAY_SKIP") shouldBe Right(FailurePolicy.RetryAndSkip(5, 5))
      FailurePolicy.fromString("REPLAY-SKIP") shouldBe Right(FailurePolicy.RetryAndSkip(5, 5))
    }

    "return Left for unknown policy" in {
      FailurePolicy.fromString("UNKNOWN").isLeft shouldBe true
      FailurePolicy.fromString("").isLeft shouldBe true
      FailurePolicy.fromString("invalid").left.value should include("Unknown failure policy")
    }
  }

  "FailurePolicy.fromStringOrDefault" should {
    "return parsed policy for valid string" in {
      FailurePolicy.fromStringOrDefault("SKIP") shouldBe FailurePolicy.Skip
      FailurePolicy.fromStringOrDefault("STOP") shouldBe FailurePolicy.Stop
    }

    "return Stop for invalid string" in {
      FailurePolicy.fromStringOrDefault("INVALID") shouldBe FailurePolicy.Stop
      FailurePolicy.fromStringOrDefault("") shouldBe FailurePolicy.Stop
    }
  }
}
