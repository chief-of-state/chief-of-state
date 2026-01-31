/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.Config

import scala.concurrent.duration._

/**
 * Circuit breaker configuration
 *
 * @param enabled whether circuit breaker is enabled
 * @param maxFailures maximum number of failures before opening the circuit
 * @param callTimeout timeout for calls
 * @param resetTimeout timeout before attempting to close the circuit
 */
final case class CircuitBreakerConfig(
    enabled: Boolean,
    maxFailures: Int,
    callTimeout: FiniteDuration,
    resetTimeout: FiniteDuration
)

object CircuitBreakerConfig {
  private val enabledKey      = "enabled"
  private val maxFailuresKey  = "max-failures"
  private val callTimeoutKey  = "call-timeout"
  private val resetTimeoutKey = "reset-timeout"

  /**
   * Creates a CircuitBreakerConfig from a Config object
   *
   * @param config the config object
   * @return CircuitBreakerConfig instance
   * @throws IllegalArgumentException if configuration is invalid
   */
  def apply(config: Config): CircuitBreakerConfig = {
    val enabled        = config.getBoolean(enabledKey)
    val maxFailures    = config.getInt(maxFailuresKey)
    val callTimeoutMs  = config.getDuration(callTimeoutKey).toMillis
    val resetTimeoutMs = config.getDuration(resetTimeoutKey).toMillis

    // Validate only if enabled
    if (enabled) {
      require(maxFailures > 0, s"$maxFailuresKey must be positive, got $maxFailures")
      require(
        maxFailures <= 100,
        s"$maxFailuresKey is too large (max 100), got $maxFailures"
      )
      require(callTimeoutMs > 0, s"$callTimeoutKey must be positive, got ${callTimeoutMs}ms")
      require(
        callTimeoutMs <= 300000,
        s"$callTimeoutKey is too large (max 5 minutes), got ${callTimeoutMs}ms"
      )
      require(resetTimeoutMs > 0, s"$resetTimeoutKey must be positive, got ${resetTimeoutMs}ms")
      require(
        resetTimeoutMs >= 1000,
        s"$resetTimeoutKey should be at least 1 second, got ${resetTimeoutMs}ms"
      )
      require(
        resetTimeoutMs <= 3600000,
        s"$resetTimeoutKey is too large (max 1 hour), got ${resetTimeoutMs}ms"
      )
    }

    CircuitBreakerConfig(
      enabled = enabled,
      maxFailures = maxFailures,
      callTimeout = callTimeoutMs.milliseconds,
      resetTimeout = resetTimeoutMs.milliseconds
    )
  }

  /**
   * Creates a disabled circuit breaker config
   *
   * @return CircuitBreakerConfig with enabled = false
   */
  def disabled(): CircuitBreakerConfig =
    CircuitBreakerConfig(
      enabled = false,
      maxFailures = 5,
      callTimeout = 10.seconds,
      resetTimeout = 1.minute
    )
}
