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
import com.github.chiefofstate.protobuf.v1.internal.*
import com.github.chiefofstate.protobuf.v1.internal.CommandReply.Reply
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.service.ChiefOfStateServiceGrpc
import com.github.chiefofstate.protobuf.v1.service.GetStateRequest
import com.github.chiefofstate.protobuf.v1.service.GetStateResponse
import com.github.chiefofstate.protobuf.v1.service.ProcessCommandRequest
import com.github.chiefofstate.protobuf.v1.service.ProcessCommandResponse
import com.github.chiefofstate.protobuf.v1.service.SubscribeAllRequest
import com.github.chiefofstate.protobuf.v1.service.SubscribeAllResponse
import com.github.chiefofstate.protobuf.v1.service.SubscribeRequest
import com.github.chiefofstate.protobuf.v1.service.SubscribeResponse
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeAllRequest
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeAllResponse
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeRequest
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeResponse
import com.github.chiefofstate.serialization.SendReceive
import com.github.chiefofstate.subscription.EventPublisher
import com.github.chiefofstate.subscription.SubscriptionGuardian
import com.github.chiefofstate.subscription.TopicRegistry
import com.github.chiefofstate.utils.Util
import com.google.protobuf.any
import com.google.rpc.status.Status.toJavaProto
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.protobuf.StatusProto
import io.grpc.stub.StreamObserver
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityRef
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class CosService(
    clusterSharding: ClusterSharding,
    writeSideConfig: WriteSideConfig
)(
    subscriptionGuardianRef: Option[ActorRef[SubscriptionGuardian.Command]] = None,
    topicRegistryRef: Option[ActorRef[TopicRegistry.Command]] = None
)(implicit
    val askTimeout: Timeout,
    ec: ExecutionContext,
    scheduler: Scheduler
) extends ChiefOfStateServiceGrpc.ChiefOfStateService
    with CosServiceApi {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  @WithSpan(value = "CosService.processCommand")
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    val entityId: String = request.entityId
    log.debug(
      s"Processing command for entity=$entityId, commandType=${request.command.map(_.typeUrl).getOrElse("unknown")}"
    )

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
      .map { c =>
        log.debug(
          s"Command processed successfully for entity=$entityId, revision=${c.getMeta.revisionNumber}"
        )
        ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta)
      }
      .recoverWith { case ex =>
        log.error(s"Command processing failed for entity=$entityId: ${ex.getMessage}", ex)
        Future.failed(ex)
      }
  }

  /**
   * Used to get the current state of that entity
   */
  @WithSpan(value = "CosService.getState")
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    val entityId: String = request.entityId
    log.debug(s"Getting state for entity=$entityId")

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

  /**
   * Used to register a subscription to live stream of events for a given entity
   */
  @WithSpan(value = "CosService.subscribe")
  override def subscribe(
      request: SubscribeRequest,
      responseObserver: StreamObserver[SubscribeResponse]
  ): Unit =
    (subscriptionGuardianRef, topicRegistryRef) match {
      case (Some(guardianRef), Some(_)) =>
        val entityId = request.entityId
        if (entityId.isEmpty) {
          responseObserver.onError(
            Status.INVALID_ARGUMENT.withDescription("entity_id is required").asException()
          )
        } else {
          val subscriptionId = subscriptionIdOrNew(request.subscriptionId)
          spawnStreamHandler(
            subscriptionId,
            EventPublisher.EntityEventsTopicPrefix + entityId,
            responseObserver,
            toSubscribeResponse(subscriptionId),
            guardianRef
          )
        }
      case _ =>
        responseObserver.onError(
          Status.UNIMPLEMENTED
            .withDescription("Subscription is not enabled; configure subscription to use streaming")
            .asException()
        )
    }

  /**
   * Used to subscribe to live streams of events across all entities
   */
  @WithSpan(value = "CosService.subscribeAll")
  override def subscribeAll(
      request: SubscribeAllRequest,
      responseObserver: StreamObserver[SubscribeAllResponse]
  ): Unit =
    subscriptionGuardianRef match {
      case Some(guardianRef) =>
        val subscriptionId = subscriptionIdOrNew(request.subscriptionId)
        spawnStreamHandler(
          subscriptionId,
          EventPublisher.AllEventsTopicName,
          responseObserver,
          toSubscribeAllResponse(subscriptionId),
          guardianRef
        )
      case None =>
        responseObserver.onError(
          Status.UNIMPLEMENTED
            .withDescription("Subscription is not enabled; configure subscription to use streaming")
            .asException()
        )
    }

  /**
   * Used to unsubscribe from live streams of events for a given entity
   */
  @WithSpan(value = "CosService.unsubscribe")
  override def unsubscribe(request: UnsubscribeRequest): Future[UnsubscribeResponse] =
    subscriptionGuardianRef match {
      case Some(guardianRef) =>
        if (request.subscriptionId.isEmpty) {
          Future.failed(
            new StatusException(
              Status.INVALID_ARGUMENT.withDescription("subscription_id is required")
            )
          )
        } else {
          guardianRef.ask[UnsubscribeResponse](replyTo =>
            SubscriptionGuardian.Unsubscribe(request.subscriptionId, replyTo)
          )(askTimeout, scheduler)
        }
      case None =>
        Future.failed(
          new StatusException(
            Status.UNIMPLEMENTED
              .withDescription(
                "Subscription is not enabled; configure subscription to use streaming"
              )
          )
        )
    }

  /**
   * Used to unsubscribe from live streams of events across all entities
   */
  @WithSpan(value = "CosService.unsubscribeAll")
  override def unsubscribeAll(request: UnsubscribeAllRequest): Future[UnsubscribeAllResponse] =
    subscriptionGuardianRef match {
      case Some(guardianRef) =>
        if (request.subscriptionId.isEmpty) {
          Future.failed(
            new StatusException(
              Status.INVALID_ARGUMENT.withDescription("subscription_id is required")
            )
          )
        } else {
          guardianRef
            .ask(replyTo => SubscriptionGuardian.Unsubscribe(request.subscriptionId, replyTo))(
              askTimeout,
              scheduler
            )
            .map(_ => UnsubscribeAllResponse(subscriptionId = request.subscriptionId))(ec)
        }
      case None =>
        Future.failed(
          new StatusException(
            Status.UNIMPLEMENTED
              .withDescription(
                "Subscription is not enabled; configure subscription to use streaming"
              )
          )
        )
    }

  private def subscriptionIdOrNew(fromRequest: String): String =
    if (fromRequest.nonEmpty) fromRequest
    else java.util.UUID.randomUUID().toString

  private def toSubscribeResponse(subscriptionId: String)(
      event: EventWrapper
  ): SubscribeResponse =
    SubscribeResponse(
      subscriptionId = subscriptionId,
      event = event.event,
      resultingState = event.resultingState,
      meta = event.meta
    )

  private def toSubscribeAllResponse(subscriptionId: String)(
      event: EventWrapper
  ): SubscribeAllResponse =
    SubscribeAllResponse(
      subscriptionId = subscriptionId,
      event = event.event,
      resultingState = event.resultingState,
      meta = event.meta
    )

  private def spawnStreamHandler[T](
      subscriptionId: String,
      topicName: String,
      responseObserver: StreamObserver[T],
      toResponse: EventWrapper => T,
      guardianRef: ActorRef[SubscriptionGuardian.Command]
  ): Unit = {
    val onEvent: EventWrapper => Unit = { event =>
      try {
        responseObserver.onNext(toResponse(event))
      } catch {
        case t: Throwable =>
          log.warn("Failed to send event to stream, client may have disconnected", t)
          try responseObserver.onError(t)
          catch { case _: Throwable => }
      }
    }
    guardianRef ! SubscriptionGuardian.SpawnStreamSetup(subscriptionId, topicName, onEvent)
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
