/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.BDDAssertions.then;

class OtelBaggageInScopeTests {

    // without a span processor, newer SDK versions only create non-recording spans, and
    // root spans are then all the same invalid span
    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
            .build())
        .build();

    OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();

    Tracer tracer = new OtelTracer(this.openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing"),
            this.currentTraceContext, event -> {
            }, new OtelBaggageManager(this.currentTraceContext, Collections.singletonList("foo"),
                    Collections.emptyList()));

    @AfterEach
    void close() {
        this.openTelemetrySdk.close();
    }

    @Test
    void should_not_store_span_in_scope_in_trace_context_when_creating_baggage_scope_for_it() {
        Span other = this.tracer.nextSpan().name("other").start();
        Span span = this.tracer.nextSpan().name("span").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(other);
                BaggageInScope baggage = this.tracer.createBaggageInScope(span.context(), "foo", "bar")) {
            Context stored = ((OtelTraceContext) span.context()).context();
            then(io.opentelemetry.api.trace.Span.fromContext(stored))
                .isNotSameAs(((OtelTraceContext) other.context()).span);
            then(Baggage.fromContext(stored).getEntryValue("foo")).isEqualTo("bar");
            then(this.tracer.currentSpan()).isEqualTo(other);
            then(this.tracer.getBaggage("foo").get()).isEqualTo("bar");
        }
        span.end();
        other.end();
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_not_store_span_in_scope_in_trace_context_when_setting_baggage_for_it() {
        Span other = this.tracer.nextSpan().name("other").start();
        Span span = this.tracer.nextSpan().name("span").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(other)) {
            this.tracer.createBaggage("foo").set(span.context(), "bar");
            Context stored = ((OtelTraceContext) span.context()).context();
            then(io.opentelemetry.api.trace.Span.fromContext(stored))
                .isNotSameAs(((OtelTraceContext) other.context()).span);
            then(Baggage.fromContext(stored).getEntryValue("foo")).isEqualTo("bar");
        }
        then(this.tracer.getBaggage("foo").get(span.context())).isEqualTo("bar");
        span.end();
        other.end();
    }

}
