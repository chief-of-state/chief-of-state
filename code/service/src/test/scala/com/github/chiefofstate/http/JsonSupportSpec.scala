/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.service.{GetStateRequest, ProcessCommandRequest}
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue

import java.util.Base64

class JsonSupportSpec extends BaseSpec with JsonSupport {

  "JsonSupport" should {
    "parse ProcessCommandRequest with base64-encoded command (custom types, no registration)" in {
      val customCommand = any.Any.pack(StringValue("custom-command-data"))
      val commandBase64 = Base64.getEncoder.encodeToString(customCommand.toByteArray)
      val json          = s"""{"entity_id":"entity-1","command":"$commandBase64"}"""
      val parsed        = parseProcessCommandRequest(json)
      parsed.entityId shouldBe "entity-1"
      parsed.command.isDefined shouldBe true
      parsed.command.get.typeUrl shouldBe customCommand.typeUrl
      parsed.command.get.value shouldBe customCommand.value
    }
    "convert ProcessCommandRequest to JSON and back (base64 command)" in {
      val command = any.Any.pack(StringValue("test-data"))
      val request = ProcessCommandRequest(
        entityId = "test-entity",
        command = Some(command)
      )

      val json = toJson(request)
      json should include("test-entity")
      json should include("command")

      val parsed = parseJson[ProcessCommandRequest](json)
      parsed.entityId shouldBe request.entityId
      parsed.command.isDefined shouldBe true
      parsed.command.get.typeUrl shouldBe command.typeUrl
      parsed.command.get.value shouldBe command.value
    }

    "convert GetStateRequest to JSON and back" in {
      val request = GetStateRequest(entityId = "test-entity")

      val json = toJson(request)
      json should include("test-entity")

      val parsed = parseJson[GetStateRequest](json)
      parsed.entityId shouldBe request.entityId
    }

    "handle empty ProcessCommandRequest" in {
      val request = ProcessCommandRequest(entityId = "", command = None)

      val json   = toJson(request)
      val parsed = parseProcessCommandRequest(json)
      parsed.entityId shouldBe ""
      parsed.command shouldBe None
    }
  }
}
