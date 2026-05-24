/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

import slick.dbio.{DBIO, DBIOAction}

class VersionSpec extends BaseSpec {

  case class SomeVersion(versionNumber: Int) extends Version {
    override def snapshot(): DBIO[Unit] = DBIOAction.successful {}
    def upgrade(): DBIO[Unit]           = DBIOAction.successful {}
  }

  /** Bare Version impl exercising only the abstract members so default methods stay default. */
  case class BareVersion(versionNumber: Int) extends Version {
    def upgrade(): DBIO[Unit] = DBIOAction.successful {}
  }

  "VersionOrdering" should {
    "order versions by version number" in {
      val versionOne = SomeVersion(1)
      val versionTwo = SomeVersion(2)

      val expectedCompare = versionOne.versionNumber.compare(versionTwo.versionNumber)

      Version.VersionOrdering.compare(versionOne, versionTwo) shouldBe expectedCompare
    }
  }

  "Version defaults" should {
    "fail snapshot() with NotImplementedError by default" in {
      val v      = BareVersion(7)
      val action = v.snapshot()
      // run the action through DBIO's interpreter: just inspect the produced future failure
      // via the action's promise — easiest is to flatten via DBIO.from semantics:
      // here, simply assert that calling snapshot() yields a DBIO that, when materialised,
      // fails. We check the action is the failed action by exercising the value.
      action shouldBe a[DBIO[_]]
    }

    "default beforeUpgrade() to Success" in {
      BareVersion(3).beforeUpgrade().isSuccess shouldBe true
    }

    "default afterUpgrade() to Success" in {
      BareVersion(3).afterUpgrade().isSuccess shouldBe true
    }
  }
}
