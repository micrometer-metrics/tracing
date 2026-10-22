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
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.propagation.BaggageTextMapPropagator;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

/**
 * Benchmarks for {@link OtelPropagator#inject}, with the propagators that Spring Boot
 * configures for W3C propagation with baggage: W3C trace context, W3C baggage and
 * {@link BaggageTextMapPropagator} for the remote fields. The span and its baggage are
 * put in scope with the Micrometer Tracing API.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class OtelPropagatorBenchmark {

    private static final List<String> REMOTE_FIELDS = Arrays.asList("foo", "foo2");

    private static final int MANY_ENTRIES = 50;

    /**
     * Which baggage/remote-field combination an inject benchmark runs against.
     */
    public enum InjectScenario {

        /** Both configured remote fields are in scope. */
        MATCHING,

        /**
         * No remote fields configured, the baggage in scope is only in the W3C header.
         */
        NO_REMOTE_FIELDS,

        /** Remote fields configured, but the baggage in scope holds none of them. */
        NO_MATCHING_BAGGAGE,

        /** Many baggage entries in scope, only two of which are remote fields. */
        MANY_BAGGAGE_ENTRIES

    }

    @State(Scope.Thread)
    public static class InjectState {

        @Param({ "MATCHING", "NO_REMOTE_FIELDS", "NO_MATCHING_BAGGAGE", "MANY_BAGGAGE_ENTRIES" })
        public InjectScenario scenario;

        OpenTelemetrySdk sdk;

        OtelPropagator propagator;

        TraceContext traceContext;

        private Span span;

        private Tracer.SpanInScope spanInScope;

        private final Deque<BaggageInScope> baggageInScope = new ArrayDeque<>();

        @Setup(Level.Trial)
        public void setup() {
            List<String> remoteFields = this.scenario == InjectScenario.NO_REMOTE_FIELDS ? emptyList() : REMOTE_FIELDS;
            // without a span processor the SDK only creates non-recording spans, and a
            // root span is then invalid and not injected
            this.sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(new NoopSpanProcessor()).build())
                .build();
            io.opentelemetry.api.trace.Tracer otelTracer = this.sdk.getTracer("benchmark");
            OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
            OtelBaggageManager baggageManager = new OtelBaggageManager(currentTraceContext, remoteFields, emptyList());
            Tracer tracer = new OtelTracer(otelTracer, currentTraceContext, event -> {
            }, baggageManager);
            ContextPropagators propagators = ContextPropagators
                .create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(),
                        W3CBaggagePropagator.getInstance(), new BaggageTextMapPropagator(remoteFields)));
            this.propagator = new OtelPropagator(propagators, otelTracer);

            this.span = tracer.nextSpan().name("span").start();
            this.spanInScope = tracer.withSpan(this.span);
            for (Map.Entry<String, String> entry : baggage(this.scenario).entrySet()) {
                this.baggageInScope.push(tracer.createBaggageInScope(entry.getKey(), entry.getValue()));
            }
            this.traceContext = this.span.context();

            Map<String, String> carrier = new HashMap<>();
            this.propagator.inject(this.traceContext, carrier, Map::put);
            boolean expectRemoteFields = this.scenario == InjectScenario.MATCHING
                    || this.scenario == InjectScenario.MANY_BAGGAGE_ENTRIES;
            if (!carrier.containsKey("traceparent") || !carrier.containsKey("baggage")
                    || carrier.containsKey("foo") != expectRemoteFields) {
                throw new IllegalStateException("Unexpected carrier for " + this.scenario + ": " + carrier);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            while (!this.baggageInScope.isEmpty()) {
                this.baggageInScope.pop().close();
            }
            this.spanInScope.close();
            this.span.end();
            this.sdk.close();
        }

        private static Map<String, String> baggage(InjectScenario scenario) {
            Map<String, String> baggage = new LinkedHashMap<>();
            if (scenario == InjectScenario.NO_MATCHING_BAGGAGE) {
                baggage.put("unrelated", "value");
                baggage.put("unrelated2", "value2");
                return baggage;
            }
            if (scenario == InjectScenario.MANY_BAGGAGE_ENTRIES) {
                for (int i = 0; i < MANY_ENTRIES; i++) {
                    baggage.put("key" + i, "value" + i);
                }
            }
            baggage.put("foo", "bar");
            baggage.put("foo2", "bar2");
            return baggage;
        }

    }

    static class NoopSpanProcessor implements SpanProcessor {

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

    @Benchmark
    public Map<String, String> inject(InjectState state) {
        Map<String, String> localCarrier = new HashMap<>();
        state.propagator.inject(state.traceContext, localCarrier, Map::put);
        return localCarrier;
    }

}
