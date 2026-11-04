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

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelSpan;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.openjdk.jmh.annotations.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class OtelBaggageReadBenchmark {

    @State(Scope.Thread)
    public static class ReadState {

        @Param({ "empty", "same", "different" })
        public String source;

        OtelBaggageManager manager;

        OtelSpan span;

        io.opentelemetry.context.Scope scope;

        @Setup
        @SuppressWarnings("MustBeClosedChecker") // Closed by the JMH teardown.
        public void setup() {
            OtelCurrentTraceContext current = new OtelCurrentTraceContext();
            this.manager = new OtelBaggageManager(current, Collections.singletonList("locale"),
                    Collections.emptyList());
            Baggage retained = "empty".equals(this.source) ? Baggage.empty()
                    : Baggage.builder()
                        .put("locale", "fr-FR")
                        .put("report.format", "pdf")
                        .put("report.id", "report-123")
                        .put("deployment.environment", "benchmark")
                        .put("service.region", "ap-northeast-1")
                        .put("tenant.id", "tenant-123")
                        .put("workflow.name", "generate-report")
                        .put("request.id", "request-123")
                        .build();
            this.span = new OtelSpan(
                    io.opentelemetry.api.trace.Span.wrap(SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736",
                            "00f067aa0ba902b7", TraceFlags.getDefault(), TraceState.getDefault())),
                    Context.root().with(retained));
            Context active;
            try (io.micrometer.tracing.CurrentTraceContext.Scope spanScope = current.newScope(this.span.context())) {
                active = Context.current();
            }
            if ("different".equals(this.source)) {
                active = active.with(Baggage.builder().put("locale", "en-US").put("request.channel", "web").build());
            }
            this.scope = active.makeCurrent();
            Map<String, String> expected = this.manager.getAllBaggage(this.span.context());
            if (expected.size() != ("empty".equals(this.source) ? 0 : "different".equals(this.source) ? 9 : 8)
                    || (!"empty".equals(this.source) && !"fr-FR".equals(expected.get("locale")))) {
                throw new IllegalStateException("Incorrect baggage read fixture");
            }
        }

        @TearDown
        public void close() {
            this.scope.close();
        }

    }

    @Benchmark
    public io.micrometer.tracing.Baggage retainedLookup(ReadState state) {
        return state.manager.getBaggage(state.span.context(), "LOCALE");
    }

    @Benchmark
    public io.micrometer.tracing.Baggage fallbackOrMissingLookup(ReadState state) {
        return state.manager.getBaggage(state.span.context(), "request.channel");
    }

    @Benchmark
    public Map<String, String> explicitBulkRead(ReadState state) {
        return state.manager.getAllBaggage(state.span.context());
    }

    @Benchmark
    public Map<String, String> implicitBulkRead(ReadState state) {
        return state.manager.getAllBaggage();
    }

}
