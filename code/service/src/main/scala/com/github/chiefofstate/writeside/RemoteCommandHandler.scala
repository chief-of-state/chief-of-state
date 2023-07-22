/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.config.GrpcConfig
import com.github.chiefofstate.protobuf.v1.common.Header.Value
import com.github.chiefofstate.protobuf.v1.internal.RemoteCommand
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.protobuf.v1.writeside.{ HandleCommandRequest, HandleCommandResponse }
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.TimeUnit
import scala.util.Try

/**
 * handles command via a gRPC call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServiceStub the grpc client stub
 */
case class RemoteCommandHandler(grpcConfig: GrpcConfig, writeHandlerServiceStub: WriteSideHandlerServiceBlockingStub) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * handles the given command and return an eventual response
   *
   * @param remoteCommand the command to handle
   * @param priorState the aggregate state before the command to handle
   * @return an eventual HandleCommandResponse
   */
  @WithSpan(value = "RemoteCommandHandler.HandleCommand")
  def handleCommand(remoteCommand: RemoteCommand, priorState: StateWrapper): Try[HandleCommandResponse] = {
    log.debug(s"sending request to the command handler, ${remoteCommand.getCommand.typeUrl}")

    // let us set the client request headers
    val headers: Metadata = new Metadata()

    Try {
      remoteCommand.propagatedHeaders.foreach { header =>
        header.value match {
          case Value.StringValue(value) =>
            headers.put(Metadata.Key.of(header.key, Metadata.ASCII_STRING_MARSHALLER), value)
          case Value.BytesValue(value) =>
            headers.put(Metadata.Key.of(header.key, Metadata.BINARY_BYTE_MARSHALLER), value.toByteArray)
          case Value.Empty =>
            throw new RuntimeException("header value must be string or bytes")
        }
      }

      writeHandlerServiceStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleCommand(
          HandleCommandRequest()
            .withPriorState(priorState.getState)
            .withCommand(remoteCommand.getCommand)
            .withPriorEventMeta(priorState.getMeta))
    }
  }
}
