/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

object BootConfig {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val DEPLOYMENT_MODE: String                = "COS_DEPLOYMENT_MODE"
  val DEPLOYMENT_MODE_DOCKER: DeploymentMode = DeploymentMode("docker", "docker.conf")
  val DEPLOYMENT_MODE_K8S: DeploymentMode    = DeploymentMode("kubernetes", "kubernetes.conf")

  def get(): Config = {
    val mode: DeploymentMode = getDeploymentMode
    ConfigFactory.parseResources(mode.file).resolve()
  }

  private[config] def getDeploymentMode: DeploymentMode = {
    val deploymentMode: String = sys.env.getOrElse(DEPLOYMENT_MODE, DEPLOYMENT_MODE_DOCKER.key)
    getDeploymentMode(deploymentMode)
  }

  private[config] def getDeploymentMode(deploymentMode: String): DeploymentMode = {
    deploymentMode.toLowerCase() match {
      case DEPLOYMENT_MODE_DOCKER.key =>
        logger.info(s"configuring deployment in docker")
        DEPLOYMENT_MODE_DOCKER

      case DEPLOYMENT_MODE_K8S.key =>
        logger.info(s"configuring deployment in kubernetes")
        DEPLOYMENT_MODE_K8S

      case unsupported =>
        val err: String = s"unrecognized deployment mode, '$unsupported'"
        logger.error(err)
        throw new IllegalArgumentException(err)
    }
  }

  case class DeploymentMode(key: String, file: String)
}
