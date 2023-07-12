/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.chiefofstate.config.ReadSideFailurePolicy.{
  ReplaySkipDirective,
  ReplayStopDirective,
  SkipDirective,
  StopDirective
}

/**
 * ReadSideConfig defines the configuration of CoS readside
 *
 * @param readSideId the readSide ID
 * @param host       the read side server host to receive read side requests
 * @param port       the read side server port
 * @param useTls     specifies whether SSL is enabled on the read side server
 * @param autoStart  specifies whether the read side should start processing messages or be in pause mode
 * @param enabled specifies whether the read side is enabled or not. This means that the readside will not be added at runtime to the list of
 *                read sides that need to run. This is useful when deactivating a faulty read side
 */
final case class ReadSideConfig(
    @JsonProperty(required = true)
    readSideId: String,
    @JsonProperty(required = true)
    host: String = "",
    @JsonProperty(required = true)
    port: Int = -1,
    @JsonProperty
    useTls: Boolean = false,
    @JsonProperty
    autoStart: Boolean = true,
    @JsonProperty
    enabled: Boolean = true,
    @JsonProperty
    failurePolicy: String = "") {

  // let us set the valid failure policy values
  private val failurePolicies =
    Seq(
      SkipDirective.toLowerCase,
      StopDirective.toLowerCase,
      ReplaySkipDirective.toLowerCase,
      ReplayStopDirective.toLowerCase)

  /**
   * check whether the read side config is valid or not
   *
   * @return true when the read side config is valid and false on the contrary
   */
  def isValid: Boolean = {
    val idPattern = "^[A-Za-z0-9]([A-Za-z0-9_-]*[A-Za-z0-9])?$"
    readSideId.matches(idPattern) && isFailurePolicyValid
  }

  /**
   * checks whether the failure policy set is valid or not.
   *
   * The possible values for failure policy are: SKIP, STOP, REPLAY_SKIP, REPLAY_STOP
   * @return true when the failure policy is valid and false on the contrary
   */
  def isFailurePolicyValid: Boolean = {
    failurePolicy.isEmpty || failurePolicies.contains(failurePolicy.toLowerCase)
  }

  override def toString: String = {
    s"id=$readSideId, host=$host, port=$port, " +
    s"useTls=$useTls, autoStart=$autoStart, " +
    s"enabled=$enabled, failurePolicy=$failurePolicy"
  }
}
