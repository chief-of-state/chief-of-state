/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.ObjectNode
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
import com.google.protobuf.any
import com.google.protobuf.timestamp.Timestamp
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

import java.time.Instant
import java.util.Base64
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * JSON marshalling support for protobuf messages over HTTP.
 *
 * All command, event, and state fields (protobuf `Any`) are base64-encoded.
 * No TypeRegistry or type registration is required.
 */
trait JsonSupport {

  private val jsonMapper = new ObjectMapper()

  // --- Core conversion helpers ---

  protected def anyToBase64(a: any.Any): String =
    Base64.getEncoder.encodeToString(a.toByteArray)

  protected def base64ToAny(s: String): any.Any =
    Try(any.Any.parseFrom(Base64.getDecoder.decode(s))) match {
      case Success(value) => value
      case Failure(ex) =>
        throw new JsonParseException(s"Failed to decode base64 Any: ${ex.getMessage}", ex)
    }

  protected def timestampToIso(ts: Timestamp): String =
    Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong).toString

  protected def isoToTimestamp(iso: String): Timestamp =
    Try {
      val instant = Instant.parse(iso)
      Timestamp().withSeconds(instant.getEpochSecond).withNanos(instant.getNano)
    }.getOrElse(Timestamp.defaultInstance)

  // --- MetaData (nested in several messages; has data map with Any values) ---

  protected def metaToJson(meta: MetaData): ObjectNode = {
    val obj = jsonMapper.createObjectNode()
    if (meta.entityId.nonEmpty) obj.put("entity_id", meta.entityId)
    if (meta.revisionNumber != 0) obj.put("revision_number", meta.revisionNumber)
    meta.revisionDate.foreach { rd => obj.put("revision_date", timestampToIso(rd)) }

    // Add data map with base64 Any values
    if (meta.data.nonEmpty) {
      val dataObj = jsonMapper.createObjectNode()
      meta.data.foreach { case (k, v) => dataObj.put(k, anyToBase64(v)) }
      obj.set("data", dataObj)
    }

    // Add headers array
    if (meta.headers.nonEmpty) {
      val arr = jsonMapper.createArrayNode()
      meta.headers.foreach { h =>
        val hObj = jsonMapper.createObjectNode()
        hObj.put("key", h.key)
        h.value match {
          case Header.Value.StringValue(v) => hObj.put("string_value", v)
          case Header.Value.BytesValue(v) =>
            hObj.put("bytes_value", Base64.getEncoder.encodeToString(v.toByteArray))
          case Header.Value.Empty =>
        }
        arr.add(hObj)
      }
      obj.set("headers", arr)
    }
    obj
  }

  protected def parseMeta(metaNode: JsonNode): MetaData = {
    var meta = MetaData()

    // Parse basic fields
    if (metaNode.has("entity_id"))
      meta = meta.withEntityId(metaNode.get("entity_id").asText())
    if (metaNode.has("revision_number"))
      meta = meta.withRevisionNumber(metaNode.get("revision_number").asInt())
    if (metaNode.has("revision_date"))
      meta = meta.withRevisionDate(isoToTimestamp(metaNode.get("revision_date").asText()))

    // Parse data map (values are base64 Any)
    if (metaNode.has("data") && !metaNode.get("data").isNull) {
      val dataNode = metaNode.get("data")
      meta = dataNode
        .fields()
        .asScala
        .foldLeft(meta) { case (acc, entry) =>
          acc.addData(entry.getKey -> base64ToAny(entry.getValue.asText()))
        }
    }

    // Parse headers array
    if (metaNode.has("headers") && metaNode.get("headers").isArray) {
      val arr = metaNode.get("headers")
      meta = (0 until arr.size).foldLeft(meta) { case (acc, i) =>
        val h   = arr.get(i)
        val key = if (h.has("key")) h.get("key").asText() else ""
        if (h.has("string_value"))
          acc.addHeaders(Header(key = key).withStringValue(h.get("string_value").asText()))
        else if (h.has("bytes_value"))
          acc.addHeaders(
            Header(key = key).withBytesValue(
              com.google.protobuf.ByteString.copyFrom(
                Base64.getDecoder.decode(h.get("bytes_value").asText())
              )
            )
          )
        else acc.addHeaders(Header(key = key))
      }
    }
    meta
  }

  // --- Helper: serialize optional Any (omit if None, base64 if present) ---

  private def putOptionalAny(obj: ObjectNode, fieldName: String, anyOpt: Option[any.Any]): Unit =
    anyOpt.foreach { a =>
      if (a != any.Any.defaultInstance) obj.put(fieldName, anyToBase64(a))
    }

  // --- Helper: parse repeated Any from array ---

  private def parseRepeatedAny(arr: JsonNode): Seq[any.Any] =
    (0 until arr.size).map(i => base64ToAny(arr.get(i).asText()))

  // --- ProcessCommandRequest (incoming from HTTP client) ---

  def toJson(request: ProcessCommandRequest): String = {
    val obj = jsonMapper.createObjectNode()
    if (request.entityId.nonEmpty) obj.put("entity_id", request.entityId)
    putOptionalAny(obj, "command", request.command)
    jsonMapper.writeValueAsString(obj)
  }

  def parseProcessCommandRequest(json: String): ProcessCommandRequest =
    Try {
      val root     = jsonMapper.readTree(json)
      val entityId = if (root.has("entity_id")) root.get("entity_id").asText("") else ""
      val command =
        if (root.has("command") && !root.get("command").isNull)
          Some(base64ToAny(root.get("command").asText()))
        else
          None
      ProcessCommandRequest(entityId = entityId, command = command)
    }.getOrElse {
      throw new JsonParseException(s"Failed to parse ProcessCommandRequest from JSON")
    }

  implicit val processCommandRequestUnmarshaller: FromEntityUnmarshaller[ProcessCommandRequest] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map(parseProcessCommandRequest)

  // --- ProcessCommandResponse, GetStateResponse (HTTP API responses) ---

  protected def processCommandResponseToJson(r: ProcessCommandResponse): String = {
    val obj = jsonMapper.createObjectNode()
    putOptionalAny(obj, "state", r.state)
    r.meta.foreach { m => obj.set("meta", metaToJson(m)) }
    jsonMapper.writeValueAsString(obj)
  }

  protected def getStateResponseToJson(r: GetStateResponse): String = {
    val obj = jsonMapper.createObjectNode()
    putOptionalAny(obj, "state", r.state)
    r.meta.foreach { m => obj.set("meta", metaToJson(m)) }
    jsonMapper.writeValueAsString(obj)
  }

  protected def parseProcessCommandResponse(json: String): ProcessCommandResponse = {
    val root = jsonMapper.readTree(json)
    val state =
      if (root.has("state") && !root.get("state").isNull)
        Some(base64ToAny(root.get("state").asText()))
      else None
    val meta =
      if (root.has("meta") && !root.get("meta").isNull) Some(parseMeta(root.get("meta"))) else None
    ProcessCommandResponse(state = state, meta = meta)
  }

  protected def parseGetStateResponse(json: String): GetStateResponse = {
    val root = jsonMapper.readTree(json)
    val state =
      if (root.has("state") && !root.get("state").isNull)
        Some(base64ToAny(root.get("state").asText()))
      else None
    val meta =
      if (root.has("meta") && !root.get("meta").isNull) Some(parseMeta(root.get("meta"))) else None
    GetStateResponse(state = state, meta = meta)
  }

  implicit val processCommandResponseMarshaller: ToEntityMarshaller[ProcessCommandResponse] =
    Marshaller.opaque { r =>
      HttpEntity(ContentTypes.`application/json`, processCommandResponseToJson(r))
    }

  implicit val getStateResponseMarshaller: ToEntityMarshaller[GetStateResponse] =
    Marshaller.opaque { r =>
      HttpEntity(ContentTypes.`application/json`, getStateResponseToJson(r))
    }

  // --- HandleCommandRequest, HandleCommandResponse (write-side HTTP) ---

  def toJson(request: HandleCommandRequest): String = {
    val obj = jsonMapper.createObjectNode()
    putOptionalAny(obj, "command", request.command)
    putOptionalAny(obj, "prior_state", request.priorState)
    request.priorEventMeta.foreach { m => obj.set("prior_event_meta", metaToJson(m)) }
    jsonMapper.writeValueAsString(obj)
  }

  def parseHandleCommandResponse(json: String): HandleCommandResponse = {
    val root = jsonMapper.readTree(json)
    var r    = HandleCommandResponse()

    // Handle deprecated single event
    if (root.has("event") && !root.get("event").isNull)
      r = r.withEvent(base64ToAny(root.get("event").asText()))

    // Handle events array (takes precedence per proto spec)
    if (root.has("events") && root.get("events").isArray)
      r = r.withEvents(parseRepeatedAny(root.get("events")))

    r
  }

  // --- HandleEventRequest, HandleEventResponse (write-side HTTP) ---

  def toJson(request: HandleEventRequest): String = {
    val obj = jsonMapper.createObjectNode()
    putOptionalAny(obj, "event", request.event)
    putOptionalAny(obj, "prior_state", request.priorState)
    request.eventMeta.foreach { m => obj.set("event_meta", metaToJson(m)) }
    jsonMapper.writeValueAsString(obj)
  }

  def parseHandleEventResponse(json: String): HandleEventResponse = {
    val root = jsonMapper.readTree(json)
    val resultingState =
      if (root.has("resulting_state") && !root.get("resulting_state").isNull)
        Some(base64ToAny(root.get("resulting_state").asText()))
      else
        None
    HandleEventResponse(resultingState = resultingState)
  }

  // --- HandleReadSideRequest, HandleReadSideResponse (read-side HTTP) ---

  def toJson(request: HandleReadSideRequest): String = {
    val obj = jsonMapper.createObjectNode()
    putOptionalAny(obj, "event", request.event)
    putOptionalAny(obj, "state", request.state)
    request.meta.foreach { m => obj.set("meta", metaToJson(m)) }
    obj.put("read_side_id", request.readSideId)
    jsonMapper.writeValueAsString(obj)
  }

  def parseHandleReadSideResponse(json: String): HandleReadSideResponse = {
    val root = jsonMapper.readTree(json)
    val successful =
      root.has("successful") && !root.get("successful").isNull && root.get("successful").asBoolean()
    HandleReadSideResponse(successful = successful)
  }

  // --- GetStateRequest (no Any; used for completeness / tests) ---

  def toJson(request: GetStateRequest): String = {
    val obj = jsonMapper.createObjectNode()
    obj.put("entity_id", request.entityId)
    jsonMapper.writeValueAsString(obj)
  }

  def parseGetStateRequest(json: String): GetStateRequest = {
    val root     = jsonMapper.readTree(json)
    val entityId = if (root.has("entity_id")) root.get("entity_id").asText("") else ""
    GetStateRequest(entityId = entityId)
  }

  /**
   * Generic parseJson for HTTP handler responses and request types.
   * Uses type-class pattern for type-specific parsing.
   */
  def parseJson[T](json: String)(using ev: ParseJsonSelector[T]): T = ev.parse(json)

  /**
   * Type-class to select the correct parse method for each message type.
   */
  sealed trait ParseJsonSelector[T] {
    def parse(json: String): T
  }

  // Scala 3 given instances for type-class resolution
  given processCommandRequestSelector: ParseJsonSelector[ProcessCommandRequest] with {
    def parse(json: String): ProcessCommandRequest = parseProcessCommandRequest(json)
  }

  given getStateRequestSelector: ParseJsonSelector[GetStateRequest] with {
    def parse(json: String): GetStateRequest = parseGetStateRequest(json)
  }

  given handleCommandResponseSelector: ParseJsonSelector[HandleCommandResponse] with {
    def parse(json: String): HandleCommandResponse = parseHandleCommandResponse(json)
  }

  given handleEventResponseSelector: ParseJsonSelector[HandleEventResponse] with {
    def parse(json: String): HandleEventResponse = parseHandleEventResponse(json)
  }

  given handleReadSideResponseSelector: ParseJsonSelector[HandleReadSideResponse] with {
    def parse(json: String): HandleReadSideResponse = parseHandleReadSideResponse(json)
  }
}

object JsonSupport extends JsonSupport

/**
 * Custom exception for JSON parsing errors in HTTP protocol.
 */
class JsonParseException(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)
