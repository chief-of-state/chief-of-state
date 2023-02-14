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
}
