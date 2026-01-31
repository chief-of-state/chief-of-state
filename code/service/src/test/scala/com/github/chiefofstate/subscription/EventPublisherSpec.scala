/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.ActorRef

class EventPublisherSpec extends BaseActorSpec(s"""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  def mkEvent(entityId: String, payload: String): EventWrapper =
    EventWrapper()
      .withMeta(MetaData().withEntityId(entityId))
      .withEvent(any.Any.pack(StringValue(payload)))
      .withResultingState(any.Any.pack(StringValue(payload)))

  "EventPublisher" should {
    "publish to the all-events topic" in {
      val probe = testKit.createTestProbe[EventWrapper]()
      val allTopic: ActorRef[Topic.Command[EventWrapper]] =
        testKit.spawn(Topic[EventWrapper](EventPublisher.AllEventsTopicName), "TestAllTopic")
      allTopic ! Topic.Subscribe(probe.ref)

      val publisher = testKit.spawn(EventPublisher(), "EventPublisher")
      val event     = mkEvent("entity-1", "event1")
      publisher ! EventPublisher.Publish("entity-1", event)

      probe.expectMessage(event)
    }
  }
}
