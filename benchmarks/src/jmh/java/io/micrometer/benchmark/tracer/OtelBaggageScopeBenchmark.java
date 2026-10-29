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
package io.micrometer.benchmark.tracer;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelSpan;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.openjdk.jmh.annotations.*;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Measures a span scope containing one baggage scope. Span creation and baggage fixture
 * construction happen outside measurement. Closing the span scope restores the same
 * retained context after every operation on both main and the candidate.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class OtelBaggageScopeBenchmark {

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class BaggageState {

        @Param({ "0", "8" })
        public int existingEntries;

        @Param({ "true", "false" })
        public boolean recording;

        OpenTelemetrySdk sdk;

        Tracer tracer;

        Span span;

        Context originalContext;

        @Setup(Level.Trial)
        public void setup() {
            this.originalContext = Context.current();
            this.sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                    .setSampler(this.recording ? Sampler.alwaysOn() : Sampler.alwaysOff())
                    .addSpanProcessor(new DiscardingSpanProcessor())
                    .build())
                .build();
            OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
            this.tracer = new OtelTracer(this.sdk.getTracer("baggage-benchmark"), currentTraceContext, event -> {
            }, new OtelBaggageManager(currentTraceContext, Collections.singletonList("locale"),
                    Collections.emptyList()));

            BaggageBuilder builder = Baggage.builder();
            if (this.existingEntries == 8) {
                builder.put("locale", "en-US")
                    .put("report.format", "pdf")
                    .put("report.id", "report-123")
                    .put("request.channel", "web")
                    .put("deployment.environment", "benchmark")
                    .put("service.region", "ap-northeast-1")
                    .put("tenant.id", "tenant-123")
                    .put("workflow.name", "generate-report");
            }
            try (io.opentelemetry.context.Scope scope = builder.build().makeCurrent()) {
                this.span = this.tracer.nextSpan().name("generate-report").start();
            }

            io.opentelemetry.api.trace.Span nativeSpan = OtelSpan.toOtel(this.span);
            if (!nativeSpan.getSpanContext().isValid() || nativeSpan.isRecording() != this.recording) {
                throw new IllegalStateException("Invalid SDK recording configuration");
            }

            validate(false);
            validate(true);
        }

        private void validate(boolean explicit) {
            try (Tracer.SpanInScope spanScope = this.tracer.withSpan(this.span);
                    BaggageInScope baggage = explicit
                            ? this.tracer.createBaggageInScope(this.span.context(), "locale", "fr-FR")
                            : this.tracer.createBaggageInScope("locale", "fr-FR")) {
                Baggage current = Baggage.current();
                Span currentSpan = this.tracer.currentSpan();
                if (!"fr-FR".equals(current.getEntryValue("locale"))
                        || current.size() != Math.max(1, this.existingEntries) || currentSpan == null
                        || !this.span.context().spanId().equals(currentSpan.context().spanId())) {
                    throw new IllegalStateException("Incorrect baggage scope fixture");
                }
            }
            if (Context.current() != this.originalContext) {
                throw new IllegalStateException("Benchmark scope leaked");
            }
        }

        @TearDown(Level.Trial)
        public void close() {
            this.span.end();
            this.sdk.close();
            if (Context.current() != this.originalContext) {
                throw new IllegalStateException("Benchmark scope leaked");
            }
        }

    }

    @Benchmark
    public String currentSpanBaggageScope(BaggageState state) {
        try (Tracer.SpanInScope spanScope = state.tracer.withSpan(state.span);
                BaggageInScope baggage = state.tracer.createBaggageInScope("locale", "fr-FR")) {
            return baggage.get();
        }
    }

    @Benchmark
    public String explicitContextBaggageScope(BaggageState state) {
        try (Tracer.SpanInScope spanScope = state.tracer.withSpan(state.span);
                BaggageInScope baggage = state.tracer.createBaggageInScope(state.span.context(), "locale", "fr-FR")) {
            return baggage.get();
        }
    }

    private static class DiscardingSpanProcessor implements SpanProcessor {

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
        }

        @Override
        public boolean isEndRequired() {
            return false;
        }

    }

}
