/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.helper.BaseSpec
import org.apache.pekko.japi.function

import java.sql.Connection

class JdbcSessionSpec extends BaseSpec {

  "JdbcSession" should {
    "invoke the given function with the wrapped connection" in {
      val conn     = mock[Connection]
      val session  = new JdbcSession(conn)
      val captured = new java.util.concurrent.atomic.AtomicReference[Connection]()
      val fn: function.Function[Connection, String] = (c: Connection) => {
        captured.set(c); "ok"
      }
      session.withConnection(fn) shouldBe "ok"
      captured.get() shouldBe conn
    }

    "delegate commit/rollback/close to the underlying connection" in {
      val conn    = mock[Connection]
      val session = new JdbcSession(conn)

      (conn.commit _).expects().once()
      session.commit()

      (conn.rollback _).expects().once()
      session.rollback()

      (conn.close _).expects().once()
      session.close()
    }
  }
}
