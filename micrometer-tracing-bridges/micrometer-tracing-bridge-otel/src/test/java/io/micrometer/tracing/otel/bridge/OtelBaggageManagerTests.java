/**
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Objects;

class OtelBaggageManagerTests {

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build())
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    OtelBaggageManager otelBaggageManager = new OtelBaggageManager(new OtelCurrentTraceContext(),
            Collections.emptyList(), Collections.emptyList());

    @Test
    void should_return_null_baggage_when_there_is_no_baggage_attached_to_a_trace_context() {
        Baggage baggage = otelBaggageManager
            .getBaggage(OtelTraceContext.fromOtel(otelTracer.spanBuilder("foo").startSpan().getSpanContext()), "foo");

        BDDAssertions.then(baggage).isNull();
    }

    @Test
    void should_return_baggage_when_there_is_baggage_attached_to_a_trace_context() {
        Context context = Context.current()
            .with(io.opentelemetry.api.baggage.Baggage.builder().put("foo", "bar").build());

        try (Scope scope = context.makeCurrent()) {
            Span span = otelTracer.spanBuilder("foo").startSpan();
            TraceContext traceContext = OtelTraceContext.fromOtel(span.getSpanContext());
            Baggage baggage = otelBaggageManager.getBaggage(traceContext, "foo");
            try (BaggageInScope baggageInScope = Objects.requireNonNull(baggage).makeCurrent()) {
                BDDAssertions.then(baggage).isNotNull();
                BDDAssertions.then(baggage.get()).isEqualTo("bar");
            }
        }
    }

    @Test
    void should_return_baggage_with_null_value_when_there_is_no_baggage() {
        Baggage baggage = otelBaggageManager.getBaggage("foo");

        BDDAssertions.then(baggage).isNotNull();
        BDDAssertions.then(baggage.get()).isNull();
    }

    @Test
    void should_return_baggage_with_when_there_is_baggage() {
        Context context = Context.current()
            .with(io.opentelemetry.api.baggage.Baggage.builder().put("foo", "bar").build());

        try (Scope scope = context.makeCurrent()) {
            Span span = otelTracer.spanBuilder("foo").startSpan();
            Baggage baggage = otelBaggageManager.getBaggage("foo");
            try (BaggageInScope baggageInScope = baggage.makeCurrent()) {
                BDDAssertions.then(baggage).isNotNull();
                BDDAssertions.then(baggage.get()).isEqualTo("bar");
            }
        }
    }

}
