/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.types

/**
 * ADT for read side failure policies with type safety
 * This replaces string-based pattern matching with compile-time safe types
 */
sealed trait FailurePolicy

object FailurePolicy {
  case object Stop                                               extends FailurePolicy
  case object Skip                                               extends FailurePolicy
  final case class RetryAndStop(retries: Int, delaySeconds: Int) extends FailurePolicy
  final case class RetryAndSkip(retries: Int, delaySeconds: Int) extends FailurePolicy

  /**
   * Parse from string (for config compatibility)
   */
  def fromString(value: String): Either[String, FailurePolicy] = value.toUpperCase match {
    case "STOP"        => Right(Stop)
    case "SKIP"        => Right(Skip)
    case "REPLAY_STOP" => Right(RetryAndStop(5, 5))
    case "REPLAY-STOP" => Right(RetryAndStop(5, 5))
    case "REPLAY_SKIP" => Right(RetryAndSkip(5, 5))
    case "REPLAY-SKIP" => Right(RetryAndSkip(5, 5))
    case _             => Left(s"Unknown failure policy: $value")
  }

  /**
   * Parse from string or use default
   */
  def fromStringOrDefault(value: String): FailurePolicy =
    fromString(value).getOrElse(Stop)
}
