/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.utils

import com.github.chiefofstate.helper.BaseSpec

class NettySpec extends BaseSpec {
  "buildChannel" should {
    "create a plaintext channel" in {
      noException shouldBe thrownBy {
        Netty.channelBuilder("x", 1, false)
      }
    }
    "create a tls channel" in {
      noException shouldBe thrownBy {
        Netty.channelBuilder("x", 1, true)
      }
    }
  }
}
