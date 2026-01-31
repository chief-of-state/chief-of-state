/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec

class ReadSideFailurePolicySpec extends BaseSpec {

  "ReadSideFailurePolicy" should {
    "define StopDirective" in {
      ReadSideFailurePolicy.StopDirective shouldBe "STOP"
    }

    "define SkipDirective" in {
      ReadSideFailurePolicy.SkipDirective shouldBe "SKIP"
    }

    "define ReplayStopDirective" in {
      ReadSideFailurePolicy.ReplayStopDirective shouldBe "REPLAY_STOP"
    }

    "define ReplaySkipDirective" in {
      ReadSideFailurePolicy.ReplaySkipDirective shouldBe "REPLAY_SKIP"
    }
  }
}
