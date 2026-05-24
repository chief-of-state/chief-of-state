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

import scala.concurrent.duration._

class TopicRegistrySpec extends BaseActorSpec(s"""
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

  "TopicRegistry" should {
    "deliver events to a subscriber registered for a topic" in {
      val reg       = testKit.spawn(TopicRegistry(), "TopicRegistry1")
      val probe     = testKit.createTestProbe[EventWrapper]()
      val publisher = testKit.spawn(Topic[EventWrapper]("topic-a"), "Publisher1")

      reg ! TopicRegistry.Subscribe("topic-a", probe.ref)
      // give Pekko Topic a moment to register the subscriber
      probe.expectNoMessage(100.millis)

      val event = mkEvent("e1")
      publisher ! Topic.Publish(event)
      probe.expectMessage(event)
    }
  }
}
