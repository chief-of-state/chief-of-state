/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.github.chiefofstate.protobuf.v1.service.GetStateRequest
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import scalapb.GeneratedMessage
import scalapb.json4s.{Parser, Printer, TypeRegistry}

/**
 * JSON marshalling support for protobuf messages.
 * Uses a TypeRegistry for proper serialization of google.protobuf.Any fields.
 */
trait JsonSupport {

  /**
   * Type registry for types that can appear in Any fields.
   * Extend this registry when using custom command/state/event types in HTTP API.
   */
  protected def typeRegistry: TypeRegistry = JsonSupport.defaultTypeRegistry

  private lazy val printer: Printer = new Printer().withTypeRegistry(typeRegistry)
  private lazy val parser: Parser   = new Parser().withTypeRegistry(typeRegistry)

  /**
   * Unmarshaller for protobuf messages from JSON
   */
  implicit def protobufUnmarshaller[T <: GeneratedMessage: scalapb.GeneratedMessageCompanion]
      : FromEntityUnmarshaller[T] = {
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map { jsonString =>
        parser.fromJsonString[T](jsonString)
      }
  }

  /**
   * Marshaller for protobuf messages to JSON
   */
  implicit def protobufMarshaller[T <: GeneratedMessage]: ToEntityMarshaller[T] = {
    Marshaller.opaque { message =>
      val json = printer.print(message)
      HttpEntity(ContentTypes.`application/json`, json)
    }
  }

  /**
   * Parse JSON string to protobuf message
   */
  def parseJson[T <: GeneratedMessage: scalapb.GeneratedMessageCompanion](json: String): T = {
    parser.fromJsonString[T](json)
  }

  /**
   * Convert protobuf message to JSON string
   */
  def toJson[T <: GeneratedMessage](message: T): String = {
    printer.print(message)
  }
}

object JsonSupport extends JsonSupport {

  /**
   * Default TypeRegistry with Chief of State and common protobuf types.
   * Add custom command/state/event types when using HTTP API with user-defined protos.
   */
  val defaultTypeRegistry: TypeRegistry =
    TypeRegistry.default
      .addMessage[GetStateRequest]
      .addMessage[StringValue]
}
