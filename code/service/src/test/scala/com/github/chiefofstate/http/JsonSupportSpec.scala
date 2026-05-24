/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.common.{Header, MetaData}
import com.github.chiefofstate.protobuf.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import com.github.chiefofstate.protobuf.v1.service.{
  GetStateRequest,
  GetStateResponse,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import com.github.chiefofstate.protobuf.v1.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  HandleEventRequest,
  HandleEventResponse
}
import com.google.protobuf.ByteString
import com.google.protobuf.any
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers.StringValue

import java.util.Base64

class JsonSupportSpec extends BaseSpec with JsonSupport {

  private def pack(s: String): any.Any = any.Any.pack(StringValue(s))
  private def b64(a: any.Any): String  = Base64.getEncoder.encodeToString(a.toByteArray)

  "JsonSupport.ProcessCommandRequest" should {
    "parse with base64-encoded command (custom types, no registration)" in {
      val cmd  = pack("custom-command-data")
      val json = s"""{"entity_id":"entity-1","command":"${b64(cmd)}"}"""
      val r    = parseProcessCommandRequest(json)
      r.entityId shouldBe "entity-1"
      r.command.value.typeUrl shouldBe cmd.typeUrl
      r.command.value.value shouldBe cmd.value
    }

    "convert to JSON and back" in {
      val cmd = pack("test-data")
      val req = ProcessCommandRequest(entityId = "test-entity", command = Some(cmd))
      val r   = parseJson[ProcessCommandRequest](toJson(req))
      r.entityId shouldBe req.entityId
      r.command.value.typeUrl shouldBe cmd.typeUrl
    }

    "handle empty request" in {
      val r =
        parseProcessCommandRequest(toJson(ProcessCommandRequest(entityId = "", command = None)))
      r.entityId shouldBe ""
      r.command shouldBe None
    }

    "treat a null command field as None" in {
      parseProcessCommandRequest("""{"entity_id":"e","command":null}""").command shouldBe None
    }

    "treat a missing entity_id as empty" in {
      parseProcessCommandRequest("""{}""").entityId shouldBe ""
    }

    "wrap invalid JSON in JsonParseException" in {
      a[JsonParseException] shouldBe thrownBy(parseProcessCommandRequest("not-json"))
    }
  }

  "JsonSupport.GetStateRequest" should {
    "round-trip" in {
      val req = GetStateRequest(entityId = "e-1")
      parseJson[GetStateRequest](toJson(req)).entityId shouldBe "e-1"
    }

    "default missing entity_id to empty" in {
      parseGetStateRequest("""{}""").entityId shouldBe ""
    }
  }

  "JsonSupport.MetaData" should {
    "serialise and parse all populated fields" in {
      val meta = MetaData()
        .withEntityId("e1")
        .withRevisionNumber(42)
        .withRevisionDate(Timestamp(seconds = 1700000000L, nanos = 123000000))
        .addData("k1" -> pack("v1"))
        .addHeaders(Header(key = "h-str").withStringValue("v"))
        .addHeaders(Header(key = "h-bytes").withBytesValue(ByteString.copyFromUtf8("bin")))
      val node = metaToJson(meta)
      val r    = parseMeta(node)
      r.entityId shouldBe "e1"
      r.revisionNumber shouldBe 42
      r.revisionDate.value.seconds shouldBe 1700000000L
      r.revisionDate.value.nanos shouldBe 123000000
      r.data("k1").typeUrl shouldBe pack("v1").typeUrl
      r.headers.size shouldBe 2
      r.headers.exists(h => h.key == "h-str" && h.getStringValue == "v") shouldBe true
      r.headers.exists(h => h.key == "h-bytes" && !h.getBytesValue.isEmpty) shouldBe true
    }

    "omit empty fields when serialised" in {
      val node = metaToJson(MetaData())
      node.has("entity_id") shouldBe false
      node.has("revision_number") shouldBe false
      node.has("revision_date") shouldBe false
      node.has("data") shouldBe false
      node.has("headers") shouldBe false
    }

    "preserve a header with no value (Empty)" in {
      val meta = MetaData().addHeaders(Header(key = "empty"))
      val r    = parseMeta(metaToJson(meta))
      r.headers.head.key shouldBe "empty"
      r.headers.head.value.isEmpty shouldBe true
    }
  }

  "JsonSupport.ProcessCommandResponse" should {
    "round-trip with state and meta" in {
      val st  = pack("state")
      val md  = MetaData().withEntityId("e").withRevisionNumber(1)
      val res = ProcessCommandResponse(state = Some(st), meta = Some(md))
      val r   = parseProcessCommandResponse(processCommandResponseToJson(res))
      r.state.value.typeUrl shouldBe st.typeUrl
      r.meta.value.entityId shouldBe "e"
      r.meta.value.revisionNumber shouldBe 1
    }

    "omit absent state and meta" in {
      val r = parseProcessCommandResponse(processCommandResponseToJson(ProcessCommandResponse()))
      r.state shouldBe None
      r.meta shouldBe None
    }
  }

  "JsonSupport.GetStateResponse" should {
    "round-trip with state and meta" in {
      val st  = pack("state")
      val md  = MetaData().withEntityId("e").withRevisionNumber(2)
      val res = GetStateResponse(state = Some(st), meta = Some(md))
      val r   = parseGetStateResponse(getStateResponseToJson(res))
      r.state.value.typeUrl shouldBe st.typeUrl
      r.meta.value.revisionNumber shouldBe 2
    }
  }

  "JsonSupport.HandleCommandRequest / Response" should {
    "serialise HandleCommandRequest with command, priorState, priorEventMeta" in {
      val req = HandleCommandRequest()
        .withCommand(pack("cmd"))
        .withPriorState(pack("prior"))
        .withPriorEventMeta(MetaData().withEntityId("e"))
      val json = toJson(req)
      json should include("command")
      json should include("prior_state")
      json should include("prior_event_meta")
    }

    "parse HandleCommandResponse with a single deprecated event" in {
      val ev   = pack("e")
      val json = s"""{"event":"${b64(ev)}"}"""
      val r    = parseJson[HandleCommandResponse](json)
      r.getEvent.typeUrl shouldBe ev.typeUrl
    }

    "parse HandleCommandResponse with events array" in {
      val a    = pack("a")
      val b    = pack("b")
      val json = s"""{"events":["${b64(a)}","${b64(b)}"]}"""
      val r    = parseJson[HandleCommandResponse](json)
      r.events.size shouldBe 2
      r.events.head.typeUrl shouldBe a.typeUrl
    }

    "parse HandleCommandResponse with no events" in {
      val r = parseJson[HandleCommandResponse]("""{}""")
      r.events shouldBe empty
      r.event shouldBe None
    }
  }

  "JsonSupport.HandleEventRequest / Response" should {
    "serialise HandleEventRequest" in {
      val req = HandleEventRequest()
        .withEvent(pack("ev"))
        .withPriorState(pack("prior"))
        .withEventMeta(MetaData().withEntityId("e"))
      val json = toJson(req)
      json should include("event")
      json should include("prior_state")
      json should include("event_meta")
    }

    "parse HandleEventResponse with resulting_state" in {
      val st   = pack("st")
      val json = s"""{"resulting_state":"${b64(st)}"}"""
      val r    = parseJson[HandleEventResponse](json)
      r.resultingState.value.typeUrl shouldBe st.typeUrl
    }

    "parse HandleEventResponse without resulting_state" in {
      parseJson[HandleEventResponse]("""{}""").resultingState shouldBe None
    }
  }

  "JsonSupport.HandleReadSideRequest / Response" should {
    "serialise HandleReadSideRequest with all fields" in {
      val req = HandleReadSideRequest()
        .withEvent(pack("ev"))
        .withState(pack("st"))
        .withMeta(MetaData().withEntityId("e"))
        .withReadSideId("rs-1")
      val json = toJson(req)
      json should include("event")
      json should include("state")
      json should include("meta")
      json should include("rs-1")
    }

    "parse HandleReadSideResponse" in {
      parseJson[HandleReadSideResponse]("""{"successful":true}""").successful shouldBe true
      parseJson[HandleReadSideResponse]("""{"successful":false}""").successful shouldBe false
      parseJson[HandleReadSideResponse]("""{}""").successful shouldBe false
    }
  }

  "JsonSupport.base64ToAny" should {
    "raise JsonParseException on garbage input" in {
      a[JsonParseException] shouldBe thrownBy(base64ToAny("not-base64!!!"))
    }
  }

  "JsonSupport.timestamp helpers" should {
    "round-trip ISO ↔ Timestamp" in {
      val ts  = Timestamp(seconds = 1700000000L, nanos = 250_000_000)
      val iso = timestampToIso(ts)
      val r   = isoToTimestamp(iso)
      r.seconds shouldBe ts.seconds
      r.nanos shouldBe ts.nanos
    }

    "return default Timestamp for invalid ISO string" in {
      isoToTimestamp("not-a-date") shouldBe Timestamp.defaultInstance
    }
  }
}
