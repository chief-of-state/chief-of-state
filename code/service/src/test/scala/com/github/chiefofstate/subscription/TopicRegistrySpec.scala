/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.ActorRef

class TopicRegistrySpec extends BaseActorSpec(s"""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  "TopicRegistry" should {
    "return a topic ref for GetOrCreateTopic" in {
      val probe = testKit.createTestProbe[ActorRef[Topic.Command[EventWrapper]]]()
      val reg   = testKit.spawn(TopicRegistry(), "TopicRegistry1")
      reg ! TopicRegistry.GetOrCreateTopic("topic-a", probe.ref)
      val ref = probe.receiveMessage()
      ref should not be null
    }

    "return the same ref for the same topic name" in {
      val probe1 = testKit.createTestProbe[ActorRef[Topic.Command[EventWrapper]]]()
      val probe2 = testKit.createTestProbe[ActorRef[Topic.Command[EventWrapper]]]()
      val reg    = testKit.spawn(TopicRegistry(), "TopicRegistry2")
      reg ! TopicRegistry.GetOrCreateTopic("topic-same", probe1.ref)
      reg ! TopicRegistry.GetOrCreateTopic("topic-same", probe2.ref)
      val ref1 = probe1.receiveMessage()
      val ref2 = probe2.receiveMessage()
      ref1 should be(ref2)
    }

    "return different refs for different topic names" in {
      val probe1 = testKit.createTestProbe[ActorRef[Topic.Command[EventWrapper]]]()
      val probe2 = testKit.createTestProbe[ActorRef[Topic.Command[EventWrapper]]]()
      val reg    = testKit.spawn(TopicRegistry(), "TopicRegistry3")
      reg ! TopicRegistry.GetOrCreateTopic("topic-x", probe1.ref)
      reg ! TopicRegistry.GetOrCreateTopic("topic-y", probe2.ref)
      val ref1 = probe1.receiveMessage()
      val ref2 = probe2.receiveMessage()
      ref1 should not be ref2
    }
  }
}
