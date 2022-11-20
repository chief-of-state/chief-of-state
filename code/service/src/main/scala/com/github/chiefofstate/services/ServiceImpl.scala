/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.services

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityRef }
import akka.util.Timeout
import com.github.chiefofstate.AggregateRoot
import com.github.chiefofstate.config.WriteSideConfig
import com.github.chiefofstate.interceptors.MetadataInterceptor
import com.github.chiefofstate.observability.Telemetry
import com.github.chiefofstate.protobuf.v1.common.Header
import com.github.chiefofstate.protobuf.v1.internal.CommandReply.Reply
import com.github.chiefofstate.protobuf.v1.internal._
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.service._
import com.github.chiefofstate.serialization.SendReceive
import com.github.chiefofstate.utils.Util
import com.google.protobuf.any
import com.google.rpc.status.Status.toJavaProto
import io.grpc.protobuf.StatusProto
import io.grpc.{ Metadata, Status, StatusException }
import io.opentelemetry.context.Context
import org.slf4j.{ Logger, LoggerFactory }
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class ServiceImpl(clusterSharding: ClusterSharding, writeSideConfig: WriteSideConfig)(implicit val askTimeout: Timeout)
    extends ChiefOfStateServiceGrpc.ChiefOfStateService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_PROCESS_COMMAND.getFullMethodName)

    val entityId: String = request.entityId

    // fetch the gRPC metadata
    val metadata: Metadata = MetadataInterceptor.REQUEST_META.get()
    val tracingHeaders = Telemetry.getTracingHeaders(Context.current())

    log.debug(s"Adding tracing headers to command $tracingHeaders")

    // ascertain the entity ID
    ServiceImpl
      .requireEntityId(entityId)
      // run remote command
      .flatMap(_ => {
        val entityRef: EntityRef[SendReceive] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)
        val propagatedHeaders: Seq[Header] = Util.extractHeaders(metadata, writeSideConfig.propagatedHeaders)
        val persistedHeaders: Seq[Header] = Util.extractHeaders(metadata, writeSideConfig.persistedHeaders)
        val remoteCommand: RemoteCommand =
          RemoteCommand(
            entityId = request.entityId,
            command = request.command,
            propagatedHeaders = propagatedHeaders,
            persistedHeaders = persistedHeaders,
            data = Map.empty[String, any.Any])

        // ask entity for response to aggregate command
        entityRef ? ((replyTo: ActorRef[GeneratedMessage]) => {

          val sendCommand: SendCommand =
            SendCommand().withRemoteCommand(remoteCommand).withTracingHeaders(tracingHeaders)

          SendReceive(message = sendCommand, actorRef = replyTo)
        })
      })
      .map((msg: GeneratedMessage) => msg.asInstanceOf[CommandReply])
      .flatMap((value: CommandReply) => Future.fromTry(ServiceImpl.handleCommandReply(value)))
      .map(c => ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * Used to get the current state of that entity
   */
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_GET_STATE.getFullMethodName)

    val entityId: String = request.entityId

    val tracingHeaders = Telemetry.getTracingHeaders(Context.current())

    // ascertain the entity id
    ServiceImpl
      .requireEntityId(entityId)
      .flatMap(_ => {
        val entityRef: EntityRef[SendReceive] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)

        val getCommand = GetStateCommand().withEntityId(entityId)

        // ask entity for response to AggregateCommand
        entityRef ? ((replyTo: ActorRef[GeneratedMessage]) => {

          val sendCommand: SendCommand =
            SendCommand().withGetStateCommand(getCommand).withTracingHeaders(tracingHeaders)

          SendReceive(message = sendCommand, actorRef = replyTo)
        })
      })
      .map((msg: GeneratedMessage) => msg.asInstanceOf[CommandReply])
      .flatMap((value: CommandReply) => Future.fromTry(ServiceImpl.handleCommandReply(value)))
      .map(c => GetStateResponse().withState(c.getState).withMeta(c.getMeta))
  }
}

object ServiceImpl {

  /**
   * checks whether an entity ID is empty or not
   *
   * @param entityId the entity id
   * @return future for the validation
   */
  private[chiefofstate] def requireEntityId(entityId: String): Future[Unit] = {
    if (entityId.isEmpty) {
      Future.failed(new StatusException(Status.INVALID_ARGUMENT.withDescription("empty entity ID")))
    } else {
      Future.successful {}
    }
  }

  /**
   * handles the command reply, specifically for errors, and
   * reports errors to the global tracer
   *
   * @param commandReply a command reply
   * @return a state wrapper
   */
  private[chiefofstate] def handleCommandReply(commandReply: CommandReply): Try[StateWrapper] = {
    commandReply.reply match {
      case Reply.State(value: StateWrapper) => Success(value)

      case Reply.Error(status: com.google.rpc.status.Status) =>
        val javaStatus: com.google.rpc.Status = toJavaProto(status)

        Failure(StatusProto.toStatusException(javaStatus))

      case default =>
        Failure(
          new StatusException(Status.INTERNAL.withDescription(s"unknown CommandReply ${default.getClass.getName}")))
    }
  }
}
