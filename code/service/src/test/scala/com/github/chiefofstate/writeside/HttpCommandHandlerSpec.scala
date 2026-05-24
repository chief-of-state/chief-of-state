/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.http.JsonSupport
import com.github.chiefofstate.protobuf.v1.common.Header
import com.github.chiefofstate.protobuf.v1.internal.RemoteCommand
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.google.protobuf.ByteString
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

class HttpCommandHandlerSpec
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

  private def stateWrapper: StateWrapper = StateWrapper.defaultInstance

  private def cmd(body: String): RemoteCommand =
    RemoteCommand(
      entityId = "e",
      command = Some(any.Any.pack(StringValue(body))),
      propagatedHeaders = Seq.empty,
      persistedHeaders = Seq.empty
    )

  private def commandResponseJson(events: any.Any*): String = {
    val arr =
      events.map(e => "\"" + Base64.getEncoder.encodeToString(e.toByteArray) + "\"").mkString(",")
    s"""{"events":[$arr]}"""
  }
  private val emptyCommandResponseJson = "{}"

  "HttpCommandHandler" should {
    "return parsed events on 200 OK" in {
      val ev = any.Any.pack(StringValue("event"))
      val route = post {
        path("v1" / "writeside" / "handleCommand") {
          complete(commandResponseJson(ev))
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpCommandHandler(baseUrl, 2000L)
        val result  = handler.handleCommand(cmd("c"), stateWrapper)
        result.success.value.events.head.typeUrl shouldBe ev.typeUrl
      }
    }

    "fail on non-2xx response" in {
      val route = post {
        path("v1" / "writeside" / "handleCommand") {
          complete(StatusCodes.InternalServerError -> "boom")
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpCommandHandler(baseUrl, 2000L)
        val result  = handler.handleCommand(cmd("c"), stateWrapper)
        result.failure.exception.getMessage should include("500")
      }
    }

    "propagate string and bytes headers in the HTTP request" in {
      val received = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
      val route = post {
        path("v1" / "writeside" / "handleCommand") {
          extractRequest { req =>
            req.headers.foreach(h => received += (h.name -> h.value))
            complete(emptyCommandResponseJson)
          }
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpCommandHandler(baseUrl, 2000L)
        val rc = cmd("c").copy(
          propagatedHeaders = Seq(
            Header(key = "x-str").withStringValue("hello"),
            Header(key = "x-bin").withBytesValue(ByteString.copyFromUtf8("raw"))
          )
        )
        handler.handleCommand(rc, stateWrapper).success.value
        received.exists(_ == ("x-str" -> "hello")) shouldBe true
        received.exists { case (k, _) => k == "x-bin" } shouldBe true
      }
    }

    "honour the circuit breaker (passes a successful call through)" in {
      val breaker = new CircuitBreaker(
        scheduler = testKit.system.classicSystem.scheduler,
        maxFailures = 5,
        callTimeout = 1.second,
        resetTimeout = 1.second
      )
      val route = post {
        path("v1" / "writeside" / "handleCommand") {
          complete(emptyCommandResponseJson)
        }
      }
      withServer(route) { baseUrl =>
        val handler = HttpCommandHandler(baseUrl, 2000L, Some(breaker))
        handler.handleCommand(cmd("c"), stateWrapper).isSuccess shouldBe true
      }
    }
  }
}
