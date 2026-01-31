/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.http.JsonSupport
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.writeside.{HandleEventRequest, HandleEventResponse}
import com.google.protobuf.any
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.pattern.CircuitBreaker
import org.slf4j.{Logger, LoggerFactory}

import javax.net.ssl.SSLContext
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/**
 * handles a given event by making an HTTP call
 *
 * @param baseUrl the base URL of the write handler service (http:// or https://)
 * @param timeout the request timeout in milliseconds
 * @param circuitBreaker optional circuit breaker for resilience
 * @param system actor system for HTTP client
 * @param ec execution context
 */
case class HttpEventHandler(
    baseUrl: String,
    timeout: Long,
    circuitBreaker: Option[CircuitBreaker] = None
)(implicit system: ActorSystem[_], ec: ExecutionContext)
    extends WriteSideEventHandler
    with JsonSupport {

  private val log: Logger = LoggerFactory.getLogger(getClass)
  private val http        = Http()
  private val endpoint    = s"$baseUrl/v1/writeside/handleEvent"
  private val isHttps     = baseUrl.startsWith("https://")

  // Create HTTPS connection context using default SSL context
  private val httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.httpsClient(SSLContext.getDefault)

  /**
   * handles the given event and return an eventual response
   *
   * @param event the event to handle
   * @param priorState the aggregate prior state
   * @return the eventual HandleEventResponse
   */
  @WithSpan(value = "HttpEventHandler.HandleEvent")
  def handleEvent(
      event: any.Any,
      priorState: any.Any,
      eventMeta: MetaData
  ): Try[HandleEventResponse] = {
    log.debug(s"sending HTTP request to the event handler, ${event.typeUrl}")

    // Build the HTTP call
    def makeHttpCall(): HandleEventResponse = {
      val request = HandleEventRequest()
        .withEvent(event)
        .withPriorState(priorState)
        .withEventMeta(eventMeta)

      val jsonBody = toJson(request)

      val httpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = endpoint,
        entity = HttpEntity(ContentTypes.`application/json`, jsonBody)
      )

      // Use HTTPS connection context for https:// URLs
      val responseFuture: Future[HandleEventResponse] = for {
        response <-
          if (isHttps) {
            http.singleRequest(httpRequest, connectionContext = httpsConnectionContext)
          } else {
            http.singleRequest(httpRequest)
          }
        result <- response.status match {
          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { jsonString =>
              parseJson[HandleEventResponse](jsonString)
            }
          case statusCode =>
            // Log response body before discarding for debugging
            Unmarshal(response.entity).to[String].map { body =>
              log.error(s"HTTP event handler returned $statusCode for ${event.typeUrl}, body=$body")
              throw new RuntimeException(
                s"HTTP event handler request failed with status $statusCode: ${body.take(200)}"
              )
            }
        }
      } yield result

      Await.result(responseFuture, timeout.milliseconds)
    }

    // Wrap call with circuit breaker if present
    circuitBreaker match {
      case Some(breaker) =>
        Try {
          breaker.withSyncCircuitBreaker(makeHttpCall())
        }
      case None =>
        Try(makeHttpCall())
    }
  }
}
