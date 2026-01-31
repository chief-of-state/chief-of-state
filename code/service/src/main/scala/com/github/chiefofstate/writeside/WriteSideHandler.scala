/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.internal.RemoteCommand
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.writeside.{HandleCommandResponse, HandleEventResponse}
import com.google.protobuf.any

import scala.util.Try

/**
 * Trait for command handling - implemented by both gRPC and HTTP handlers
 */
trait WriteSideCommandHandler {

  /**
   * handles the given command and return an eventual response
   *
   * @param remoteCommand the command to handle
   * @param priorState the aggregate state before the command to handle
   * @return an eventual HandleCommandResponse
   */
  def handleCommand(
      remoteCommand: RemoteCommand,
      priorState: StateWrapper
  ): Try[HandleCommandResponse]
}

/**
 * Trait for event handling - implemented by both gRPC and HTTP handlers
 */
trait WriteSideEventHandler {

  /**
   * handles the given event and return an eventual response
   *
   * @param event the event to handle
   * @param priorState the aggregate prior state
   * @param eventMeta the event metadata
   * @return the eventual HandleEventResponse
   */
  def handleEvent(
      event: any.Any,
      priorState: any.Any,
      eventMeta: MetaData
  ): Try[HandleEventResponse]
}
