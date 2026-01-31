/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeResponse
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import java.util.UUID
import scala.collection.mutable

/**
 * Guardian actor that spawns stream-setup actors for gRPC subscription requests
 * and tracks subscriptions by [[subscriptionId]] for [[Unsubscribe]].
 *
 * == Why a guardian ==
 * The gRPC service ([[com.github.chiefofstate.services.CosService]]) runs outside
 * the actor system. To spawn typed actors (for subscribing to Pekko [[Topic]] and
 * forwarding events to [[io.grpc.stub.StreamObserver]]), we need an [[org.apache.pekko.actor.typed.ActorContext]].
 * The guardian holds that context and spawns a short-lived "stream setup" actor per
 * [[SpawnStreamSetup]] request.
 *
 * == Flow for each subscription ==
 * 1. [[CosService]] receives `Subscribe` or `SubscribeAll` and sends
 *    [[SpawnStreamSetup]](subscriptionId, topicName, onEvent) to this guardian.
 * 2. Guardian spawns [[streamSetupBehavior]], which asks [[TopicRegistry]] for the
 *    topic ref, spawns a [[streamSubscriberBehavior]] actor, subscribes it to the topic,
 *    then sends [[RegisterSubscription]](subscriptionId, subscriberRef) back to the guardian.
 * 3. Guardian stores subscriptionId -> subscriberRef. The subscriber stays alive and
 *    forwards each [[EventWrapper]] via `onEvent`.
 * 4. When [[Unsubscribe]](subscriptionId, replyTo) is received, guardian stops the
 *    subscriber, removes it from the map, and replies with [[UnsubscribeResponse]].
 *
 * == Lifecycle ==
 * - Spawned once per node when subscription is enabled (see [[com.github.chiefofstate.ServiceStarter]]).
 * - Receives [[SpawnStreamSetup]] from [[CosService]] on each new gRPC subscription.
 *
 * == Concurrency ==
 * - Single actor; spawning and map updates are serialized. Each stream setup runs in its own child actor.
 */
object SubscriptionGuardian {

  /** Request to set up a new subscription stream for the given topic and subscription id. */
  sealed trait Command
  final case class SpawnStreamSetup(
      subscriptionId: String,
      topicName: String,
      onEvent: EventWrapper => Unit
  ) extends Command

  /** Internal: sent by stream-setup actor to register the subscriber ref for a subscription id. */
  private final case class RegisterSubscription(
      subscriptionId: String,
      subscriberRef: ActorRef[EventWrapper]
  ) extends Command

  /** Request to stop the subscription identified by subscription_id; reply is sent to replyTo. */
  final case class Unsubscribe(
      subscriptionId: String,
      replyTo: ActorRef[UnsubscribeResponse]
  ) extends Command

  def apply(topicRegistryRef: ActorRef[TopicRegistry.Command]): Behavior[Command] =
    Behaviors.setup { context =>
      val subscriptions: mutable.Map[String, ActorRef[EventWrapper]] = mutable.Map.empty
      Behaviors.receiveMessage {
        case SpawnStreamSetup(subscriptionId, topicName, onEvent) =>
          context.spawn(
            streamSetupBehavior(subscriptionId, topicName, topicRegistryRef, onEvent, context.self),
            "StreamSetup-" + UUID.randomUUID().toString.take(8)
          )
          Behaviors.same

        case RegisterSubscription(subscriptionId, subscriberRef) =>
          subscriptions(subscriptionId) = subscriberRef
          Behaviors.same

        case Unsubscribe(subscriptionId, replyTo) =>
          subscriptions.remove(subscriptionId) match {
            case Some(subscriberRef) =>
              context.stop(subscriberRef)
              replyTo ! UnsubscribeResponse(subscriptionId = subscriptionId)
            case None =>
              replyTo ! UnsubscribeResponse(subscriptionId = subscriptionId)
          }
          Behaviors.same
      }
    }

  /**
   * Short-lived actor: asks TopicRegistry for the topic ref, spawns a subscriber,
   * subscribes it to the topic, registers the subscriber with the guardian, then stops.
   */
  private def streamSetupBehavior(
      subscriptionId: String,
      topicName: String,
      topicRegistryRef: ActorRef[TopicRegistry.Command],
      onEvent: EventWrapper => Unit,
      guardianRef: ActorRef[Command]
  ): Behavior[ActorRef[Topic.Command[EventWrapper]]] =
    Behaviors.setup { context =>
      topicRegistryRef ! TopicRegistry.GetOrCreateTopic(topicName, context.self)
      Behaviors.receiveMessage { topicRef =>
        val subscriber = context.spawn(
          streamSubscriberBehavior(onEvent),
          "Subscriber-" + UUID.randomUUID().toString.take(8)
        )
        topicRef ! Topic.Subscribe(subscriber)
        guardianRef ! RegisterSubscription(subscriptionId, subscriber)
        Behaviors.stopped
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
