/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.config

import com.github.chiefofstate.helper.BaseSpec

class ReadSideConfigSpec extends BaseSpec {
  "read side config with alphanumeric read side id" should {
    "valid" in {
      // test case 1
      var readSideConfig =
        ReadSideConfig(readSideId = "read-side-1", host = "localhost", port = 0)
      readSideConfig.isValid should be(true)

      // test case 2
      readSideConfig = ReadSideConfig(readSideId = "readSide23", host = "localhost", port = 0)
      readSideConfig.isValid should be(true)

      // test case 3
      readSideConfig = ReadSideConfig(readSideId = "read_side_23", host = "localhost", port = 0)
      readSideConfig.isValid should be(true)

      // test case 4
      readSideConfig = ReadSideConfig(readSideId = "read_side-23", host = "localhost", port = 0)
      readSideConfig.isValid should be(true)

      // test case 5
      readSideConfig = ReadSideConfig(readSideId = "read__side--23", host = "localhost", port = 0)
      readSideConfig.isValid should be(true)
    }

    "invalid" in {
      // test case 1
      var readSideConfig = ReadSideConfig(readSideId = "read__", host = "localhost", port = 0)
      readSideConfig.isValid should be(false)

      // test case 2
      readSideConfig = ReadSideConfig(readSideId = "read__side-", host = "localhost", port = 0)
      readSideConfig.isValid should be(false)
    }
  }

  "read side config protocol validation" should {
    "accept grpc protocol" in {
      val readSideConfig = ReadSideConfig(
        readSideId = "read-side-1",
        protocol = "grpc",
        host = "localhost",
        port = 50053
      )
      readSideConfig.isProtocolValid should be(true)
      readSideConfig.isValid should be(true)
    }

    "accept http protocol" in {
      val readSideConfig = ReadSideConfig(
        readSideId = "read-side-1",
        protocol = "http",
        host = "localhost",
        port = 8080
      )
      readSideConfig.isProtocolValid should be(true)
      readSideConfig.isValid should be(true)
    }

    "accept case-insensitive protocol" in {
      var readSideConfig = ReadSideConfig(
        readSideId = "read-side-1",
        protocol = "GRPC",
        host = "localhost",
        port = 50053
      )
      readSideConfig.isProtocolValid should be(true)

      readSideConfig = ReadSideConfig(
        readSideId = "read-side-1",
        protocol = "HTTP",
        host = "localhost",
        port = 8080
      )
      readSideConfig.isProtocolValid should be(true)
    }

    "reject invalid protocol" in {
      val readSideConfig = ReadSideConfig(
        readSideId = "read-side-1",
        protocol = "websocket",
        host = "localhost",
        port = 8080
      )
      readSideConfig.isProtocolValid should be(false)
      readSideConfig.isValid should be(false)
    }
  }

  "read side config with http protocol" should {
    "be valid with host and port" in {
      val readSideConfig = ReadSideConfig(
        readSideId = "read-side-1",
        protocol = "http",
        host = "read-handler",
        port = 8080
      )
      readSideConfig.isValid should be(true)
    }
  }
}
