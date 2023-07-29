/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl._
import com.github.chiefofstate.config.{CosConfig, SnapshotConfig}
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.internal.{
  CommandReply,
  GetStateCommand,
  RemoteCommand,
  SendCommand
}
import com.github.chiefofstate.protobuf.v1.persistence.{EventWrapper, StateWrapper}
import com.github.chiefofstate.serialization.SendReceive
import com.github.chiefofstate.utils.ProtosValidator
import com.github.chiefofstate.utils.Util.{Instants, makeFailedStatusPf, toRpcStatus}
import com.github.chiefofstate.writeside.ResponseType._
import com.github.chiefofstate.writeside.{RemoteCommandHandler, RemoteEventHandler}
import com.google.protobuf.any
import com.google.protobuf.empty.Empty
import io.grpc.{Status, StatusException}
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.{Logger, LoggerFactory}

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

/**
 *  This is an event sourced actor.
 */
object AggregateRoot {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * thee aggregate root type key
   */
  val TypeKey: EntityTypeKey[SendReceive] = EntityTypeKey[SendReceive]("chiefOfState")

  /**
   * creates a new instance of the aggregate root
   *
   * @param persistenceId the internal persistence ID used by akka to locate the aggregate based upon the given entity ID.
   * @param shardIndex the shard index of the aggregate
   * @param cosConfig the main config
   * @param commandHandler the remote command handler
   * @param eventHandler the remote events handler handler
   * @return an akka behaviour
   */
  def apply(
      persistenceId: PersistenceId,
      shardIndex: Int,
      cosConfig: CosConfig,
      commandHandler: RemoteCommandHandler,
      eventHandler: RemoteEventHandler,
      protosValidator: ProtosValidator
  ): Behavior[SendReceive] = {
    Behaviors.setup { context =>
      {
        EventSourcedBehavior
          .withEnforcedReplies[SendReceive, EventWrapper, StateWrapper](
            persistenceId,
            emptyState = initialState(persistenceId),
            (state, command) =>
              handleCommand(context, state, command, commandHandler, eventHandler, protosValidator),
            (state, event) => handleEvent(state, event)
          )
          .withTagger(_ => Set(shardIndex.toString))
          .withRetention(setSnapshotRetentionCriteria(cosConfig.snapshotConfig))
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      }
    }
  }

  /**
   * handles the received command by the aggregate root
   *
   * @param context the actor system context
   * @param aggregateState the prior state of the aggregate before the command being handled is received
   * @param aggregateCommand the command to handle
   * @param commandHandler the remote commands handler
   * @param eventHandler the remote events handler
   * @return a side effect
   */
  private[chiefofstate] def handleCommand(
      context: ActorContext[SendReceive],
      aggregateState: StateWrapper,
      aggregateCommand: SendReceive,
      commandHandler: RemoteCommandHandler,
      eventHandler: RemoteEventHandler,
      protosValidator: ProtosValidator
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    log.debug("begin handle command")

    val sendCommand: SendCommand = aggregateCommand.message.asInstanceOf[SendCommand]

    val headers = sendCommand.tracingHeaders
    log.trace(s"aggregate root headers $headers")

    val output: ReplyEffect[EventWrapper, StateWrapper] = sendCommand.message match {
      case SendCommand.Message.RemoteCommand(remoteCommand) =>
        handleRemoteCommand(
          aggregateState,
          remoteCommand,
          aggregateCommand.actorRef,
          commandHandler,
          eventHandler,
          protosValidator,
          remoteCommand.data
        )

      case SendCommand.Message.GetStateCommand(getStateCommand) =>
        handleGetStateCommand(getStateCommand, aggregateState, aggregateCommand.actorRef)

      case SendCommand.Message.Empty =>
        val errStatus = Status.INTERNAL.withDescription("no command sent")
        Effect.reply(aggregateCommand.actorRef)(CommandReply().withError(toRpcStatus(errStatus)))
    }
    output
  }

  /**
   * handles GetStateCommand
   *
   * @param cmd a GetStateCommand
   * @param state an aggregate StateWrapper
   * @param replyTo address to reply to
   * @return a reply effect returning the state or an error
   */
  @WithSpan(value = "AggregateRoot.HandleGetStateCommand")
  def handleGetStateCommand(
      cmd: GetStateCommand,
      state: StateWrapper,
      replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    if (state.meta.map(_.revisionNumber).getOrElse(0) > 0) {
      log.debug(s"found state for entity ${cmd.entityId}")
      Effect.reply(replyTo)(CommandReply().withState(state))
    } else {
      Effect.reply(replyTo)(CommandReply().withError(toRpcStatus(Status.NOT_FOUND)))
    }
  }

  /**
   * handler for remote commands
   *
   * @param priorState the prior state of the entity
   * @param command the command to handle
   * @param replyTo the actor ref to reply to
   * @param commandHandler a command handler
   * @param eventHandler an event handler
   * @param protosValidator a proto validator
   * @param data COS plugin data
   * @return a reply effect
   */
  @WithSpan(value = "AggregateRoot.HandleRemoteCommandsAndEvents")
  def handleRemoteCommand(
      priorState: StateWrapper,
      command: RemoteCommand,
      replyTo: ActorRef[CommandReply],
      commandHandler: RemoteCommandHandler,
      eventHandler: RemoteEventHandler,
      protosValidator: ProtosValidator,
      data: Map[String, com.google.protobuf.any.Any]
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    val handlerOutput: Try[Response] = commandHandler
      .handleCommand(command, priorState)
      .map(_.event match {
        case Some(newEvent) =>
          protosValidator.requireValidEvent(newEvent)
          NewEvent(newEvent)

        case None =>
          NoOp
      })
      .flatMap {
        case NewEvent(newEvent) =>
          val newEventMeta: MetaData = MetaData()
            .withRevisionNumber(priorState.getMeta.revisionNumber + 1)
            .withRevisionDate(Instant.now().toTimestamp)
            .withData(data)
            .withEntityId(priorState.getMeta.entityId)
            .withHeaders(command.persistedHeaders)

          val priorStateAny: com.google.protobuf.any.Any = priorState.getState

          eventHandler
            .handleEvent(newEvent, priorStateAny, newEventMeta)
            .map(response => {
              require(response.resultingState.isDefined, "event handler replied with empty state")
              protosValidator.requireValidState(response.getResultingState)
              NewState(newEvent, response.getResultingState, newEventMeta)
            })

        case unhandled =>
          Success(unhandled)
      }
      .recoverWith(makeFailedStatusPf)

    handlerOutput match {
      case Success(NoOp) =>
        Effect.reply(replyTo)(CommandReply().withState(priorState))

      case Success(NewState(event, newState, eventMeta)) =>
        persistEventAndReply(event, newState, eventMeta, replyTo)

      case Failure(e: StatusException) =>
        // reply with the error status
        Effect.reply(replyTo)(CommandReply().withError(toRpcStatus(e.getStatus, e.getTrailers)))

      case unhandled =>
        // this should never happen, but here for code completeness
        val errStatus =
          Status.INTERNAL.withDescription(s"write handler failure, ${unhandled.getClass}")
        Effect.reply(replyTo)(CommandReply().withError(toRpcStatus(errStatus)))
    }
  }

  /**
   * persists an event and the resulting state and reply to the caller
   *
   * @param event the event to persist
   * @param resultingState the resulting state to persist
   * @param eventMeta the prior meta before the event to be persisted
   * @param replyTo the caller ref receiving the reply when persistence is successful
   * @return a reply effect
   */
  private[chiefofstate] def persistEventAndReply(
      event: any.Any,
      resultingState: any.Any,
      eventMeta: MetaData,
      replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    Effect
      .persist(
        EventWrapper().withEvent(event).withResultingState(resultingState).withMeta(eventMeta)
      )
      .thenReply(replyTo)((updatedState: StateWrapper) => CommandReply().withState(updatedState))
  }

  /**
   * handles the aggregate event persisted by applying the prior state to the
   * event to return a new state
   *
   * @param state the prior state to the event being handled
   * @param event the event to handle
   * @return the resulting state
   */
  private[chiefofstate] def handleEvent(state: StateWrapper, event: EventWrapper): StateWrapper = {
    state.update(_.meta := event.getMeta, _.state := event.getResultingState)
  }

  /**
   * sets the snapshot retention criteria
   *
   * @param snapshotConfig the snapshot config
   * @return the snapshot retention criteria
   */
  private[chiefofstate] def setSnapshotRetentionCriteria(
      snapshotConfig: SnapshotConfig
  ): RetentionCriteria = {
    if (snapshotConfig.disableSnapshot) RetentionCriteria.disabled
    else {
      // journal/snapshot retention criteria
      val rc: SnapshotCountRetentionCriteria = RetentionCriteria.snapshotEvery(
        numberOfEvents = snapshotConfig.retentionFrequency, // snapshotFrequency
        keepNSnapshots = snapshotConfig.retentionNr         // snapshotRetention
      )
      // journal/snapshot retention criteria
      if (snapshotConfig.deleteEventsOnSnapshot) rc.withDeleteEventsOnSnapshot
      rc
    }
  }

  /**
   * creates the initial state of the aggregate
   *
   * @param persistenceId the persistence ID
   * @return the initial state
   */
  private[chiefofstate] def initialState(persistenceId: PersistenceId): StateWrapper = {
    StateWrapper.defaultInstance
      .withMeta(MetaData.defaultInstance.withEntityId(persistenceId.id))
      .withState(any.Any.pack(Empty.defaultInstance))
  }
}
