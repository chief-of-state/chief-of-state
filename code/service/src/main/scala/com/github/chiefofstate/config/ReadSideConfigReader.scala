/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import java.io.File
import scala.jdk.CollectionConverters.CollectionHasAsScala;

/**
 * ReadSideConfigReader read side configuration from env vars
 */
object ReadSideConfigReader {

  /**
   * reads the readside yaml configuration file
   * @param resourcePath the yaml configuration file
   * @return a sequence of read side config
   */
  def read(resourcePath: String): Seq[ReadSideConfig] = {
    val factory = new YAMLFactory()
    val mapper = new ObjectMapper(factory)
    val parser = factory.createParser(new File(resourcePath))
    mapper.registerModule(DefaultScalaModule)
    val configs = mapper.readValues(parser, new TypeReference[ReadSideConfig] {}).readAll().asScala.toSeq
    // let us validate the configs
    if (!configs.forall(_.isValid)) {
      throw new IllegalArgumentException("invalid read side configuration")
    }
    configs
  }
}
