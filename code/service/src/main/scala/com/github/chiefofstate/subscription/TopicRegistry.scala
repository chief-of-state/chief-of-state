/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

/**
 * Caches Pekko [[Topic]] refs by topic name so that multiple subscribers
 * (or publishers) to the same logical topic share one Topic actor per node.
 *
 * == Role in the subscription flow ==
 * - Used by [[SubscriptionGuardian]] when setting up a new gRPC subscription:
 *   the guardian sends [[GetOrCreateTopic]](topicName, replyTo), and the registry
 *   returns a ref to the Topic for that name (creating it on first use).
 * - Topic names must match those used by [[EventPublisher]] when publishing:
 *   [[EventPublisher.AllEventsTopicName]] and [[EventPublisher.EntityEventsTopicPrefix]] + entityId.
 *
 * == Design notes ==
 * - One Topic actor per (topicName, node); created on first [[GetOrCreateTopic]] for that name.
 * - Cache is in-memory and grows with the number of distinct topic names requested
 *   (e.g. one per entity that has at least one subscriber on this node).
 * - Pekko Topic is cluster-aware: subscribers on this node receive events published
 *   to the same topic name from any node (including from [[EventPublisher]] on other nodes).
 *
 * == Lifecycle ==
 * - Spawned once per node when subscription is enabled (see [[com.github.chiefofstate.ServiceStarter ServiceStarter]]).
 * - Receives [[GetOrCreateTopic]] only from [[SubscriptionGuardian]] stream-setup actors.
 *
 * == Concurrency ==
 * - Single actor; the cache is accessed only from this actor.
 */
object TopicRegistry {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /** Request to get or create a Topic ref for the given name; reply is sent to `replyTo`. */
  sealed trait Command
  final case class GetOrCreateTopic(
      topicName: String,
      replyTo: ActorRef[ActorRef[Topic.Command[EventWrapper]]]
  ) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val cache: mutable.Map[String, ActorRef[Topic.Command[EventWrapper]]] = mutable.Map.empty
      Behaviors.receiveMessage { case GetOrCreateTopic(topicName, replyTo) =>
        val ref = cache.getOrElseUpdate(
          topicName,
          context.spawnAnonymous(Topic[EventWrapper](topicName))
        )
        replyTo ! ref
        Behaviors.same
      }
    }
}
