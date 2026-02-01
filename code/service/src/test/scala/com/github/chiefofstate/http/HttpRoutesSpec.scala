/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.github.chiefofstate.config.{CircuitBreakerConfig, WriteSideConfig}
import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.service.{
  GetStateRequest,
  GetStateResponse,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import com.github.chiefofstate.protocol.ServerProtocol
import com.github.chiefofstate.services.CosServiceApi
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.http.scaladsl.model.{ContentTypes, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers

import java.util.Base64
import scala.concurrent.Future

class HttpRoutesSpec extends BaseSpec with Matchers with ScalatestRouteTest {

  // Prevent ActorSystem coordinated-shutdown from exiting the JVM, which causes
  // EOFException in sbt forked test harness when ScalatestRouteTest's ActorSystem terminates.
  override def testConfigSource: String =
    """
      |pekko.coordinated-shutdown.exit-jvm = off
      |pekko.coordinated-shutdown.terminate-actor-system = off
      |pekko.coordinated-shutdown.run-by-actor-system-terminate = off
      |pekko.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      |""".stripMargin

  val writeSideConfig: WriteSideConfig = WriteSideConfig(
    protocol = ServerProtocol.Http,
    host = "localhost",
    port = 8080,
    useTls = false,
    enableProtoValidation = false,
    eventsProtos = Seq.empty,
    statesProtos = Seq.empty,
    propagatedHeaders = Seq.empty,
    persistedHeaders = Seq.empty,
    circuitBreakerConfig = CircuitBreakerConfig.disabled()
  )

  def createHttpRoutes(cosService: CosServiceApi): HttpRoutes = {
    new HttpRoutes(cosService, writeSideConfig)(system.dispatcher)
  }

  "HttpRoutes" should {

    "POST /v1/commands/:entityId/process returns OK on success" in {
      val entityId = "entity-123"
      val response = ProcessCommandResponse()
        .withState(any.Any.pack(StringValue("state")))
        .withMeta(MetaData().withRevisionNumber(1))
      val cosService = mock[CosServiceApi]
      (cosService.processCommand _)
        .expects(ProcessCommandRequest(entityId = entityId))
        .returning(Future.successful(response))

      val routes = createHttpRoutes(cosService).routes
      val request = Post(s"/v1/commands/$entityId/process")
        .withEntity(ContentTypes.`application/json`, s"""{"entity_id":"$entityId"}""")

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType.mediaType shouldBe ContentTypes.`application/json`.mediaType
        val body = responseAs[String]
        body should include("state")
        body should include("meta")
      }
    }

    "POST /v1/commands/:entityId/process accepts base64-encoded command (custom types)" in {
      val entityId      = "entity-custom"
      val customCommand = any.Any.pack(StringValue("custom-data"))
      val commandBase64 = Base64.getEncoder.encodeToString(customCommand.toByteArray)
      val expectedRequest =
        ProcessCommandRequest(entityId = entityId, command = Some(customCommand))
      val response = ProcessCommandResponse()
        .withState(any.Any.pack(StringValue("state")))
        .withMeta(MetaData().withRevisionNumber(1))
      val cosService = mock[CosServiceApi]
      (cosService.processCommand _)
        .expects(expectedRequest)
        .returning(Future.successful(response))

      val routes = createHttpRoutes(cosService).routes
      val request = Post(s"/v1/commands/$entityId/process")
        .withEntity(
          ContentTypes.`application/json`,
          s"""{"entity_id":"$entityId","command":"$commandBase64"}"""
        )

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "POST /v1/commands/:entityId/process fills entity_id from path when missing in body" in {
      val entityId = "entity-from-path"
      val response = ProcessCommandResponse()
        .withState(any.Any.pack(StringValue("state")))
        .withMeta(MetaData().withRevisionNumber(1))
      val cosService = mock[CosServiceApi]
      (cosService.processCommand _)
        .expects(ProcessCommandRequest(entityId = entityId))
        .returning(Future.successful(response))

      val routes = createHttpRoutes(cosService).routes
      val request = Post(s"/v1/commands/$entityId/process")
        .withEntity(ContentTypes.`application/json`, "{}")

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "POST /v1/commands/:entityId/process returns InternalServerError on failure" in {
      val entityId   = "entity-456"
      val cosService = mock[CosServiceApi]
      (cosService.processCommand _)
        .expects(ProcessCommandRequest(entityId = entityId))
        .returning(Future.failed(new RuntimeException("service error")))

      val routes = createHttpRoutes(cosService).routes
      val request = Post(s"/v1/commands/$entityId/process")
        .withEntity(ContentTypes.`application/json`, s"""{"entity_id":"$entityId"}""")

      request ~> routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("error")
        responseAs[String] should include("service error")
      }
    }

    "GET /v1/entities/:entityId/state returns OK on success" in {
      val entityId = "entity-789"
      val response = GetStateResponse()
        .withState(any.Any.pack(StringValue("current-state")))
        .withMeta(MetaData().withRevisionNumber(2))
      val cosService = mock[CosServiceApi]
      (cosService.getState _)
        .expects(GetStateRequest(entityId = entityId))
        .returning(Future.successful(response))

      val routes  = createHttpRoutes(cosService).routes
      val request = Get(s"/v1/entities/$entityId/state")

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType.mediaType shouldBe ContentTypes.`application/json`.mediaType
        val body = responseAs[String]
        body should include("state")
        body should include("meta")
        body should include("revision_number")
      }
    }

    "GET /v1/entities/:entityId/state returns InternalServerError on failure" in {
      val entityId   = "entity-fail"
      val cosService = mock[CosServiceApi]
      (cosService.getState _)
        .expects(GetStateRequest(entityId = entityId))
        .returning(Future.failed(new RuntimeException("get state failed")))

      val routes  = createHttpRoutes(cosService).routes
      val request = Get(s"/v1/entities/$entityId/state")

      request ~> routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("error")
        responseAs[String] should include("get state failed")
      }
    }

    "reject requests to unknown paths" in {
      val cosService = mock[CosServiceApi]
      val routes     = createHttpRoutes(cosService).routes

      Get("/v1/unknown") ~> routes ~> check {
        handled shouldBe false
      }
    }

    "reject POST without valid JSON for process command" in {
      val entityId   = "entity-123"
      val cosService = mock[CosServiceApi]
      val routes     = Route.seal(createHttpRoutes(cosService).routes)
      val request = Post(s"/v1/commands/$entityId/process")
        .withEntity(ContentTypes.`application/json`, "invalid json")

      request ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }
}
