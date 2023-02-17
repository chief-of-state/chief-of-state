/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

trait Projection {

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def start(): Unit
}
