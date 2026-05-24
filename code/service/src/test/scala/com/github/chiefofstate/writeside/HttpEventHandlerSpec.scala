/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.http.JsonSupport
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern.CircuitBreaker

import java.util.Base64
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

class HttpEventHandlerSpec
    extends BaseActorSpec("""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    """)
    with JsonSupport {

  implicit val sys: ActorSystem[_]  = testKit.system
  implicit val ec: ExecutionContext = testKit.system.executionContext

  private def withServer[A](route: Route)(f: String => A): A = {
    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), 5.seconds)
    try f(s"http://127.0.0.1:${binding.localAddress.getPort}")
    finally Await.result(binding.unbind(), 5.seconds)
  }

  private val event      = any.Any.pack(StringValue("event"))
  private val priorState = any.Any.pack(StringValue("prior"))
  private val meta       = MetaData().withEntityId("e")

  private def eventResponseJson(st: any.Any): String =
    s"""{"resulting_state":"${Base64.getEncoder.encodeToString(st.toByteArray)}"}"""
  private val emptyEventResponseJson = "{}"

  "HttpEventHandler" should {
    "return parsed resulting_state on 200 OK" in {
      val st = any.Any.pack(StringValue("st"))
      val route = post {
        path("v1" / "writeside" / "handleEvent") {
          complete(eventResponseJson(st))
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpEventHandler(baseUrl, 2000L)
        val r       = handler.handleEvent(event, priorState, meta)
        r.success.value.resultingState.value.typeUrl shouldBe st.typeUrl
      }
    }

    "fail on non-2xx response" in {
      val route = post {
        path("v1" / "writeside" / "handleEvent") {
          complete(StatusCodes.BadRequest -> "nope")
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpEventHandler(baseUrl, 2000L)
        val r       = handler.handleEvent(event, priorState, meta)
        r.failure.exception.getMessage should include("400")
      }
    }

    "run through the circuit breaker without tripping on a success" in {
      val breaker = new CircuitBreaker(
        scheduler = testKit.system.classicSystem.scheduler,
        maxFailures = 5,
        callTimeout = 1.second,
        resetTimeout = 1.second
      )
      val route = post {
        path("v1" / "writeside" / "handleEvent") {
          complete(emptyEventResponseJson)
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpEventHandler(baseUrl, 2000L, Some(breaker))
        handler.handleEvent(event, priorState, meta).isSuccess shouldBe true
      }
    }
  }
}
