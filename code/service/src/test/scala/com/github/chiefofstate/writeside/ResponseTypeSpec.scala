/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.google.protobuf.any.Any

class ResponseTypeSpec extends BaseSpec {

  "ResponseType" should {
    "support NewEvent" in {
      val event = Any.pack(com.github.chiefofstate.protobuf.v1.service.GetStateRequest("e1"))
      val r: ResponseType.Response = ResponseType.NewEvent(event)
      r shouldBe a[ResponseType.NewEvent]
      r.asInstanceOf[ResponseType.NewEvent].event shouldBe event
    }

    "support NewState" in {
      val event = Any.pack(com.github.chiefofstate.protobuf.v1.service.GetStateRequest("e1"))
      val state = Any.pack(com.github.chiefofstate.protobuf.v1.service.GetStateRequest("s1"))
      val meta  = MetaData()
      val r: ResponseType.Response = ResponseType.NewState(event, state, meta)
      r shouldBe a[ResponseType.NewState]
      val ns = r.asInstanceOf[ResponseType.NewState]
      ns.event shouldBe event
      ns.state shouldBe state
      ns.eventMeta shouldBe meta
    }

    "support NoOp" in {
      val r: ResponseType.Response = ResponseType.NoOp
      r shouldBe ResponseType.NoOp
    }
  }
}
