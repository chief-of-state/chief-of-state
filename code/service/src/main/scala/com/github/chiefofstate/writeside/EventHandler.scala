/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.config.GrpcConfig
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.protobuf.v1.writeside.{HandleEventRequest, HandleEventResponse}
import com.google.protobuf.any
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.apache.pekko.pattern.CircuitBreaker
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit
import scala.util.Try

/**
 * handles a given event by making a rpc call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServiceStub the grpc client stub
 * @param circuitBreaker optional circuit breaker for resilience
 */
case class EventHandler(
    grpcConfig: GrpcConfig,
    writeHandlerServiceStub: WriteSideHandlerServiceBlockingStub,
    circuitBreaker: Option[CircuitBreaker] = None
) extends WriteSideEventHandler {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * handles the given event and return an eventual response
   *
   * @param event the event to handle
   * @param priorState the aggregate prior state
   * @return the eventual HandleEventResponse
   */
  @WithSpan(value = "RemoteCommandHandler.HandleEvent")
  def handleEvent(
      event: any.Any,
      priorState: any.Any,
      eventMeta: MetaData
  ): Try[HandleEventResponse] = {
    log.debug(s"sending request to the event handler, ${event.typeUrl}")

    // Build the gRPC call
    def makeGrpcCall(): HandleEventResponse = {
      writeHandlerServiceStub
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleEvent(
          HandleEventRequest().withEvent(event).withPriorState(priorState).withEventMeta(eventMeta)
        )
    }

    // Wrap call with circuit breaker if present
    circuitBreaker match {
      case Some(breaker) =>
        Try {
          breaker.withSyncCircuitBreaker(makeGrpcCall())
        }
      case None =>
        Try(makeGrpcCall())
    }
  }
}
