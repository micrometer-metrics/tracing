/*
 * Copyright 2024 the original author or authors.
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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.BDDAssertions.then;

class OtelTraceContextTests {

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
        .build();

    OpenTelemetrySdkBuilder openTelemetrySdkBuilder = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider);

    @Test
    void should_return_null_when_parent_invalid() {

        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span span = otelTracer.spanBuilder("foo").startSpan();

            OtelTraceContext otelTraceContext = new OtelTraceContext(span);

            then(otelTraceContext.parentId()).isNull();
        }

    }

    @Test
    void should_return_null_when_spans_not_sampled() { // works differently than Brave
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOff())
            .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
            .build();

        OpenTelemetrySdkBuilder openTelemetrySdkBuilder = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider);

        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span parentSpan = otelTracer.spanBuilder("parent").startSpan();
            Span span = otelTracer.spanBuilder("foo")
                .setParent(parentSpan.storeInContext(Context.current()))
                .startSpan();

            OtelTraceContext otelTraceContext = new OtelTraceContext(span);

            then(otelTraceContext.parentId()).isNull();
        }

    }

    @Test
    void should_return_parentid_when_parent_valid() {
        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span parentSpan = otelTracer.spanBuilder("parent").startSpan();
            Span span = otelTracer.spanBuilder("foo")
                .setParent(parentSpan.storeInContext(Context.current()))
                .startSpan();

            OtelTraceContext otelTraceContext = new OtelTraceContext(span);

            then(otelTraceContext.parentId()).isEqualTo(parentSpan.getSpanContext().getSpanId());
        }

    }

    @Test
    void should_contain_own_span_and_trace_context_key_on_creation() {
        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span span = otelTracer.spanBuilder("foo").startSpan();

            OtelTraceContext otelTraceContext = new OtelTraceContext(span);

            then(Span.fromContext(otelTraceContext.context())).isEqualTo(span);
            then(otelTraceContext.context().get(OtelCurrentTraceContext.OTEL_CONTEXT_KEY)).isSameAs(otelTraceContext);
        }
    }

    @Test
    void should_contain_child_span_and_not_parent_on_child_trace_context_creation() {
        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span parentSpan = otelTracer.spanBuilder("parent").startSpan();
            OtelTraceContext parentTraceContext = new OtelTraceContext(parentSpan);

            Span childSpan = otelTracer.spanBuilder("child").setParent(parentTraceContext.context()).startSpan();
            OtelTraceContext childTraceContext = new OtelTraceContext(parentTraceContext.context(),
                    childSpan.getSpanContext(), childSpan);

            then(Span.fromContext(childTraceContext.context()).getSpanContext()).isEqualTo(childSpan.getSpanContext());
            then(Span.fromContext(childTraceContext.context()).getSpanContext().getSpanId())
                .isNotEqualTo(parentSpan.getSpanContext().getSpanId());
            then(childTraceContext.context().get(OtelCurrentTraceContext.OTEL_CONTEXT_KEY)).isSameAs(childTraceContext);
        }
    }

    @Test
    void span_created_via_tracer_should_contain_own_span_and_key_before_scoping() {
        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
            io.micrometer.tracing.Tracer tracer = new OtelTracer(otelTracer, currentTraceContext, event -> {
            });

            io.micrometer.tracing.Span parent = tracer.nextSpan().name("parent").start();
            OtelTraceContext parentContext = (OtelTraceContext) parent.context();
            then(Span.fromContext(parentContext.context()).getSpanContext()).isEqualTo(parentContext.delegate);
            then(parentContext.context().get(OtelCurrentTraceContext.OTEL_CONTEXT_KEY)).isSameAs(parentContext);

            // Child created with parent in scope
            try (io.micrometer.tracing.Tracer.SpanInScope scope = tracer.withSpan(parent)) {
                io.micrometer.tracing.Span child = tracer.nextSpan().name("child").start();
                OtelTraceContext childContext = (OtelTraceContext) child.context();
                then(Span.fromContext(childContext.context()).getSpanContext()).isEqualTo(childContext.delegate);
                then(Span.fromContext(childContext.context()).getSpanContext().getSpanId())
                    .isNotEqualTo(parentContext.delegate.getSpanId());
                then(childContext.context().get(OtelCurrentTraceContext.OTEL_CONTEXT_KEY)).isSameAs(childContext);
                child.end();
            }

            // Child created with parent passed explicitly (parent not in scope)
            io.micrometer.tracing.Span child2 = Objects.requireNonNull(tracer.nextSpan(parent)).name("child2").start();
            OtelTraceContext child2Context = (OtelTraceContext) child2.context();
            then(Span.fromContext(child2Context.context()).getSpanContext()).isEqualTo(child2Context.delegate);
            then(Span.fromContext(child2Context.context()).getSpanContext().getSpanId())
                .isNotEqualTo(parentContext.delegate.getSpanId());
            then(child2Context.context().get(OtelCurrentTraceContext.OTEL_CONTEXT_KEY)).isSameAs(child2Context);
            child2.end();

            parent.end();
        }
    }

    private static Tracer tracer(OpenTelemetrySdk openTelemetrySdk) {
        return openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");
    }

}
