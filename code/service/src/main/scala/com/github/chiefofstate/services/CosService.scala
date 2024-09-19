/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.services

import com.github.chiefofstate.Entity
import com.github.chiefofstate.config.WriteSideConfig
import com.github.chiefofstate.interceptors.MetadataInterceptor
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
import io.grpc.{Metadata, Status, StatusException}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import org.apache.pekko.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class CosService(clusterSharding: ClusterSharding, writeSideConfig: WriteSideConfig)(implicit
    val askTimeout: Timeout
) extends ChiefOfStateServiceGrpc.ChiefOfStateService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_PROCESS_COMMAND.getFullMethodName)

    val entityId: String = request.entityId

    // fetch the gRPC metadata
    val metadata: Metadata = MetadataInterceptor.REQUEST_META.get()

    // ascertain the entity ID
    CosService
      .requireEntityId(entityId)
      // run remote command
      .flatMap(_ => {
        val entityRef: EntityRef[SendReceive] =
          clusterSharding.entityRefFor(Entity.TypeKey, entityId)
        val propagatedHeaders: Seq[Header] =
          Util.extractHeaders(metadata, writeSideConfig.propagatedHeaders)
        val persistedHeaders: Seq[Header] =
          Util.extractHeaders(metadata, writeSideConfig.persistedHeaders)
        val remoteCommand: RemoteCommand =
          RemoteCommand(
            entityId = request.entityId,
            command = request.command,
            propagatedHeaders = propagatedHeaders,
            persistedHeaders = persistedHeaders,
            data = Map.empty[String, any.Any]
          )

        // ask entity for response to aggregate command
        entityRef ? ((replyTo: ActorRef[GeneratedMessage]) => {

          val sendCommand: SendCommand =
            SendCommand().withRemoteCommand(remoteCommand)

          SendReceive(message = sendCommand, actorRef = replyTo)
        })
      })
      .map((msg: GeneratedMessage) => msg.asInstanceOf[CommandReply])
      .flatMap((value: CommandReply) => Future.fromTry(CosService.handleCommandReply(value)))
      .map(c => ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * Used to get the current state of that entity
   */
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_GET_STATE.getFullMethodName)

    val entityId: String = request.entityId

    // ascertain the entity id
    CosService
      .requireEntityId(entityId)
      .flatMap(_ => {
        val entityRef: EntityRef[SendReceive] =
          clusterSharding.entityRefFor(Entity.TypeKey, entityId)

        val getCommand = GetStateCommand().withEntityId(entityId)

        // ask entity for response to AggregateCommand
        entityRef ? ((replyTo: ActorRef[GeneratedMessage]) => {

          val sendCommand: SendCommand =
            SendCommand().withGetStateCommand(getCommand)

          SendReceive(message = sendCommand, actorRef = replyTo)
        })
      })
      .map((msg: GeneratedMessage) => msg.asInstanceOf[CommandReply])
      .flatMap((value: CommandReply) => Future.fromTry(CosService.handleCommandReply(value)))
      .map(c => GetStateResponse().withState(c.getState).withMeta(c.getMeta))
  }
}

object CosService {

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
          new StatusException(
            Status.INTERNAL.withDescription(s"unknown CommandReply ${default.getClass.getName}")
          )
        )
    }
  }
}
