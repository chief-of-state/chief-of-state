/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.{LinkedHashMap => JLinkedHashMap, Map => JMap}

/**
 * Publishes persisted entity events to Pekko DistributedPubSub (Topic) so that
 * subscription clients receive them in real time without reading from the journal.
 *
 * == Role in the subscription flow ==
 * - Invoked from [[com.github.chiefofstate.Entity]] after an event is successfully persisted
 *   (when subscription is enabled and an `EventPublisher` ref is passed to the entity).
 * - Sends each event to two logical topics:
 *   1. [[AllEventsTopicName]] — consumed by clients that call `SubscribeAll`
 *   2. `[[EntityEventsTopicPrefix]] + entityId` — consumed by clients that call `Subscribe(entityId)`
 *
 * == Design notes ==
 * - Uses Pekko Typed [[Topic]] for cluster-wide pub-sub; no extra DB reads.
 * - One topic ref for "all events" is created at startup; entity-specific topic refs
 *   are created on demand and cached in [[entityTopics]] to avoid unbounded growth
 *   only for entities that have ever published on this node.
 * - Topic names must match those used by [[TopicRegistry]] / [[SubscriptionGuardian]]
 *   when setting up subscribers (same string for the same logical topic).
 *
 * == Lifecycle ==
 * - Spawned once per node when `chiefofstate.subscription.enabled` is true (see
 *   [[com.github.chiefofstate.ServiceStarter]]).
 * - Receives [[Publish]] messages only from entity actors on the same node.
 *
 * == Concurrency ==
 * - Single actor; all state ([[entityTopics]]) is accessed only from this actor.
 */
object EventPublisher {

  /** Topic name for all-entities subscription. Must match usage in [[CosService.subscribeAll]]. */
  val AllEventsTopicName: String = "cos.events.all"

  /** Prefix for entity-specific topic names. Full name is `EntityEventsTopicPrefix + entityId`. */
  val EntityEventsTopicPrefix: String = "cos.events.entity."

  /**
   * Maximum number of entity-specific topic actors cached locally. Entity IDs form an
   * unbounded set in long-running services; LRU eviction keeps memory bounded. Evicted
   * topic actors are stopped; cluster subscribers (which attach to TopicRegistry's
   * topic actor, not this cache) keep receiving events via the same logical topic name.
   */
  val DefaultMaxCachedEntityTopics: Int = 10000

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /** Command to publish an event for a given entity (sent by [[com.github.chiefofstate.Entity]]). */
  sealed trait Command
  final case class Publish(entityId: String, event: EventWrapper) extends Command

  def apply(): Behavior[Command] = apply(DefaultMaxCachedEntityTopics)

  def apply(maxCachedEntityTopics: Int): Behavior[Command] =
    Behaviors.setup { context =>
      val allTopic: ActorRef[Topic.Command[EventWrapper]] =
        context.spawn(Topic[EventWrapper](AllEventsTopicName), "CosEventsAll")
      new EventPublisher(context, allTopic, maxCachedEntityTopics).behavior()
    }
}

/**
 * Internal stateful actor implementation: holds the "all" topic ref and a cache of
 * entity-specific topic refs, and handles [[EventPublisher.Publish]] by forwarding
 * to the appropriate topics.
 */
private final class EventPublisher(
    context: ActorContext[EventPublisher.Command],
    allTopic: ActorRef[Topic.Command[EventWrapper]],
    maxCachedEntityTopics: Int
) {

  import EventPublisher._

  /**
   * Cache of topic name -> Topic ref, bounded with access-order LRU eviction. The
   * eldest entry's topic actor is stopped on eviction. EP-spawned topics never have
   * local subscribers attached, so stopping them is safe; they only forward to
   * DistributedPubSub.
   */
  private val entityTopics =
    new JLinkedHashMap[String, ActorRef[Topic.Command[EventWrapper]]](16, 0.75f, true) {
      override def removeEldestEntry(
          eldest: JMap.Entry[String, ActorRef[Topic.Command[EventWrapper]]]
      ): Boolean = {
        val shouldEvict = size > maxCachedEntityTopics
        if (shouldEvict) context.stop(eldest.getValue)
        shouldEvict
      }
    }

  def behavior(): Behavior[Command] =
    Behaviors.receiveMessage { case Publish(entityId, event) =>
      val entityTopicName = EntityEventsTopicPrefix + entityId
      val cached          = entityTopics.get(entityTopicName)
      val entityTopic =
        if (cached != null) cached
        else {
          val fresh = context.spawnAnonymous(Topic[EventWrapper](entityTopicName))
          entityTopics.put(entityTopicName, fresh)
          fresh
        }
      allTopic ! Topic.Publish(event)
      entityTopic ! Topic.Publish(event)
      Behaviors.same
    }
}
