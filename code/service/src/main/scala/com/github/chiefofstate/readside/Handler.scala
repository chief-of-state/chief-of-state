/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.github.chiefofstate.protobuf.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

/**
 * read side processor that sends messages to a gRPC server that implements
 * the ReadSideHandler service
 *
 * @param processorId the unique Id for this read side
 * @param readSideHandlerServiceBlockingStub a blocking client for a ReadSideHandler
 */
private[readside] class HandlerImpl(
    processorId: String,
    readSideHandlerServiceBlockingStub: ReadSideHandlerServiceBlockingStub
) extends Handler {

  private val COS_EVENT_TAG_HEADER = "x-cos-event-tag"
  private val COS_ENTITY_ID_HEADER = "x-cos-entity-id"

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Processes events read from the Journal
   *
   * @param event          the actual event
   * @param eventTag       the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta           the additional meta data
   * @return an eventual HandleReadSideResponse
   */
  @WithSpan(value = "ReadSideHandler.HandleEvent")
  def processEvent(
      event: com.google.protobuf.any.Any,
      eventTag: String,
      resultingState: com.google.protobuf.any.Any,
      meta: MetaData
  ): Boolean = {
    val response: Try[HandleReadSideResponse] = Try {
      val headers = new Metadata()
      headers.put(
        Metadata.Key.of(COS_ENTITY_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER),
        meta.entityId
      )
      headers.put(Metadata.Key.of(COS_EVENT_TAG_HEADER, Metadata.ASCII_STRING_MARSHALLER), eventTag)

      readSideHandlerServiceBlockingStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
        .handleReadSide(
          HandleReadSideRequest()
            .withEvent(event)
            .withState(resultingState)
            .withMeta(meta)
            .withReadSideId(processorId)
        )
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

private[readside] trait Handler {

  /**
   * Processes events read from the Journal
   *
   * @param event          the actual event
   * @param eventTag       the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta           the additional meta data
   * @return an eventual HandleReadSideResponse
   */
  def processEvent(
      event: com.google.protobuf.any.Any,
      eventTag: String,
      resultingState: com.google.protobuf.any.Any,
      meta: MetaData
  ): Boolean
}
