/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.config.GrpcConfig
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.protobuf.v1.writeside.{ HandleEventRequest, HandleEventResponse }
import com.google.protobuf.any
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.TimeUnit
import scala.util.Try

/**
 * handles a given event by making a rpc call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServiceStub the grpc client stub
 */
case class RemoteEventHandler(grpcConfig: GrpcConfig, writeHandlerServiceStub: WriteSideHandlerServiceBlockingStub) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * handles the given event and return an eventual response
   *
   * @param event the event to handle
   * @param priorState the aggregate prior state
   * @return the eventual HandleEventResponse
   */
  @WithSpan(value = "RemoteCommandHandler.HandleEvent")
  def handleEvent(event: any.Any, priorState: any.Any, eventMeta: MetaData): Try[HandleEventResponse] = {
    Try {
      log.debug(s"sending request to the event handler, ${event.typeUrl}")

      writeHandlerServiceStub
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleEvent(HandleEventRequest().withEvent(event).withPriorState(priorState).withEventMeta(eventMeta))
    }
  }
}
