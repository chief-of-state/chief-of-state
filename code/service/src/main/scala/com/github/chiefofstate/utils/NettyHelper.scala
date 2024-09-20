/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.utils

import io.grpc.netty.NegotiationType.{PLAINTEXT, TLS}
import io.grpc.netty.{NegotiationType, NettyChannelBuilder, NettyServerBuilder}

import java.net.InetSocketAddress

object NettyHelper {

  /**
   * returns a NettyChannelBuilder
   *
   * @param host host to connect to
   * @param port port to use
   * @param useTls true/false to enable TLS
   * @return a NettyChannelBuilder
   */
  def channelBuilder(host: String, port: Int, useTls: Boolean): NettyChannelBuilder = {
    // decide on negotiation type
    val negotiationType: NegotiationType = if (useTls) TLS else PLAINTEXT
    NettyChannelBuilder.forAddress(host, port).negotiationType(negotiationType)
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
