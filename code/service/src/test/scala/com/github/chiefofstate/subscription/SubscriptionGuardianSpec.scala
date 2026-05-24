/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeResponse
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.actor.typed.pubsub.Topic

import scala.concurrent.duration.*

class SubscriptionGuardianSpec extends BaseActorSpec(s"""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  private def mkEvent(payload: String): EventWrapper =
    EventWrapper()
      .withMeta(MetaData().withEntityId("entity-1"))
      .withEvent(any.Any.pack(StringValue(payload)))
      .withResultingState(any.Any.pack(StringValue(payload)))

  "SubscriptionGuardian" should {
    "reply to Unsubscribe for an unknown subscription_id" in {
      val topicRegistry = testKit.spawn(TopicRegistry(), "TopicRegistry-SubGuard1")
      val guardian   = testKit.spawn(SubscriptionGuardian(topicRegistry), "SubscriptionGuardian1")
      val replyProbe = testKit.createTestProbe[UnsubscribeResponse]()
      guardian ! SubscriptionGuardian.Unsubscribe("unknown-id", replyProbe.ref)
      replyProbe.receiveMessage().subscriptionId shouldBe "unknown-id"
    }

    "spawn a stream subscriber and forward events delivered to its topic" in {
      val topicRegistry = testKit.spawn(TopicRegistry(), "TopicRegistry-SubGuard2")
      val guardian   = testKit.spawn(SubscriptionGuardian(topicRegistry), "SubscriptionGuardian2")
      val eventProbe = testKit.createTestProbe[EventWrapper]()
      val publisher  = testKit.spawn(Topic[EventWrapper]("guardian-topic-a"), "GuardianPublisher2")

      guardian ! SubscriptionGuardian.SpawnStreamSetup(
        "sub-1",
        "guardian-topic-a",
        ev => eventProbe.ref ! ev
      )
      // give Topic + Subscribe a moment to wire up
      eventProbe.expectNoMessage(500.millis)

      val ev = mkEvent("hello")
      publisher ! Topic.Publish(ev)
      eventProbe.expectMessage(ev)
    }

    "stop the subscriber on Unsubscribe and confirm via reply" in {
      val topicRegistry = testKit.spawn(TopicRegistry(), "TopicRegistry-SubGuard3")
      val guardian   = testKit.spawn(SubscriptionGuardian(topicRegistry), "SubscriptionGuardian3")
      val eventProbe = testKit.createTestProbe[EventWrapper]()
      val replyProbe = testKit.createTestProbe[UnsubscribeResponse]()
      val publisher  = testKit.spawn(Topic[EventWrapper]("guardian-topic-b"), "GuardianPublisher3")

      guardian ! SubscriptionGuardian.SpawnStreamSetup(
        "sub-2",
        "guardian-topic-b",
        ev => eventProbe.ref ! ev
      )
      eventProbe.expectNoMessage(500.millis)

      guardian ! SubscriptionGuardian.Unsubscribe("sub-2", replyProbe.ref)
      replyProbe.receiveMessage().subscriptionId shouldBe "sub-2"

      // after Unsubscribe, a new publish should not be delivered
      publisher ! Topic.Publish(mkEvent("after-unsub"))
      eventProbe.expectNoMessage(500.millis)
    }

    "stop the subscriber on Cancel without sending a reply" in {
      val topicRegistry = testKit.spawn(TopicRegistry(), "TopicRegistry-SubGuard4")
      val guardian   = testKit.spawn(SubscriptionGuardian(topicRegistry), "SubscriptionGuardian4")
      val eventProbe = testKit.createTestProbe[EventWrapper]()
      val publisher  = testKit.spawn(Topic[EventWrapper]("guardian-topic-c"), "GuardianPublisher4")

      guardian ! SubscriptionGuardian.SpawnStreamSetup(
        "sub-3",
        "guardian-topic-c",
        ev => eventProbe.ref ! ev
      )
      eventProbe.expectNoMessage(500.millis)

      guardian ! SubscriptionGuardian.Cancel("sub-3")
      // give the guardian a moment to process Cancel
      eventProbe.expectNoMessage(500.millis)

      publisher ! Topic.Publish(mkEvent("after-cancel"))
      eventProbe.expectNoMessage(500.millis)
    }
  }
}
