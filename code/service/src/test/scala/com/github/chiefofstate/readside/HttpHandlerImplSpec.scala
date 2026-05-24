/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern.CircuitBreaker

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

class HttpHandlerImplSpec extends BaseActorSpec("""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    """) {

  implicit val sys: ActorSystem[_]  = testKit.system
  implicit val ec: ExecutionContext = testKit.system.executionContext

  private def withServer[A](route: Route)(f: String => A): A = {
    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), 5.seconds)
    try f(s"http://127.0.0.1:${binding.localAddress.getPort}")
    finally Await.result(binding.unbind(), 5.seconds)
  }

  private val event = any.Any.pack(StringValue("ev"))
  private val state = any.Any.pack(StringValue("st"))
  private val meta  = MetaData().withEntityId("e")

  "HttpHandlerImpl.processEvent" should {
    "return true on a successful response" in {
      val route = post {
        path("v1" / "readside" / "handle") {
          complete("""{"successful":true}""")
        }
      }
      withServer(route) { baseUrl =>
        val handler = new HttpHandlerImpl("rs-1", baseUrl, 2000L)
        handler.processEvent(event, "tag-1", state, meta) shouldBe true
      }
    }

    "return false when remote reports failure" in {
      val route = post {
        path("v1" / "readside" / "handle") {
          complete("""{"successful":false}""")
        }
      }
      withServer(route) { baseUrl =>
        val handler = new HttpHandlerImpl("rs-1", baseUrl, 2000L)
        handler.processEvent(event, "tag-1", state, meta) shouldBe false
      }
    }

    "return false on non-2xx response" in {
      val route = post {
        path("v1" / "readside" / "handle") {
          complete(StatusCodes.InternalServerError -> "boom")
        }
      }
      withServer(route) { baseUrl =>
        val handler = new HttpHandlerImpl("rs-1", baseUrl, 2000L)
        handler.processEvent(event, "tag-1", state, meta) shouldBe false
      }
    }

    "forward entity_id and event_tag headers" in {
      val received = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
      val route = post {
        path("v1" / "readside" / "handle") {
          extractRequest { req =>
            req.headers.foreach(h => received += (h.name -> h.value))
            complete("""{"successful":true}""")
          }
        }
      }
      withServer(route) { baseUrl =>
        val handler = new HttpHandlerImpl("rs-1", baseUrl, 2000L)
        handler.processEvent(event, "tag-1", state, meta.withEntityId("entity-A")) shouldBe true
        received.exists(_ == ("x-cos-entity-id" -> "entity-A")) shouldBe true
        received.exists(_ == ("x-cos-event-tag" -> "tag-1")) shouldBe true
      }
    }

    "return false when the circuit breaker is open" in {
      val breaker = new CircuitBreaker(
        scheduler = testKit.system.classicSystem.scheduler,
        maxFailures = 1,
        callTimeout = 1.second,
        resetTimeout = 1.minute
      )
      // Trip the breaker (the call rethrows the body's exception)
      try breaker.withSyncCircuitBreaker(throw new RuntimeException("fail"))
      catch { case _: RuntimeException => () }
      val route = post {
        path("v1" / "readside" / "handle") {
          complete("""{"successful":true}""")
        }
      }
      withServer(route) { baseUrl =>
        val handler = new HttpHandlerImpl("rs-1", baseUrl, 2000L, Some(breaker))
        handler.processEvent(event, "tag-1", state, meta) shouldBe false
      }
    }
  }
}
