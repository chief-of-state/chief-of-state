/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.protobuf.v1.common.MetaData

/**
 * ResponseType represents the various transitions a write side handler
 */
object ResponseType {
  sealed trait Response

  /**
   *  an event is returned by the write side handler. That event will be persisted into the journal
   * @param event the event return
   */
  case class NewEvent(event: com.google.protobuf.any.Any) extends Response

  /**
   * a new state is return sequel to an event handler call
   * @param event the event
   * @param state the state
   * @param eventMeta the event meta
   */
  case class NewState(
      event: com.google.protobuf.any.Any,
      state: com.google.protobuf.any.Any,
      eventMeta: MetaData
  ) extends Response

  /**
   * this will return the current state of the AggregateRoot. No event will be persisted
   */
  case object NoOp extends Response

}
