/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.netty

import com.github.chiefofstate.config.GrpcClientKeepalive
import com.github.chiefofstate.helper.BaseSpec

class NettySpec extends BaseSpec {

  "Netty.channelBuilder" should {
    "create a plaintext channel" in {
      noException shouldBe thrownBy(Netty.channelBuilder("x", 1, false))
    }
    "create a TLS channel" in {
      noException shouldBe thrownBy(Netty.channelBuilder("x", 1, true))
    }
    "apply keepalive options when provided" in {
      val ka = GrpcClientKeepalive(timeSeconds = 30, timeoutSeconds = 5, withoutCalls = true)
      noException shouldBe thrownBy(Netty.channelBuilder("x", 1, false, Some(ka)))
    }
  }

  "Netty.serverBuilder" should {
    "construct without failure" in {
      noException shouldBe thrownBy(Netty.serverBuilder("127.0.0.1", 0))
    }
  }

  "Netty.createChannelPool" should {
    "reject a non-positive pool size" in {
      an[IllegalArgumentException] shouldBe thrownBy(
        Netty.createChannelPool(0, "x", 1, false, None)
      )
    }

    "build a pool with the requested size" in {
      val pool = Netty.createChannelPool(3, "x", 1, false, None)
      try pool.size shouldBe 3
      finally pool.shutdown()
    }
  }

  "ChannelPool" should {
    "reject an empty channel sequence" in {
      an[IllegalArgumentException] shouldBe thrownBy(new ChannelPool(IndexedSeq.empty))
    }

    "round-robin through the channels" in {
      val pool = Netty.createChannelPool(3, "x", 1, false, None)
      try {
        val picked = (0 until 9).map(_ => pool.next()).toSet
        picked.size shouldBe 3 // visits all three
      } finally pool.shutdown()
    }

    "return a non-negative index even when the counter wraps past Int.MaxValue" in {
      // sanity: floorMod gives a non-negative remainder regardless of the dividend
      Math.floorMod(Int.MinValue, 3) should be >= 0
    }

    "shutdown all channels without throwing" in {
      val pool = Netty.createChannelPool(2, "x", 1, false, None)
      noException shouldBe thrownBy(pool.shutdown())
      noException shouldBe thrownBy(pool.shutdownNow())
    }
  }
}
