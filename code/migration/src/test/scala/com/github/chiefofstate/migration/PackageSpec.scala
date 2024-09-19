/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

class PackageSpec extends BaseSpec {

  ".isUpper" should {
    "be true" in {
      "PEKKO_PROJECTION_OFFSET_STORE".isUpper shouldBe true
      "PEKKO PROJECTION OFFSET STORE".isUpper shouldBe true
    }

    "be false with mixed cases" in {
      val tableName: String = "PEKKO_read"
      tableName.isUpper shouldBe false
    }

    "be false lowercase" in {
      val tableName: String = "read_side_offset"
      tableName.isUpper shouldBe false
    }
  }

  ".quote" should {
    "work as expected" in {
      val result: String = "PEKKO_PROJECTION_OFFSET_STORE".quote
      result shouldBe s"""\"PEKKO_PROJECTION_OFFSET_STORE\""""
    }
  }
}
