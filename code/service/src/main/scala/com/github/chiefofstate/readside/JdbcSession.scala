/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import org.apache.pekko.japi.function
import org.apache.pekko.projection.jdbc.{JdbcSession => PekkoJdbcSession}

import java.sql.Connection

/**
 * Simple implementation of a JdbcSession that uses an existing
 * connection. This is meant to be used in a connection pool
 *
 * @param conn a java sql Connection
 */
private[readside] class JdbcSession(val conn: Connection) extends PekkoJdbcSession {

  override def withConnection[Result](func: function.Function[Connection, Result]): Result = {
    func(conn)
  }

  override def commit(): Unit   = conn.commit()
  override def rollback(): Unit = conn.rollback()
  override def close(): Unit    = conn.close()
}
