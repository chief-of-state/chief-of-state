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
 * Caches Pekko [[Topic]] refs by topic name and subscribes subscribers to them.
 *
 * == Role in the subscription flow ==
 * - Used by [[SubscriptionGuardian]] when setting up a new gRPC subscription:
 *   the guardian sends [[Subscribe]](topicName, subscriber). The registry creates
 *   the topic on first use, subscribes the given actor, watches it, and stops the
 *   topic actor when its last subscriber terminates.
 * - Topic names must match those used by [[EventPublisher]] when publishing:
 *   [[EventPublisher.AllEventsTopicName]] and [[EventPublisher.EntityEventsTopicPrefix]] + entityId.
 *
 * == Memory bound ==
 * The cache holds at most one entry per topic with at least one live subscriber on this
 * node. When the last subscriber dies, the topic actor is stopped and the entry removed,
 * preventing unbounded growth for high-cardinality entity-specific topics.
 */
object TopicRegistry {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /** Request to subscribe `subscriber` to the topic named `topicName`. */
  sealed trait Command
  final case class Subscribe(topicName: String, subscriber: ActorRef[EventWrapper]) extends Command

  private final case class SubscriberTerminated(subscriber: ActorRef[EventWrapper]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val topicByName =
        mutable.Map.empty[String, ActorRef[Topic.Command[EventWrapper]]]
      val subscribersByTopic =
        mutable.Map.empty[String, mutable.Set[ActorRef[EventWrapper]]]
      val topicBySubscriber =
        mutable.Map.empty[ActorRef[EventWrapper], String]

      Behaviors.receiveMessage[Command] {
        case Subscribe(topicName, subscriber) =>
          val topicRef = topicByName.getOrElseUpdate(
            topicName,
            context.spawnAnonymous(Topic[EventWrapper](topicName))
          )
          topicRef ! Topic.Subscribe(subscriber)
          subscribersByTopic.getOrElseUpdate(topicName, mutable.Set.empty) += subscriber
          topicBySubscriber(subscriber) = topicName
          context.watchWith(subscriber, SubscriberTerminated(subscriber))
          Behaviors.same

        case SubscriberTerminated(subscriber) =>
          topicBySubscriber.remove(subscriber).foreach { topicName =>
            subscribersByTopic.get(topicName).foreach { remaining =>
              remaining -= subscriber
              if (remaining.isEmpty) {
                subscribersByTopic.remove(topicName)
                topicByName.remove(topicName).foreach { ref =>
                  context.stop(ref)
                  log.debug("evicted topic '{}' (no subscribers)", topicName)
                }
              }
            }
          }
          Behaviors.same
      }
    }
}
