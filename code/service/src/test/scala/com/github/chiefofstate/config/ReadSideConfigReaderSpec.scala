/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.fasterxml.jackson.databind.exc.{ InvalidFormatException, MismatchedInputException }
import com.github.chiefofstate.helper.BaseSpec

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
        ReadSideConfig(readSideId = "read-side-1", host = "localhost", port = 100, autoStart = false)

      val readSide2 =
        ReadSideConfig(readSideId = "read-side-2", host = "localhost", port = 200, useTls = true, autoStart = false)

      val configFile = getClass.getResource("/readside-config-testcase-1.yaml").getPath
      val actual = ReadSideConfigReader.read(configFile)
      val expected: Seq[ReadSideConfig] = Seq(readSide1, readSide2)

      actual.length should be(expected.length)
      actual should contain theSameElementsAs expected
    }

    "read configurations with default values" in {
      val readSide1 =
        ReadSideConfig(readSideId = "read-side-1", host = "localhost", port = 100, autoStart = false)

      val readSide2 =
        ReadSideConfig(readSideId = "read-side-2", host = "localhost", port = 200)

      val configFile = getClass.getResource("/readside-config-testcase-2.yaml").getPath
      val actual = ReadSideConfigReader.read(configFile)
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
  }
}
