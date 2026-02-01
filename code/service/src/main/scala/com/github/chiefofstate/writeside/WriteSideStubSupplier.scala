/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.netty.ChannelPool
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub

/**
 * Supplies a write-side gRPC stub for the duration of one call.
 *
 * Abstracts over a single channel (one stub) or a channel pool (stub from pool.next() per call).
 * Used by CommandHandler and EventHandler to distribute load across channels when pool-size > 1.
 *
 * @see com.github.chiefofstate.utils.ChannelPool
 */
trait WriteSideStubSupplier {

  /**
   * Runs the given function with a stub. For a single channel, the same stub is used;
   * for a pool, a stub backed by the next channel in the pool is used (thread-safe round-robin).
   *
   * @param f function to run with the stub
   * @return the result of f
   */
  def withStub[A](f: WriteSideHandlerServiceBlockingStub => A): A
}

/**
 * Supplies the same stub for every call (single channel).
 */
final case class SingleStubSupplier(stub: WriteSideHandlerServiceBlockingStub)
    extends WriteSideStubSupplier {

  override def withStub[A](f: WriteSideHandlerServiceBlockingStub => A): A =
    f(stub)
}

/**
 * Supplies a stub from the pool for each call (round-robin across channels).
 */
final case class PooledStubSupplier(pool: ChannelPool) extends WriteSideStubSupplier {

  override def withStub[A](f: WriteSideHandlerServiceBlockingStub => A): A = {
    val channel = pool.next()
    val stub    = new WriteSideHandlerServiceBlockingStub(channel)
    f(stub)
  }
}
