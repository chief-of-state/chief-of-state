/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.utils

import com.github.chiefofstate.helper.BaseSpec

class NettyHelperSpec extends BaseSpec {
  "buildChannel" should {
    "create a plaintext channel" in {
      noException shouldBe thrownBy {
        NettyHelper.channelBuilder("x", 1, false)
      }
    }
    "create a tls channel" in {
      noException shouldBe thrownBy {
        NettyHelper.channelBuilder("x", 1, true)
      }
    }
  }
}
