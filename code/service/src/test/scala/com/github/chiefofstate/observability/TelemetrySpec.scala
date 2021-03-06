/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.observability

import com.github.chiefofstate.helper.BaseSpec
import io.opentelemetry.api.trace.{ Span, Tracer }
import io.opentelemetry.api.{ GlobalOpenTelemetry, OpenTelemetry }
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.extension.trace.propagation.B3Propagator

class TelemetrySpec extends BaseSpec {
  val propagators: ContextPropagators =
    ContextPropagators.create(B3Propagator.injectingMultiHeaders())
  val ot: OpenTelemetry = OpenTelemetrySdk.builder().setPropagators(propagators).build()

  GlobalOpenTelemetry.resetForTest()
  GlobalOpenTelemetry.set(ot)
  val tracer: Tracer = ot.getTracer("testTracer")

  ".getTracingHeaders" should {
    "yield a map with parent spanId and traceId" in {

      val span: Span = tracer.spanBuilder("foo").startSpan()

      val scope = span.makeCurrent()

      val actual = Telemetry.getTracingHeaders(Context.current())
      scope.close()
      span.end()

      actual.get("X-B3-SpanId") shouldBe Some(span.getSpanContext.getSpanId)
      actual.get("X-B3-TraceId") shouldBe Some(span.getSpanContext.getTraceId)
    }
  }
  ".getParentSpanContext" should {
    "return a span context given the headers" in {
      val spanID = "1111111111111111"
      val traceID = "00000000000000002222222222222222"
      val headers: Map[String, String] =
        Map("X-B3-SpanId" -> spanID, "X-B3-TraceId" -> traceID, "X-B3-SAMPLED" -> "1")
      val actual = Telemetry.getParentSpanContext(Context.current(), headers)
      val span = Span.fromContext(actual)
      span.getSpanContext.getSpanId shouldBe spanID
      span.getSpanContext.getTraceId shouldBe traceID
    }
  }
}
