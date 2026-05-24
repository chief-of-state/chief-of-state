/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.helper.BaseActorSpec

import scala.concurrent.ExecutionContext

/**
 * Smoke tests for ReadSideManager. Without a running ClusterSharding harness we can't
 * test the happy paths of projection control end-to-end, but we can exercise the
 * shard-number validation guards and confirm that the manager forwards calls without
 * crashing on construction.
 */
class ReadSideManagerSpec extends BaseActorSpec("""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "tmp/snapshot-rsm"
    """) {

  private val numShards                     = 4
  private def newManager: ReadSideManager   = new ReadSideManager(testKit.system, numShards)
  private implicit val ec: ExecutionContext = testKit.system.executionContext

  "ReadSideManager.offset" should {
    "reject a negative shardNumber" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.offset("rs", -1))
    }
    "reject a shardNumber >= numShards" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.offset("rs", numShards))
    }
  }

  "ReadSideManager.restart" should {
    "reject a negative shardNumber" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.restart("rs", -1))
    }
    "reject a shardNumber >= numShards" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.restart("rs", numShards))
    }
  }

  "ReadSideManager.pause" should {
    "reject a negative shardNumber" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.pause("rs", -1))
    }
    "reject a shardNumber >= numShards" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.pause("rs", numShards))
    }
  }

  "ReadSideManager.resume" should {
    "reject a negative shardNumber" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.resume("rs", -1))
    }
    "reject a shardNumber >= numShards" in {
      an[IllegalArgumentException] shouldBe thrownBy(newManager.resume("rs", numShards))
    }
  }

  "ReadSideManager.skipOffset (single shard)" should {
    "not throw synchronously even without a registered projection" in {
      noException shouldBe thrownBy(newManager.skipOffset("rs", 0))
    }
  }

  "ReadSideManager.offsets (all shards)" should {
    "return a Future without throwing synchronously" in {
      noException shouldBe thrownBy(newManager.offsets("missing"))
    }
  }

  "ReadSideManager.restartForAll" should {
    "complete (success or failure) without throwing synchronously" in {
      val mgr = newManager
      // The forAll variants iterate all shards; without a real projection the
      // underlying ProjectionManagement call will fail, which we observe via the
      // recover path. The contract here is "the call returns a Future".
      noException shouldBe thrownBy(mgr.restartForAll("rs"))
    }
  }

  "ReadSideManager.pauseForAll" should {
    "complete (success or failure) without throwing synchronously" in {
      noException shouldBe thrownBy(newManager.pauseForAll("rs"))
    }
  }

  "ReadSideManager.resumeForAll" should {
    "complete (success or failure) without throwing synchronously" in {
      noException shouldBe thrownBy(newManager.resumeForAll("rs"))
    }
  }

  "ReadSideManager.skipOffsets (all shards)" should {
    "not throw synchronously" in {
      noException shouldBe thrownBy(newManager.skipOffsets("rs"))
    }
  }

  "ReadSideManager.isReadSidePaused" should {
    "not throw synchronously" in {
      noException shouldBe thrownBy(newManager.isReadSidePaused("rs"))
    }
  }
}
