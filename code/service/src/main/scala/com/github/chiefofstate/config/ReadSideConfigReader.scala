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
   * reads the read side yaml configuration file
   * @param resourcePath the yaml configuration file
   * @return a sequence of read side config
   */
  def read(resourcePath: String): Seq[ReadSideConfig] = {
    // let us make sure that the path is not null
    require(resourcePath.nonEmpty, "path is required")
    // create a file handle
    val fh = new File(resourcePath)
    // make sure the file exist
    require(fh.exists(), "path does not exist")
    fh match {
      case folder if folder.isDirectory =>
        // get the list of all the files in that directory
        val files = listFiles(folder)
        files.foldLeft(Seq.empty[ReadSideConfig]) { (seq, f) =>
          // parse the yaml file and append them to the seq
          seq ++ parseYAMLFile(f)
        }
      case f if f.isFile =>
        parseYAMLFile(f)
      case _ => Seq()
    }
  }

  private def parseYAMLFile(f: File): Seq[ReadSideConfig] = {
    // create an instance of YAMLFactory
    val factory = new YAMLFactory()
    // create an instance of ObjectMapper
    val mapper = new ObjectMapper(factory)
    // register the default scala module
    mapper.registerModule(DefaultScalaModule)
    val configs =
      mapper.readValues(factory.createParser(f), new TypeReference[ReadSideConfig] {}).readAll().asScala.toSeq
    // let us validate the configs
    if (!configs.forall(_.isValid)) {
      throw new IllegalArgumentException("invalid read side configuration")
    }
    configs
  }

  private def listFiles(dir: File): Seq[File] = {
    def go(dir: File): List[File] = dir match {
      // here the directory exist and it is readable
      case d if d.exists && d.isDirectory && d.canRead =>
        val files = d.listFiles.filter(a => a.canRead && a.isFile).toList
        val dirs = dir.listFiles.filter(_.isDirectory).toList
        files ::: dirs.foldLeft(List.empty[File])(_ ::: go(_))
      // here the default scenario
      case _ => List.empty[File]
    }
    go(dir)
  }
}
