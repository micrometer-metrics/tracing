/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.tracing.otel.handler;

import java.time.Duration;
import java.util.Collections;
import java.util.Queue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingRecordingHandler;
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("unchecked")
class DefaultTracingRecordingHandlerOtelTests {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    ArrayListSpanProcessor testSpanProcessor = new ArrayListSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
            .addSpanProcessor(SimpleSpanProcessor.create(testSpanProcessor)).build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> { }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.emptyList(), Collections.emptyList()));

    DefaultTracingRecordingHandler handler = new DefaultTracingRecordingHandler(tracer, customizers);

    @Test
    void should_be_applicable_for_non_null_context() {
        then(handler.supportsContext(new Timer.HandlerContext())).isTrue();
    }

    @Test
    void should_not_be_applicable_for_null_context() {
        then(handler.supportsContext(null)).isFalse();
    }

    @Test
    void should_put_and_remove_trace_from_thread_local_on_scope_change() {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer.HandlerContext context = new Timer.HandlerContext();
        long currentNanos = System.nanoTime();

        handler.onStart(sample, context);

        then(tracer.currentSpan()).as("Span NOT put in scope").isNull();

        handler.onScopeOpened(sample, context);

        then(tracer.currentSpan()).as("Span put in scope").isNotNull();

        handler.onScopeClosed(sample, context);

        then(tracer.currentSpan()).as("Span removed from scope").isNull();

        handler.onStop(sample, context, Timer.builder("name").register(meterRegistry), Duration.ZERO);

        then(tracer.currentSpan()).as("Span still not in scope").isNull();
        thenSpanStartedAndStopped(currentNanos);
    }

    private void thenSpanStartedAndStopped(long currentNanos) {
        Queue<SpanData> spans = testSpanProcessor.spans();
        then(spans).hasSize(1);
        SpanData data = testSpanProcessor.takeLocalSpan();
        long startTimestamp = data.getStartEpochNanos();
        then(startTimestamp).isGreaterThan(currentNanos);
        then(data.getEndEpochNanos()).as("Span has to have a duration").isGreaterThan(startTimestamp);
    }

}
