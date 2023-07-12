/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

/**
 * defines the read side failure policy
 */
object ReadSideFailurePolicy {
  // this will completely stop the given readside when the processing of an event failed
  val StopDirective = "STOP"
  // this will skip the failed processed event and advanced the offset to continue to the next event.
  val SkipDirective = "SKIP"
  // this will attempt to replay the failed processed event five times and stop the given readside
  val ReplayStopDirective = "REPLAY_STOP"
  // this will attempt to replay the failed processed event five times and skip to the next event
  val ReplaySkipDirective = "REPLAY_SKIP"
}
