/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeResponse
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import java.util.UUID
import scala.collection.mutable

/**
 * Guardian actor that spawns subscriber actors for gRPC subscription requests and
 * tracks subscriptions by [[subscriptionId]] for [[Unsubscribe]] / [[Cancel]].
 *
 * == Flow for each subscription ==
 * 1. [[CosService]] sends [[SpawnStreamSetup]](subscriptionId, topicName, onEvent).
 * 2. Guardian spawns a [[streamSubscriberBehavior]] child actor, records it in the
 *    subscriptions map, and asks [[TopicRegistry]] to subscribe it to the topic.
 * 3. The subscriber forwards each [[EventWrapper]] via `onEvent`.
 * 4. [[Unsubscribe]] or [[Cancel]] removes the entry and stops the child subscriber;
 *    `TopicRegistry` notices the subscriber's termination and evicts the topic if
 *    it has no more subscribers.
 *
 * == Lifecycle ==
 * - Spawned once per node when subscription is enabled.
 * - Subscriber actors are children of the guardian, so they survive long enough to
 *   receive events and can be stopped via `context.stop`.
 */
object SubscriptionGuardian {

  /** Request to set up a new subscription stream for the given topic and subscription id. */
  sealed trait Command
  final case class SpawnStreamSetup(
      subscriptionId: String,
      topicName: String,
      onEvent: EventWrapper => Unit
  ) extends Command

  /** Request to stop the subscription identified by subscription_id; reply is sent to replyTo. */
  final case class Unsubscribe(
      subscriptionId: String,
      replyTo: ActorRef[UnsubscribeResponse]
  ) extends Command

  /** Fire-and-forget stop, used when the gRPC stream is cancelled by the client. */
  final case class Cancel(subscriptionId: String) extends Command

  def apply(topicRegistryRef: ActorRef[TopicRegistry.Command]): Behavior[Command] =
    Behaviors.setup { context =>
      val subscriptions: mutable.Map[String, ActorRef[EventWrapper]] = mutable.Map.empty
      Behaviors.receiveMessage {
        case SpawnStreamSetup(subscriptionId, topicName, onEvent) =>
          // Subscriber must be a child of the guardian so that context.stop on
          // Unsubscribe/Cancel works and so its lifetime isn't tied to a short-lived
          // setup actor.
          val subscriber = context.spawn(
            streamSubscriberBehavior(onEvent),
            "Subscriber-" + UUID.randomUUID().toString.take(8)
          )
          subscriptions(subscriptionId) = subscriber
          topicRegistryRef ! TopicRegistry.Subscribe(topicName, subscriber)
          Behaviors.same

        case Unsubscribe(subscriptionId, replyTo) =>
          subscriptions.remove(subscriptionId).foreach(context.stop)
          replyTo ! UnsubscribeResponse(subscriptionId = subscriptionId)
          Behaviors.same

        case Cancel(subscriptionId) =>
          subscriptions.remove(subscriptionId).foreach(context.stop)
          Behaviors.same
      }
    }

  /**
   * Subscriber actor: receives [[EventWrapper]] from the Topic and invokes
   * `onEvent(event)` (which pushes to the gRPC StreamObserver). Stays alive
   * until stopped by [[Unsubscribe]] or the client disconnects.
   */
  private def streamSubscriberBehavior(onEvent: EventWrapper => Unit): Behavior[EventWrapper] =
    Behaviors.receive { (_, event) =>
      onEvent(event)
      Behaviors.same
    }
}
