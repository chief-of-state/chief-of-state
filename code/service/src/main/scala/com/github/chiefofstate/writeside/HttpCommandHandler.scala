/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.http.JsonSupport
import com.github.chiefofstate.protobuf.v1.common.Header.Value
import com.github.chiefofstate.protobuf.v1.internal.RemoteCommand
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.writeside.{HandleCommandRequest, HandleCommandResponse}
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
 * handles command via an HTTP call
 *
 * @param baseUrl the base URL of the write handler service (http:// or https://)
 * @param timeout the request timeout in milliseconds
 * @param circuitBreaker optional circuit breaker for resilience
 * @param system actor system for HTTP client
 * @param ec execution context
 */
case class HttpCommandHandler(
    baseUrl: String,
    timeout: Long,
    circuitBreaker: Option[CircuitBreaker] = None
)(implicit system: ActorSystem[_], ec: ExecutionContext)
    extends WriteSideCommandHandler
    with JsonSupport {

  private val log: Logger = LoggerFactory.getLogger(getClass)
  private val http        = Http()
  private val endpoint    = s"$baseUrl/v1/writeside/handleCommand"
  private val isHttps     = baseUrl.startsWith("https://")

  // Create HTTPS connection context using default SSL context
  private val httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.httpsClient(SSLContext.getDefault)

  /**
   * handles the given command and return an eventual response
   *
   * @param remoteCommand the command to handle
   * @param priorState the aggregate state before the command to handle
   * @return an eventual HandleCommandResponse
   */
  @WithSpan(value = "HttpCommandHandler.HandleCommand")
  def handleCommand(
      remoteCommand: RemoteCommand,
      priorState: StateWrapper
  ): Try[HandleCommandResponse] = {
    log.debug(s"sending HTTP request to the command handler, ${remoteCommand.getCommand.typeUrl}")

    // Build the HTTP call
    def makeHttpCall(): HandleCommandResponse = {
      // Build headers from propagated headers
      val httpHeaders = remoteCommand.propagatedHeaders.flatMap { header =>
        header.value match {
          case Value.StringValue(value) =>
            Some(headers.RawHeader(header.key, value))
          case Value.BytesValue(value) =>
            // For binary headers, base64 encode
            Some(
              headers.RawHeader(
                header.key,
                java.util.Base64.getEncoder.encodeToString(value.toByteArray)
              )
            )
          case Value.Empty =>
            None
        }
      }.toList

      val request = HandleCommandRequest()
        .withPriorState(priorState.getState)
        .withCommand(remoteCommand.getCommand)
        .withPriorEventMeta(priorState.getMeta)

      val jsonBody = toJson(request)

      val httpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = endpoint,
        headers = httpHeaders,
        entity = HttpEntity(ContentTypes.`application/json`, jsonBody)
      )

      // Use HTTPS connection context for https:// URLs
      val responseFuture: Future[HandleCommandResponse] = for {
        response <-
          if (isHttps) {
            http.singleRequest(httpRequest, connectionContext = httpsConnectionContext)
          } else {
            http.singleRequest(httpRequest)
          }
        result <- response.status match {
          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { jsonString =>
              parseJson[HandleCommandResponse](jsonString)
            }
          case statusCode =>
            response.entity.discardBytes()
            Future.failed(
              new RuntimeException(s"HTTP command handler request failed with status $statusCode")
            )
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
