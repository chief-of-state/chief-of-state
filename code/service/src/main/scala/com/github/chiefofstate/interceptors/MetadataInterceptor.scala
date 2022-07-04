/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.interceptors

import io.grpc.{ Context, Contexts, Metadata, ServerCall, ServerCallHandler, ServerInterceptor }

/**
 * Intercepts gRPC headers and propagate them downstream via the gRPC context
 */
object MetadataInterceptor extends ServerInterceptor {
  val REQUEST_META: Context.Key[Metadata] = Context.key[Metadata]("metadata")
  override def interceptCall[ReqT, RespT](
      call: ServerCall[ReqT, RespT],
      headers: Metadata,
      next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    val context: Context = Context.current().withValue(REQUEST_META, headers)
    Contexts.interceptCall(context, call, headers, next)
  }
}
