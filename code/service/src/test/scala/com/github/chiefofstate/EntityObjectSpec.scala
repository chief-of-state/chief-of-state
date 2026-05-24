/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import com.github.chiefofstate.config.SnapshotConfig
import com.github.chiefofstate.helper.BaseSpec
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.RetentionCriteria

/** Pure-function tests for the `Entity` companion object that don't need an ActorSystem. */
class EntityObjectSpec extends BaseSpec {

  "Entity.setSnapshotRetentionCriteria" should {
    "return RetentionCriteria.disabled when snapshotting is disabled" in {
      val cfg = SnapshotConfig(
        disableSnapshot = true,
        retentionFrequency = 100,
        retentionNr = 3,
        deleteEventsOnSnapshot = false
      )
      Entity.setSnapshotRetentionCriteria(cfg) shouldBe RetentionCriteria.disabled
    }

    "return a snapshot-every criteria when delete-events is off" in {
      val cfg = SnapshotConfig(
        disableSnapshot = false,
        retentionFrequency = 100,
        retentionNr = 3,
        deleteEventsOnSnapshot = false
      )
      val res = Entity.setSnapshotRetentionCriteria(cfg)
      res should not be RetentionCriteria.disabled
    }

    "return a snapshot-every criteria with deleteEventsOnSnapshot when enabled" in {
      val cfg = SnapshotConfig(
        disableSnapshot = false,
        retentionFrequency = 50,
        retentionNr = 2,
        deleteEventsOnSnapshot = true
      )
      val res = Entity.setSnapshotRetentionCriteria(cfg)
      res should not be RetentionCriteria.disabled
    }
  }

  "Entity.initialState" should {
    "produce a StateWrapper with the persistence id stamped in meta.entityId" in {
      val state = Entity.initialState(PersistenceId.ofUniqueId("entity-xyz"))
      state.getMeta.entityId shouldBe "entity-xyz"
      state.getState.typeUrl should include("Empty")
    }
  }
}
