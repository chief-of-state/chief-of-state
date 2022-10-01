/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ReadSideConfig defines the configuration of CoS readside
 *
 * @param readSideId the readSide ID
 * @param host the read side server host to receive read side requests
 * @param port the read side server port
 * @param useTls specifies whether SSL is enabled on the read side server
 * @param autoStart specifies whether the read side should start processing messages or be in pause mode
 */
final case class ReadSideConfig(
    @JsonProperty(required = true)
    readSideId: String,
    @JsonProperty(required = true)
    host: String,
    @JsonProperty(required = true)
    port: Int,
    @JsonProperty
    useTls: Boolean = false,
    @JsonProperty
    autoStart: Boolean = true) {

  /**
   *  check whether the read side config is valid or not
   * @return true when the read side config is valid and false on the contrary
   */
  def isValid: Boolean = {
    val idPattern = "^[A-Za-z0-9]([A-Za-z0-9_-]*[A-Za-z0-9])?$"
    readSideId.matches(idPattern)
  }
}
