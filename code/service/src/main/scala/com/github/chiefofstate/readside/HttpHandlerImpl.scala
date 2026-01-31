/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.http.JsonSupport
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import org.slf4j.{Logger, LoggerFactory}

import javax.net.ssl.SSLContext
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * HTTP-based read side handler that sends events to an HTTP endpoint
 *
 * @param processorId the unique ID for this read side
 * @param baseUrl the base URL of the HTTP endpoint (e.g., "http://handler:8080" or "https://handler:8443")
 * @param timeout timeout for HTTP requests in milliseconds
 * @param circuitBreaker optional circuit breaker for resilience
 * @param system actor system for HTTP client
 * @param ec execution context
 */
private[readside] class HttpHandlerImpl(
    processorId: String,
    baseUrl: String,
    timeout: Long,
    circuitBreaker: Option[CircuitBreaker] = None
)(implicit system: ActorSystem[_], ec: ExecutionContext)
    extends Handler
    with JsonSupport {

  private val COS_EVENT_TAG_HEADER = "x-cos-event-tag"
  private val COS_ENTITY_ID_HEADER = "x-cos-entity-id"

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val http           = Http()

  // Default endpoint path (can be made configurable)
  private val endpoint = s"$baseUrl/v1/readside/handle"
  private val isHttps  = baseUrl.startsWith("https://")

  // Create HTTPS connection context using default SSL context
  private val httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.httpsClient(SSLContext.getDefault)

  /**
   * Processes events read from the Journal
   *
   * @param event          the actual event
   * @param eventTag       the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta           the additional meta data
   * @return an eventual HandleReadSideResponse
   */
  @WithSpan(value = "HttpReadSideHandler.HandleEvent")
  def processEvent(
      event: com.google.protobuf.any.Any,
      eventTag: String,
      resultingState: com.google.protobuf.any.Any,
      meta: MetaData
  ): Boolean = {
    // Build the HTTP request
    def makeHttpCall(): HandleReadSideResponse = {
      val request = HandleReadSideRequest()
        .withEvent(event)
        .withState(resultingState)
        .withMeta(meta)
        .withReadSideId(processorId)

      // Convert protobuf to JSON
      val jsonBody = toJson(request)

      // Create HTTP request with headers
      val httpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = endpoint,
        headers = List(
          headers.RawHeader(COS_ENTITY_ID_HEADER, meta.entityId),
          headers.RawHeader(COS_EVENT_TAG_HEADER, eventTag)
        ),
        entity = HttpEntity(ContentTypes.`application/json`, jsonBody)
      )

      // Make the HTTP call and wait for response (use HTTPS context for https:// URLs)
      val responseFuture: Future[HandleReadSideResponse] = for {
        response <-
          if (isHttps) {
            http.singleRequest(httpRequest, connectionContext = httpsConnectionContext)
          } else {
            http.singleRequest(httpRequest)
          }
        result <- response.status match {
          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { jsonString =>
              parseJson[HandleReadSideResponse](jsonString)
            }
          case statusCode =>
            // Log response body before discarding for debugging
            Unmarshal(response.entity).to[String].map { body =>
              logger.error(
                s"HTTP read-side handler returned $statusCode for processor=$processorId, entity=${meta.entityId}, body=$body"
              )
              throw new RuntimeException(
                s"HTTP request failed with status $statusCode: ${body.take(200)}"
              )
            }
        }
      } yield result

      // Block and wait for the response (matching gRPC blocking stub behavior)
      Await.result(responseFuture, timeout.milliseconds)
    }

    // Wrap call with circuit breaker if present
    val response: Try[HandleReadSideResponse] = circuitBreaker match {
      case Some(breaker) =>
        Try {
          breaker.withSyncCircuitBreaker(makeHttpCall())
        }
      case None =>
        Try(makeHttpCall())
    }

    // return the response
    response match {
      // return true when the remote server responds with "true"
      case Success(value) if value.successful =>
        logger.debug(s"success for id=${meta.entityId}, revisionNumber=${meta.revisionNumber}")
        true

      // return false when remote server responds with "false"
      case Success(_) =>
        logger.warn(
          s"read side returned failure, processor=$processorId, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}"
        )
        false

      // Circuit breaker is open - fail fast
      case Failure(_: CircuitBreakerOpenException) =>
        logger.warn(
          s"read side circuit breaker open, processor=$processorId, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}"
        )
        false

      // return false when remote server fails
      case Failure(exception) =>
        logger.error(
          s"read side processing failure, processor=$processorId, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}, cause=${exception.getMessage}"
        )
        // for debug purposes, log the stack trace as well
        logger.debug("remote handler failure", exception)
        false
    }
  }
}
