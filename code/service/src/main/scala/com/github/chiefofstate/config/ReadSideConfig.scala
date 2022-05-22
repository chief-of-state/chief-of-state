/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

/**
 * ReadSideConfig defines the configuration of CoS readside
 *
 * @param readSideId the readSide ID
 * @param host the read side server host to receive read side requests
 * @param port the read side server port
 * @param useTls specifies whether SSL is enabled on the read side server
 * @param autoStart specifies whether the read side should start processing messages or be in pause mode
 * @param settings additional read side settings
 */
final case class ReadSideConfig(
    readSideId: String,
    host: String = "",
    port: Int = -1,
    useTls: Boolean = false,
    autoStart: Boolean = false,
    settings: Map[String, String] = Map.empty[String, String]) {

  /**
   * Adds a setting to the config
   *
   * @param key Setting key
   * @param value Setting value
   */
  def addSetting(key: String, value: String): ReadSideConfig =
    copy(settings = settings + (key -> value))

  /**
   * Gets the setting from the config
   *
   * @param key Setting key
   * @return Setting value
   */
  def getSetting(key: String): Option[String] = settings.get(key)

  /**
   * Removes the setting from the config
   *
   * @param key Setting key
   * @return
   */
  def removeSetting(key: String): ReadSideConfig = copy(settings = settings.removed(key))

  /**
   * Lists the settings from the config
   *
   * @return Map[String, String]
   */
  def listSettings: Map[String, String] = settings
}
