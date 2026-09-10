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
import io.micrometer.tracing.otel.propagation.BaggageTextMapPropagator;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

/**
 * Benchmarks for {@link BaggageTextMapPropagator}, the OpenTelemetry bridge's
 * one-header-per-field baggage propagator.
 *
 * <p>
 * Note that {@code inject} reads baggage from the <em>current</em> {@link Context} via
 * {@link OtelBaggageManager#getAllBaggage()} rather than from the {@code Context} passed
 * to it, so the inject scenarios below make baggage current for the whole trial. Without
 * that, inject would be measured as a no-op.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class BaggageTextMapPropagatorBenchmark {

    private static final List<String> REMOTE_FIELDS = Arrays.asList("foo", "foo2");

    private static final int MANY_ENTRIES = 50;

    /**
     * Which baggage/remote-field combination an inject benchmark runs against.
     */
    public enum InjectScenario {

        /** Both configured remote fields are present in the current baggage. */
        MATCHING,

        /** No remote fields configured, so nothing can ever be propagated. */
        NO_REMOTE_FIELDS,

        /** Remote fields configured, but the current baggage holds none of them. */
        NO_MATCHING_BAGGAGE,

        /** Many baggage entries present, only two of which are remote fields. */
        MANY_BAGGAGE_ENTRIES

    }

    private static TextMapGetter<Map<String, String>> textMapGetter(final List<String> keys) {
        return new TextMapGetter<Map<String, String>>() {
            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return keys;
            }

            @Override
            public String get(Map<String, String> carrier, String key) {
                return carrier == null ? null : carrier.get(key);
            }
        };
    }

    private static BaggageTextMapPropagator propagator(List<String> remoteFields) {
        return new BaggageTextMapPropagator(remoteFields,
                new OtelBaggageManager(new OtelCurrentTraceContext(), remoteFields, emptyList()));
    }

    @State(Scope.Benchmark)
    public static class InjectState {

        @Param({ "MATCHING", "NO_REMOTE_FIELDS", "NO_MATCHING_BAGGAGE", "MANY_BAGGAGE_ENTRIES" })
        public InjectScenario scenario;

        BaggageTextMapPropagator propagator;

        TextMapSetter<Map<String, String>> setter;

        private io.opentelemetry.context.Scope scope;

        @Setup(Level.Trial)
        @SuppressWarnings("MustBeClosedChecker") // closed in tearDown()
        public void setup() {
            List<String> remoteFields = this.scenario == InjectScenario.NO_REMOTE_FIELDS ? emptyList() : REMOTE_FIELDS;
            this.propagator = propagator(remoteFields);
            this.setter = Map::put;
            this.scope = Context.root().with(baggage(this.scenario)).makeCurrent();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.scope.close();
        }

        private static Baggage baggage(InjectScenario scenario) {
            BaggageBuilder builder = Baggage.builder();
            if (scenario == InjectScenario.NO_MATCHING_BAGGAGE) {
                builder.put("unrelated", "value");
                builder.put("unrelated2", "value2");
                return builder.build();
            }
            if (scenario == InjectScenario.MANY_BAGGAGE_ENTRIES) {
                for (int i = 0; i < MANY_ENTRIES; i++) {
                    builder.put("key" + i, "value" + i);
                }
            }
            builder.put("foo", "bar");
            builder.put("foo2", "bar2");
            return builder.build();
        }

    }

    @State(Scope.Benchmark)
    public static class ExtractState {

        BaggageTextMapPropagator propagator;

        TextMapGetter<Map<String, String>> getter;

        Context root;

        Context contextWithBaggage;

        Map<String, String> matchingCarrier;

        Map<String, String> nonMatchingCarrier;

        @Setup(Level.Trial)
        public void setup() {
            this.propagator = propagator(REMOTE_FIELDS);
            this.getter = textMapGetter(REMOTE_FIELDS);
            this.root = Context.root();
            this.contextWithBaggage = Context.root().with(Baggage.builder().put("lorem", "ipsum").build());

            this.matchingCarrier = new HashMap<>();
            this.matchingCarrier.put("foo", "bar");
            this.matchingCarrier.put("foo2", "bar2");

            // The common production case: a request carrying no baggage headers at all.
            this.nonMatchingCarrier = new HashMap<>();
            this.nonMatchingCarrier.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            this.nonMatchingCarrier.put("content-type", "application/json");
        }

    }

    @Benchmark
    public Map<String, String> inject(InjectState state) {
        Map<String, String> localCarrier = new HashMap<>();
        state.propagator.inject(Context.root(), localCarrier, state.setter);
        return localCarrier;
    }

    @Benchmark
    public Context extract(ExtractState state) {
        return state.propagator.extract(state.root, state.matchingCarrier, state.getter);
    }

    @Benchmark
    public Context extractNoMatchingFields(ExtractState state) {
        return state.propagator.extract(state.root, state.nonMatchingCarrier, state.getter);
    }

    @Benchmark
    public Context extractIntoExistingBaggage(ExtractState state) {
        return state.propagator.extract(state.contextWithBaggage, state.matchingCarrier, state.getter);
    }

}
