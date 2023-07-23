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
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import scala.jdk.CollectionConverters.CollectionHasAsScala;

/**
 * ReadSideConfigReader read side configuration from env vars
 */
object ReadSideConfigReader {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

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
      mapper
        .readValues(factory.createParser(f), new TypeReference[ReadSideConfig] {})
        .readAll()
        .asScala
        .toSeq
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
        val dirs  = dir.listFiles.filter(_.isDirectory).toList
        files ::: dirs.foldLeft(List.empty[File])(_ ::: go(_))
      // here the default scenario
      case _ => List.empty[File]
    }
    go(dir)
  }

  /**
   * reads the read side from environment variables.
   * This is to allow backward compatibility for older versions of CoS users
   * @return a sequence of read side config
   */
  def readFromEnvVars: Seq[ReadSideConfig] = {
    // define the various env vars settings to read
    val READ_SIDE_HOST_KEY: String       = "HOST"
    val READ_SIDE_PORT_KEY: String       = "PORT"
    val READ_SIDE_TLS_KEY: String        = "USE_TLS"
    val READ_SIDE_AUTO_START: String     = "AUTO_START"
    val READ_SIDE_ENABLED: String        = "ENABLED"
    val READ_SIDE_FAILURE_POLICY: String = "FAILURE_POLICY"

    // let us read the env vars
    val envVars: Map[String, String] = sys.env.filter { pair =>
      val (key, _) = pair
      key.startsWith("COS_READ_SIDE_CONFIG__")
    }

    if (envVars.isEmpty) {
      logger.warn("read sides are enabled but none are configured")
    }

    if (envVars.keySet.exists(v => v.split("__").length != 3)) {
      throw new RuntimeException("One or more of the read side configurations is invalid")
    }

    val groupedEnvVars: Map[String, Iterable[(String, String)]] =
      envVars.groupMap(_._1.split("__").last) { case (k, v) =>
        val settingName: String = k.split("__").tail.head
        require(settingName != "", s"Setting must be defined in $k")

        settingName -> v
      }

    groupedEnvVars.map { case (readSideId, settings) =>
      val readSideConfig: ReadSideConfig =
        settings.foldLeft(ReadSideConfig(readSideId)) {

          case (config, (READ_SIDE_HOST_KEY, value)) =>
            config.copy(host = value)

          case (config, (READ_SIDE_PORT_KEY, value)) =>
            config.copy(port = value.toInt)

          case (config, (READ_SIDE_TLS_KEY, value)) =>
            config.copy(useTls = value.toBooleanOption.getOrElse(false))

          case (config, (READ_SIDE_AUTO_START, value)) =>
            config.copy(autoStart = value.toBooleanOption.getOrElse(false))

          case (config, (READ_SIDE_ENABLED, value)) =>
            config.copy(enabled = value.toBooleanOption.getOrElse(true))

          case (config, (READ_SIDE_FAILURE_POLICY, value)) =>
            config.copy(failurePolicy = value)

          case (_, (key, _)) =>
            throw new IllegalArgumentException(s"$key is a not valid read side env var key")
        }

      // Requires Host and Port to be defined per GrpcReadSideSetting
      require(readSideConfig.host.nonEmpty, s"readside $readSideId is missing a HOST")
      require(readSideConfig.port > 0, s"readside $readSideId is missing a PORT")

      // Validate the failure policy
      require(
        readSideConfig.isFailurePolicyValid,
        s"readside $readSideId failurePolicy is invalid."
      )

      logger.info(
        s"Configuring read side '$readSideId', host=${readSideConfig.host}, port=${readSideConfig.port}, " +
          s"useTls=${readSideConfig.useTls}, autoStart=${readSideConfig.autoStart}, " +
          s"enabled=${readSideConfig.enabled}, failurePolicy=${readSideConfig.failurePolicy}"
      )

      readSideConfig
    }.toSeq
  }
}
