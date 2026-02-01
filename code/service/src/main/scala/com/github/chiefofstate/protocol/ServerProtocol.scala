/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.protocol

/**
 * ServerProtocol defines which protocols ChiefOfState should expose
 */
sealed trait ServerProtocol
object ServerProtocol {
  case object Grpc extends ServerProtocol
  case object Http extends ServerProtocol
  case object Both extends ServerProtocol

  def fromString(value: String): ServerProtocol = {
    value.toLowerCase match {
      case "grpc" => Grpc
      case "http" => Http
      case "both" => Both
      case _ =>
        throw new IllegalArgumentException(
          s"Invalid server protocol: $value. Must be 'grpc', 'http', or 'both'"
        )
    }
  }
}
