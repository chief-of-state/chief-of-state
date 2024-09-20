/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.Config
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables

class BootConfigSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  ".getDeploymentMode" should {
    "return docker configs" in {
      val mode = BootConfig.getDeploymentMode(BootConfig.DEPLOYMENT_MODE_DOCKER.key)
      mode shouldBe BootConfig.DEPLOYMENT_MODE_DOCKER
    }

    "return k8s configs" in {
      val mode = BootConfig.getDeploymentMode(BootConfig.DEPLOYMENT_MODE_K8S.key)
      mode shouldBe BootConfig.DEPLOYMENT_MODE_K8S
    }

    "error on unknown config" in {
      val actual: IllegalArgumentException = intercept[IllegalArgumentException] {
        BootConfig.getDeploymentMode("not a mode")
      }

      actual.getMessage().contains("not a mode") shouldBe true
    }

    "read the env var" in {
      val env = new EnvironmentVariables()
      env.set(BootConfig.DEPLOYMENT_MODE, BootConfig.DEPLOYMENT_MODE_K8S.key).setup()
      BootConfig.getDeploymentMode shouldBe BootConfig.DEPLOYMENT_MODE_K8S
      env.teardown()
    }
  }

  ".get" should {
    "run e2e" in {
      val env = new EnvironmentVariables()
      env.set(BootConfig.DEPLOYMENT_MODE, BootConfig.DEPLOYMENT_MODE_DOCKER.key).setup()
      val config: Config = BootConfig.get()
      config.getString("deployment-mode") shouldBe "docker"
      env.teardown()
    }
  }
}
