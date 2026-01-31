/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.utils

import com.github.chiefofstate.config.GrpcClientKeepalive
import io.grpc.ManagedChannel
import io.grpc.netty.NegotiationType.{PLAINTEXT, TLS}
import io.grpc.netty.{NegotiationType, NettyChannelBuilder, NettyServerBuilder}

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object Netty {

  /**
   * returns a NettyChannelBuilder
   *
   * @param host host to connect to
   * @param port port to use
   * @param useTls true/false to enable TLS
   * @return a NettyChannelBuilder
   */
  def channelBuilder(host: String, port: Int, useTls: Boolean): NettyChannelBuilder = {
    channelBuilder(host, port, useTls, None)
  }

  /**
   * returns a NettyChannelBuilder with optional HTTP/2 keepalive (functional, immutable style)
   *
   * Keepalive reduces "http2 exception" under load by preventing idle connections from being
   * closed by load balancers or firewalls. Enable when using long-lived connections or high concurrency.
   *
   * @param host host to connect to
   * @param port port to use
   * @param useTls true/false to enable TLS
   * @param keepalive optional keepalive settings (when Some, applies HTTP/2 keepalive)
   * @return a NettyChannelBuilder configured with TLS and optional keepalive
   */
  def channelBuilder(
      host: String,
      port: Int,
      useTls: Boolean,
      keepalive: Option[GrpcClientKeepalive]
  ): NettyChannelBuilder = {
    val negotiationType: NegotiationType = if (useTls) TLS else PLAINTEXT
    val baseBuilder = NettyChannelBuilder.forAddress(host, port).negotiationType(negotiationType)

    keepalive.fold(baseBuilder) { k =>
      baseBuilder
        .keepAliveWithoutCalls(k.withoutCalls)
        .keepAliveTime(k.timeSeconds, TimeUnit.SECONDS)
        .keepAliveTimeout(k.timeoutSeconds, TimeUnit.SECONDS)
    }
  }

  /**
   * creates a pool of gRPC channels for high-concurrency scenarios
   *
   * Multiple channels reduce contention on a single HTTP/2 connection and increase throughput.
   * Each channel maintains its own connection with independent stream limits. Use when:
   * - High concurrency (many concurrent RPCs)
   * - Server-side stream limits are a bottleneck
   * - Single channel shows "http2 exception" or RESOURCE_EXHAUSTED errors
   *
   * @param poolSize number of channels to create (typically 2-8 for most workloads)
   * @param host host to connect to
   * @param port port to use
   * @param useTls true/false to enable TLS
   * @param keepalive optional keepalive settings (applied to all channels in the pool)
   * @return a ChannelPool with round-robin channel selection
   */
  def createChannelPool(
      poolSize: Int,
      host: String,
      port: Int,
      useTls: Boolean,
      keepalive: Option[GrpcClientKeepalive]
  ): ChannelPool = {
    require(poolSize > 0, "poolSize must be positive")
    val channels = (1 to poolSize).map { _ =>
      channelBuilder(host, port, useTls, keepalive).build()
    }
    new ChannelPool(channels.toIndexedSeq)
  }

  /**
   * returns an NettyServerBuilder
   *
   * @param host - host to bind the server
   * @param port - port to start the server on
   * @return a NettyServerBuilder
   */
  def serverBuilder(host: String, port: Int): NettyServerBuilder = {
    NettyServerBuilder
      .forAddress(
        new InetSocketAddress(host, port)
      )
  }
}

/**
 * A thread-safe pool of gRPC channels with round-robin selection.
 *
 * Use this when a single channel is a bottleneck (high concurrency, stream limits).
 * Each call to `next()` returns a channel in a round-robin fashion, distributing load
 * across all channels in the pool.
 *
 * @param channels the sequence of managed channels in the pool
 */
class ChannelPool(channels: IndexedSeq[ManagedChannel]) {
  require(channels.nonEmpty, "ChannelPool must have at least one channel")

  private val counter = new AtomicInteger(0)

  /**
   * returns the next channel in round-robin order (thread-safe)
   *
   * @return a ManagedChannel from the pool
   */
  def next(): ManagedChannel = {
    val index = Math.abs(counter.getAndIncrement() % channels.size)
    channels(index)
  }

  /**
   * returns the number of channels in the pool
   */
  def size: Int = channels.size

  /**
   * shuts down all channels in the pool
   */
  def shutdown(): Unit = {
    channels.foreach(_.shutdown())
  }

  /**
   * attempts to shut down all channels immediately
   */
  def shutdownNow(): Unit = {
    channels.foreach(_.shutdownNow())
  }
}
