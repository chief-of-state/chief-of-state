/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.fasterxml.jackson.databind.exc.{InvalidFormatException, MismatchedInputException}
import com.github.chiefofstate.helper.BaseSpec
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables

import scala.jdk.CollectionConverters.MapHasAsJava

class ReadSideConfigReaderSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  "ReadSideConfigReader" should {
    "read configurations" in {
      val readSide1 =
        ReadSideConfig(
          readSideId = "read-side-1",
          host = "localhost",
          port = 100,
          autoStart = false,
          failurePolicy = ReadSideFailurePolicy.StopDirective
        )

      val readSide2 =
        ReadSideConfig(
          readSideId = "read-side-2",
          host = "localhost",
          port = 200,
          useTls = true,
          autoStart = false,
          enabled = false
        )

      val configFile = getClass.getResource("/readside-config-testcase-1.yaml").getPath
      val actual     = ReadSideConfigReader.read(configFile)
      val expected: Seq[ReadSideConfig] = Seq(readSide1, readSide2)

      actual.length should be(expected.length)
      actual should contain theSameElementsAs expected
    }

    "read configurations from a directory" in {
      val readSide1 =
        ReadSideConfig(
          readSideId = "read-side-1",
          host = "localhost",
          port = 100,
          autoStart = false,
          enabled = true
        )

      val readSide2 =
        ReadSideConfig(
          readSideId = "read-side-2",
          host = "localhost",
          port = 200,
          useTls = true,
          autoStart = false,
          enabled = true
        )

      val configFile                    = getClass.getResource("/readside-configs").getPath
      val actual                        = ReadSideConfigReader.read(configFile)
      val expected: Seq[ReadSideConfig] = Seq(readSide1, readSide2)

      actual.length should be(expected.length)
      actual should contain theSameElementsAs expected
    }

    "read configurations with default values" in {
      val readSide1 =
        ReadSideConfig(
          readSideId = "read-side-1",
          host = "localhost",
          port = 100,
          autoStart = false
        )

      val readSide2 =
        ReadSideConfig(readSideId = "read-side-2", host = "localhost", port = 200)

      val configFile = getClass.getResource("/readside-config-testcase-2.yaml").getPath
      val actual     = ReadSideConfigReader.read(configFile)
      val expected: Seq[ReadSideConfig] = Seq(readSide1, readSide2)

      actual.length should be(expected.length)
      actual should contain theSameElementsAs expected
    }

    "throw an exception when file is not found" in {
      val configFile = "some-path"
      an[Exception] shouldBe thrownBy(ReadSideConfigReader.read(configFile))
    }

    "throw an exception when a readside setting is invalid" in {
      val configFile = getClass.getResource("/readside-config-testcase-3.yaml").getPath
      an[InvalidFormatException] shouldBe thrownBy(ReadSideConfigReader.read(configFile))
    }

    "throw an exception if one or more of the read side configurations does not contain a port" in {
      val configFile = getClass.getResource("/readside-config-testcase-4.yaml").getPath
      val exception: MismatchedInputException =
        intercept[MismatchedInputException](ReadSideConfigReader.read(configFile))

      val msg = exception.getMessage
      msg should include("Missing required creator property 'port'")
    }

    "throw an exception if one or more of the read side configurations does not contain a host" in {
      val configFile = getClass.getResource("/readside-config-testcase-5.yaml").getPath
      val exception: MismatchedInputException =
        intercept[MismatchedInputException](ReadSideConfigReader.read(configFile))

      val msg = exception.getMessage
      msg should include("Missing required creator property 'host'")
    }

    "throw an exception if one or more of the read side configurations does not contain a readSideId" in {
      val configFile = getClass.getResource("/readside-config-testcase-6.yaml").getPath
      val exception: MismatchedInputException =
        intercept[MismatchedInputException](ReadSideConfigReader.read(configFile))

      val msg = exception.getMessage
      msg should include("Missing required creator property 'readSideId'")
    }

    "throw an exception if one or more of the read side configurations readSideId is invalid" in {
      val configFile = getClass.getResource("/readside-config-testcase-7.yaml").getPath
      val exception: Exception =
        intercept[Exception](ReadSideConfigReader.read(configFile))

      exception.getMessage shouldBe "invalid read side configuration"
    }

    "read from environment variables" in {
      val env = new EnvironmentVariables()
      env
        .set("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        .set("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        .set("COS_READ_SIDE_CONFIG__HOST__RS2", "host2")
        .set("COS_READ_SIDE_CONFIG__PORT__RS2", "2")
        .set("COS_READ_SIDE_CONFIG__HOST__RS3", "host3")
        .set("COS_READ_SIDE_CONFIG__PORT__RS3", "3")
        .set("COS_READ_SIDE_CONFIG__USE_TLS__RS3", "true")
        .set("COS_READ_SIDE_CONFIG__AUTO_START__RS3", "true")
        .setup()

      val readSide1: ReadSideConfig = ReadSideConfig("RS1", "host1", 1)
      val readSide2: ReadSideConfig = ReadSideConfig("RS2", "host2", 2)
      val readSide3: ReadSideConfig = ReadSideConfig("RS3", "host3", 3, useTls = true)

      val actual: Seq[ReadSideConfig]   = ReadSideConfigReader.readFromEnvVars
      val expected: Seq[ReadSideConfig] = Seq(readSide1, readSide2, readSide3)
      actual.length should be(expected.length)
      actual should contain theSameElementsAs expected

      env.teardown()
    }

    "throw no exception when there is no env vars" in {
      noException shouldBe thrownBy(ReadSideConfigReader.readFromEnvVars)
    }

    "throw an exception if one or more of the read side configurations env vars is invalid" in {
      // set the env vars
      val env = new EnvironmentVariables()
      env
        .set("COS_READ_SIDE_CONFIG__HOST__", "not-a-valid-config")
        .set("COS_READ_SIDE_CONFIG__PORT__", "1")
        .setup()

      val exception: Exception = intercept[Exception](ReadSideConfigReader.readFromEnvVars)
      exception.getMessage shouldBe "One or more of the read side configurations is invalid"

      env.teardown()
    }

    "throw an exception if one or more of the read side configurations env vars does not contain the host" in {
      // set the env vars
      val env = new EnvironmentVariables()
      env
        .set("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        .set("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        .set("COS_READ_SIDE_CONFIG__PORT__RS2", "2")
        .setup()

      val exception: Exception = intercept[Exception](ReadSideConfigReader.readFromEnvVars)
      exception.getMessage shouldBe "requirement failed: readside RS2 is missing a HOST"

      env.teardown()
    }

    "throw an exception if one or more of the read side configurations env vars does not contain the port" in {
      // set the env vars
      val env = new EnvironmentVariables()
      env
        .set("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        .set("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        .set("COS_READ_SIDE_CONFIG__HOST__RS2", "host2")
        .setup()

      val exception: Exception = intercept[Exception](ReadSideConfigReader.readFromEnvVars)
      exception.getMessage shouldBe "requirement failed: readside RS2 is missing a PORT"

      env.teardown()
    }

    "throw an exception on an invalid env var name format" in {
      // set the env vars
      val env = new EnvironmentVariables()
      env
        .set("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        .set("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        .set("COS_READ_SIDE_CONFIG____RS1", "host2")
        .setup()

      val exception: Exception = intercept[Exception](ReadSideConfigReader.readFromEnvVars)
      exception.getMessage shouldBe "requirement failed: Setting must be defined in COS_READ_SIDE_CONFIG____RS1"

      env.teardown()
    }

    "throw an exception on invalid keys" in {
      // set the env vars
      val env = new EnvironmentVariables()
      env
        .set("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        .set("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        .set("COS_READ_SIDE_CONFIG__GRPC_SOME_SETTING__RS1", "setting1")
        .setup()

      val exception: Exception = intercept[Exception](ReadSideConfigReader.readFromEnvVars)
      exception.getMessage shouldBe "GRPC_SOME_SETTING is a not valid read side env var key"

      env.teardown()
    }
  }
}
