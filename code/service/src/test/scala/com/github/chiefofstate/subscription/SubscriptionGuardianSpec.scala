/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.subscription

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.service.UnsubscribeResponse

class SubscriptionGuardianSpec extends BaseActorSpec(s"""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  "SubscriptionGuardian" should {
    "reply to Unsubscribe for unknown subscription_id" in {
      val topicRegistry = testKit.spawn(TopicRegistry(), "TopicRegistry-SubGuard1")
      val guardian   = testKit.spawn(SubscriptionGuardian(topicRegistry), "SubscriptionGuardian1")
      val replyProbe = testKit.createTestProbe[UnsubscribeResponse]()
      guardian ! SubscriptionGuardian.Unsubscribe("unknown-id", replyProbe.ref)
      val response = replyProbe.receiveMessage()
      response.subscriptionId should be("unknown-id")
    }
  }
}
