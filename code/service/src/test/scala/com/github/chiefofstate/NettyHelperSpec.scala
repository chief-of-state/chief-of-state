/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import com.github.chiefofstate.helper.BaseSpec

class NettyHelperSpec extends BaseSpec {
  "buildChannel" should {
    "create a plaintext channel" in {
      noException shouldBe thrownBy {
        NettyHelper.builder("x", 1, false)
      }
    }
    "create a tls channel" in {
      noException shouldBe thrownBy {
        NettyHelper.builder("x", 1, true)
      }
    }
  }
}
