/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.http

import com.github.chiefofstate.config.WriteSideConfig
import com.github.chiefofstate.interceptors.MetadataInterceptor
import com.github.chiefofstate.protobuf.v1.service._
import com.github.chiefofstate.services.CosServiceApi
import io.grpc.{Context, Metadata}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * HTTP routes for ChiefOfState service
 *
 * @param cosService the underlying CosServiceApi that handles commands
 * @param writeSideConfig write side configuration for header propagation
 * @param ec execution context
 */
class HttpRoutes(
    cosService: CosServiceApi,
    writeSideConfig: WriteSideConfig
)(implicit ec: ExecutionContext)
    extends JsonSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Main route combining all endpoints
   */
  val routes: Route = pathPrefix("v1") {
    processCommandRoute ~ getStateRoute
  }

  /**
   * POST /v1/commands/:entityId/process
   * Body: ProcessCommandRequest (without entity_id, as it's in the path)
   */
  private def processCommandRoute: Route = {
    path("commands" / Segment / "process") { entityId =>
      post {
        extractRequest { httpRequest =>
          entity(as[ProcessCommandRequest]) { requestBody =>
            // Set up gRPC-style metadata from HTTP headers
            val metadata = new Metadata()

            httpRequest.headers.foreach { header =>
              val key = Metadata.Key.of(header.name(), Metadata.ASCII_STRING_MARSHALLER)
              metadata.put(key, header.value())
            }

            // Attach metadata to gRPC context (matching gRPC interceptor behavior)
            val grpcContext =
              Context.current().withValue(MetadataInterceptor.REQUEST_META, metadata)
            val previousCtx = grpcContext.attach()

            // Ensure entity_id in request matches path parameter
            val request = if (requestBody.entityId.isEmpty) {
              requestBody.withEntityId(entityId)
            } else if (requestBody.entityId != entityId) {
              logger.warn(s"Entity ID mismatch: path=$entityId, body=${requestBody.entityId}")
              requestBody.withEntityId(entityId) // path takes precedence
            } else {
              requestBody
            }

            val futureResponse: Future[ProcessCommandResponse] = cosService.processCommand(request)

            onComplete(futureResponse) { result =>
              // Detach context after completion
              grpcContext.detach(previousCtx)
              result match {
                case Success(response) =>
                  complete(StatusCodes.OK, response)
                case Failure(ex) =>
                  logger.error(s"Failed to process command for entity $entityId", ex)
                  complete(StatusCodes.InternalServerError, errorJson(ex.getMessage))
              }
            }
          }
        }
      }
    }
  }

  /**
   * GET /v1/entities/:entityId/state
   */
  private def getStateRoute: Route = {
    path("entities" / Segment / "state") { entityId =>
      get {
        extractRequest { httpRequest =>
          // Set up gRPC-style metadata from HTTP headers
          val metadata = new Metadata()

          httpRequest.headers.foreach { header =>
            val key = Metadata.Key.of(header.name(), Metadata.ASCII_STRING_MARSHALLER)
            metadata.put(key, header.value())
          }

          // Attach metadata to gRPC context
          val grpcContext = Context.current().withValue(MetadataInterceptor.REQUEST_META, metadata)
          val previousCtx = grpcContext.attach()

          val request                                  = GetStateRequest(entityId = entityId)
          val futureResponse: Future[GetStateResponse] = cosService.getState(request)

          onComplete(futureResponse) { result =>
            // Detach context after completion
            grpcContext.detach(previousCtx)
            result match {
              case Success(response) =>
                complete(StatusCodes.OK, response)
              case Failure(ex) =>
                logger.error(s"Failed to get state for entity $entityId", ex)
                complete(StatusCodes.InternalServerError, errorJson(ex.getMessage))
            }
          }
        }
      }
    }
  }

  /**
   * Helper to create error JSON response
   */
  private def errorJson(message: String): HttpEntity.Strict = {
    val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
    HttpEntity(ContentTypes.`application/json`, s"""{"error":"$escapedMessage"}""")
  }
}
