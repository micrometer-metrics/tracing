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
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class OtelTracerBenchmark {

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

        ArrayListSpanProcessor spans;

        SdkTracerProvider sdkTracerProvider;

        OpenTelemetrySdk openTelemetrySdk;

        io.opentelemetry.api.trace.Tracer otelTracer;

        OtelCurrentTraceContext otelCurrentTraceContext;

        OtelTracer tracer;

        Span createdSpan;

        Span startedSpan;

        @Setup
        public void setup() {
            this.spans = new ArrayListSpanProcessor();
            this.sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(alwaysOn())
                .addSpanProcessor(spans)
                .build();
            this.openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
                .build();
            this.otelTracer = openTelemetrySdk.getTracerProvider()
                .get("io.micrometer.micrometer-tracing");
            this.otelCurrentTraceContext = new OtelCurrentTraceContext();
            this.tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
            }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
            this.createdSpan = this.tracer.nextSpan().name("created-span");
            this.startedSpan = this.tracer.nextSpan().name("started-span").start();
        }

        @TearDown
        public void close() {
            spans.close();
        }

    }

    @State(Scope.Benchmark)
    public static class OtelState {

        ArrayListSpanProcessor spans;

        SdkTracerProvider sdkTracerProvider;

        OpenTelemetrySdk openTelemetrySdk;

        io.opentelemetry.api.trace.Tracer tracer;

        SpanBuilder createdSpan;

        io.opentelemetry.api.trace.Span startedSpan;

        Context parentContext;

        @Setup
        public void setup() {
            this.spans = new ArrayListSpanProcessor();
            this.sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(alwaysOn())
                .addSpanProcessor(spans)
                .build();
            this.openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
                .build();
            this.tracer = openTelemetrySdk.getTracerProvider()
                .get("io.micrometer.micrometer-tracing");
            this.createdSpan = this.tracer.spanBuilder("created-span");
            this.startedSpan = this.tracer.spanBuilder("started-span").startSpan();
            this.parentContext = Context.root().with(this.startedSpan);
        }

        @TearDown
        public void close() {
            spans.close();
        }

    }

    // TODO: Add randomness in state
    // TODO: Add a typical use-case full scenario with all steps (create, start, stop, scope, tag, event)
    // TODO: A noop case with micrometer tracing

    @Benchmark
    public void micrometerTracing_createSpan(MicrometerTracingState state) {
        state.tracer.nextSpan();
    }

    @Benchmark
    public void micrometerTracing_createSpanWithParent(MicrometerTracingState state) {
        state.tracer.nextSpan(state.startedSpan);
    }

    @Benchmark
    public void micrometerTracing_startStopSpan(MicrometerTracingState state) {
        state.createdSpan.start().end();
    }

    @Benchmark
    public void micrometerTracing_openCloseScope(MicrometerTracingState state) {
        state.tracer.withSpan(state.startedSpan).close();
    }

    @Benchmark
    public void micrometerTracing_tagASpan(MicrometerTracingState state) {
        state.startedSpan.tag("foo", "bar");
    }

    @Benchmark
    public void micrometerTracing_annotateASpan(MicrometerTracingState state) {
        state.startedSpan.event("event");
    }

    @Benchmark
    public void micrometerTracing_getCurrentSpanWhenNoSpanPresent(MicrometerTracingState state) {
        state.tracer.currentSpan();
    }

    @Benchmark
    public void tracer_createSpan(OtelState state) {
        state.tracer.spanBuilder("new-span").startSpan();
    }

    @Benchmark
    public void tracer_createSpanWithParent(OtelState state) {
        state.tracer.spanBuilder("with-parent").setParent(state.parentContext).startSpan();
    }

    @Benchmark
    public void tracer_startStopSpan(OtelState state) {
        state.createdSpan.startSpan().end();
    }

    @Benchmark
    public void tracer_openCloseScope(OtelState state) {
        state.startedSpan.makeCurrent().close();
    }

    @Benchmark
    public void tracer_tagASpan(OtelState state) {
        state.startedSpan.setAttribute("foo", "bar");
    }

    @Benchmark
    public void tracer_annotateASpan(OtelState state) {
        state.startedSpan.addEvent("event");
    }

    @Benchmark
    public void tracer_getCurrentSpanWhenNoSpanPresent(OtelState state) {
        io.opentelemetry.api.trace.Span.current();
    }

}
