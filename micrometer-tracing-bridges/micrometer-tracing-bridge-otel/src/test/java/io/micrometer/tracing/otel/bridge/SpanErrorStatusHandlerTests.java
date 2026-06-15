/**
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

import io.micrometer.tracing.ScopedSpan;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpanErrorStatusHandlerTests {

    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    /**
     * Treats {@link ExpectedException} as expected (leaves the status unset, so the span
     * ends as OK) and prefixes the description for every other error.
     */
    SpanErrorStatusHandler customHandler = (error, span) -> {
        if (error instanceof ExpectedException) {
            return;
        }
        span.setStatus(StatusCode.ERROR, "custom: " + error.getMessage());
    };

    OtelTracer defaultTracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
    });

    OtelTracer customTracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
    }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()),
            customHandler);

    @Test
    void default_handler_sets_error_status() {
        defaultTracer.nextSpan().name("foo").start().error(new RuntimeException("boom")).end();

        SpanData spanData = onlyFinishedSpan();
        assertThat(spanData.getStatus()).isEqualTo(StatusData.create(StatusCode.ERROR, "boom"));
        assertThat(spanData.getEvents()).extracting(e -> e.getName()).containsExactly("exception");
    }

    @Test
    void custom_handler_leaves_status_unset_for_expected_exception_on_span() {
        customTracer.nextSpan().name("foo").start().error(new ExpectedException("ignored")).end();

        SpanData spanData = onlyFinishedSpan();
        // status left unset by the handler -> OK on end
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        // exception is still recorded regardless of the status decision
        assertThat(spanData.getEvents()).extracting(e -> e.getName()).containsExactly("exception");
    }

    @Test
    void custom_handler_leaves_status_unset_for_expected_exception_on_scoped_span() {
        ScopedSpan scopedSpan = customTracer.startScopedSpan("foo");
        scopedSpan.error(new ExpectedException("ignored"));
        scopedSpan.end();

        SpanData spanData = onlyFinishedSpan();
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(spanData.getEvents()).extracting(e -> e.getName()).containsExactly("exception");
    }

    @Test
    void custom_handler_leaves_status_unset_for_expected_exception_on_builder() {
        customTracer.spanBuilder().name("foo").error(new ExpectedException("ignored")).start().end();

        SpanData spanData = onlyFinishedSpan();
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(spanData.getEvents()).extracting(e -> e.getName()).containsExactly("exception");
    }

    @Test
    void custom_handler_can_set_custom_description() {
        customTracer.nextSpan().name("foo").start().error(new RuntimeException("boom")).end();

        SpanData spanData = onlyFinishedSpan();
        assertThat(spanData.getStatus()).isEqualTo(StatusData.create(StatusCode.ERROR, "custom: boom"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void null_handler_is_rejected() {
        assertThatThrownBy(() -> new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
        }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("errorStatusHandler");
    }

    private SpanData onlyFinishedSpan() {
        assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);
        return spanExporter.getFinishedSpanItems().get(0);
    }

    static class ExpectedException extends RuntimeException {

        ExpectedException(String message) {
            super(message);
        }

    }

}
