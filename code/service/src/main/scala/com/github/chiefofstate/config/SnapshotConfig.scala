/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config

/**
 * Snapshot settings
 *
 * @param disableSnapshot disallow the creation of state snapshot
 * @param retentionFrequency how often a snapshot should be created
 * @param retentionNr the number of snapshot to keep in the store
 * @param deleteEventsOnSnapshot removes all old events before snapshotting
 */
final case class SnapshotConfig(
    disableSnapshot: Boolean,
    retentionFrequency: Int,
    retentionNr: Int,
    deleteEventsOnSnapshot: Boolean
) {}

object SnapshotConfig {
  private val disableSnapshotKey: String    = "chiefofstate.snapshot-criteria.disable-snapshot"
  private val retentionFrequencyKey: String = "chiefofstate.snapshot-criteria.retention-frequency"
  private val retentionNrKey: String        = "chiefofstate.snapshot-criteria.retention-number"
  private val deleteEventsOnSnapshotKey: String =
    "chiefofstate.snapshot-criteria.delete-events-on-snapshot"

  /**
   * creates a new instance of SnapshotConfig with validation
   * @param config the config object
   * @return the new instance of SnapshotConfig
   * @throws IllegalArgumentException if configuration is invalid
   */
  def apply(config: Config): SnapshotConfig = {
    val disableSnapshot        = config.getBoolean(disableSnapshotKey)
    val retentionFrequency     = config.getInt(retentionFrequencyKey)
    val retentionNr            = config.getInt(retentionNrKey)
    val deleteEventsOnSnapshot = config.getBoolean(deleteEventsOnSnapshotKey)

    // Only validate snapshot settings if snapshots are enabled
    if (!disableSnapshot) {
      require(
        retentionFrequency > 0,
        s"$retentionFrequencyKey must be positive when snapshots are enabled, got $retentionFrequency"
      )
      require(
        retentionNr > 0,
        s"$retentionNrKey must be positive when snapshots are enabled, got $retentionNr"
      )
      require(
        retentionNr <= 100,
        s"$retentionNrKey is too large (max 100), got $retentionNr"
      )
    }

    SnapshotConfig(
      disableSnapshot,
      retentionFrequency,
      retentionNr,
      deleteEventsOnSnapshot
    )
  }
}
