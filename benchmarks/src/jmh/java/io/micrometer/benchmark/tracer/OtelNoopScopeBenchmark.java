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

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelSpan;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Measures redundant scopes with escaping return values and ordinary inlined use. Valid
 * non-recording spans isolate scope handling from SDK recording/export costs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class OtelNoopScopeBenchmark {

    @State(Scope.Thread)
    public static class ActiveState {

        OtelCurrentTraceContext current = new OtelCurrentTraceContext();

        OtelTracer tracer = new OtelTracer(OpenTelemetry.noop().getTracer("scope-benchmark"), this.current, event -> {
        });

        OtelSpan span = new OtelSpan(
                io.opentelemetry.api.trace.Span.wrap(SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736",
                        "00f067aa0ba902b7", TraceFlags.getDefault(), TraceState.getDefault())));

        Tracer.SpanInScope scope;

        @Setup
        public void enter() {
            this.scope = this.tracer.withSpan(this.span);
            if (!io.opentelemetry.api.trace.Span.current()
                .getSpanContext()
                .equals(OtelSpan.toOtel(this.span).getSpanContext())) {
                throw new IllegalStateException("Span must be current before measurement");
            }
        }

        @TearDown
        public void close() {
            this.scope.close();
        }

    }

    @State(Scope.Thread)
    public static class EmptyState {

        OtelCurrentTraceContext current = new OtelCurrentTraceContext();

        OtelTracer tracer = new OtelTracer(OpenTelemetry.noop().getTracer("scope-benchmark"), this.current, event -> {
        });

        OtelSpan span = new OtelSpan(
                io.opentelemetry.api.trace.Span.wrap(SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736",
                        "00f067aa0ba902b7", TraceFlags.getDefault(), TraceState.getDefault())));

        io.opentelemetry.context.Scope scope;

        @Setup
        @SuppressWarnings("MustBeClosedChecker") // Closed by the JMH teardown.
        public void enter() {
            this.scope = Context.root().makeCurrent();
        }

        @TearDown
        public void close() {
            this.scope.close();
        }

    }

    @Benchmark
    public CurrentTraceContext.Scope currentTraceContextScope(ActiveState state) {
        try (CurrentTraceContext.Scope scope = state.current.maybeScope(state.span.context())) {
            return scope;
        }
    }

    @Benchmark
    public Tracer.SpanInScope tracerScope(ActiveState state) {
        try (Tracer.SpanInScope scope = state.tracer.withSpan(state.span)) {
            return scope;
        }
    }

    @Benchmark
    public void inlinedTracerScope(ActiveState state) {
        try (Tracer.SpanInScope scope = state.tracer.withSpan(state.span)) {
        }
    }

    @Benchmark
    public CurrentTraceContext.Scope emptyScope(EmptyState state) {
        try (CurrentTraceContext.Scope scope = state.current.maybeScope(null)) {
            return scope;
        }
    }

    @Benchmark
    public Tracer.SpanInScope realTracerScope(EmptyState state) {
        try (Tracer.SpanInScope scope = state.tracer.withSpan(state.span)) {
            return scope;
        }
    }

}
