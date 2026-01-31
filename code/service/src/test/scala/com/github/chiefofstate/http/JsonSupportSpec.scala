/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.service.{GetStateRequest, ProcessCommandRequest}
import com.google.protobuf.any

class JsonSupportSpec extends BaseSpec with JsonSupport {

  "JsonSupport" should {
    "convert ProcessCommandRequest to JSON and back" in {
      val command = any.Any.pack(GetStateRequest("test-entity"))
      val request = ProcessCommandRequest(
        entityId = "test-entity",
        command = Some(command)
      )

      val json = toJson(request)
      json should include("test-entity")

      val parsed = parseJson[ProcessCommandRequest](json)
      parsed.entityId shouldBe request.entityId
      parsed.command.isDefined shouldBe true
    }

    "convert GetStateRequest to JSON and back" in {
      val request = GetStateRequest(entityId = "test-entity")

      val json = toJson(request)
      json should include("test-entity")

      val parsed = parseJson[GetStateRequest](json)
      parsed.entityId shouldBe request.entityId
    }

    "handle empty messages" in {
      val request = ProcessCommandRequest(entityId = "")

      val json   = toJson(request)
      val parsed = parseJson[ProcessCommandRequest](json)
      parsed.entityId shouldBe ""
    }
  }
}
