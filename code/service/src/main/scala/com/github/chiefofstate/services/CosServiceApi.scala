/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.services

import com.github.chiefofstate.protobuf.v1.service.{
  GetStateRequest,
  GetStateResponse,
  ProcessCommandRequest,
  ProcessCommandResponse
}

import scala.concurrent.Future

/**
 * Minimal API for HTTP route testing. CosService implements this via the gRPC service interface.
 */
trait CosServiceApi {
  def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse]
  def getState(request: GetStateRequest): Future[GetStateResponse]
}
