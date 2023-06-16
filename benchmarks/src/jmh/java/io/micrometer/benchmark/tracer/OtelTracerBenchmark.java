/*
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collections;

@BenchmarkMode(Mode.Throughput)
public class OtelTracerBenchmark implements MicrometerTracingBenchmarks {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(OtelTracerBenchmark.class.getSimpleName())
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.SampleTime)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    public static class MicrometerTracingState {

        @Param({"5"})
        public int childSpanCount;

        SdkTracerProvider sdkTracerProvider;

        OpenTelemetrySdk openTelemetrySdk;

        io.opentelemetry.api.trace.Tracer otelTracer;

        OtelCurrentTraceContext otelCurrentTraceContext;

        OtelTracer tracer;

        Span createdSpan;

        Span startedSpan;

        @Setup
        public void setup() {
            this.sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOff())
                .build();
            this.openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .build();
            this.otelTracer = openTelemetrySdk.getTracerProvider()
                .get("io.micrometer.micrometer-tracing");
            this.otelCurrentTraceContext = new OtelCurrentTraceContext();
            this.tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
            }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
            this.createdSpan = this.tracer.nextSpan().name("created-span");
            this.startedSpan = this.tracer.nextSpan().name("started-span").start();
        }

    }

    @Benchmark
    public void micrometerTracing(MicrometerTracingState state, Blackhole blackhole) {
        micrometerTracing(state.tracer, state.childSpanCount, blackhole);
    }

    @Benchmark
    public void micrometerTracingWithScope(MicrometerTracingState state, Blackhole blackhole) {
        micrometerTracingWithScope(state.tracer, state.childSpanCount, blackhole);
    }


    @State(Scope.Benchmark)
    public static class OtelState {

        @Param({"5"})
        public int childSpanCount;

        SdkTracerProvider sdkTracerProvider;

        OpenTelemetrySdk openTelemetrySdk;

        io.opentelemetry.api.trace.Tracer tracer;

        io.opentelemetry.api.trace.Span startedSpan;

        Context parentContext;

        @Setup
        public void setup() {
            this.sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOff())
                .build();
            this.openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .build();
            this.tracer = openTelemetrySdk.getTracerProvider()
                .get("io.micrometer.micrometer-tracing");
            this.startedSpan = this.tracer.spanBuilder("started-span").startSpan();
            this.parentContext = Context.root().with(this.startedSpan);
        }

    }


    @Benchmark
    public void otelTracing(OtelState state, Blackhole blackhole) {
        io.opentelemetry.api.trace.Span parentSpan = state.tracer.spanBuilder("parent-span").startSpan();
        for (int i = 0; i < state.childSpanCount; i++) {
            io.opentelemetry.api.trace.Span span = state.tracer.spanBuilder("new-span" + i).setParent(state.parentContext).startSpan();
            span.setAttribute("key", "value").addEvent("event").end();
        }
        parentSpan.end();
        blackhole.consume(parentSpan);
    }

    @Benchmark
    public void otelTracingWithScope(OtelState state, Blackhole blackhole) {
        io.opentelemetry.api.trace.Span parentSpan = state.tracer.spanBuilder("parent-span").startSpan();
        try (io.opentelemetry.context.Scope scope = parentSpan.makeCurrent()) {
            for (int i = 0; i < state.childSpanCount; i++) {
                io.opentelemetry.api.trace.Span span = state.tracer.spanBuilder("new-span" + i).startSpan();
                try (io.opentelemetry.context.Scope scope2 = span.makeCurrent()) {
                    span.setAttribute("key", "value").addEvent("event");
                }
                span.end();
            }
        }
        parentSpan.end();
        blackhole.consume(parentSpan);
    }

}
