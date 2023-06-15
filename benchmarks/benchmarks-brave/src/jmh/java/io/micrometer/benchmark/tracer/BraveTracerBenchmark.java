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

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class BraveTracerBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(BraveTracerBenchmark.class.getSimpleName())
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.SampleTime)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    public static class MicrometerTracingState {

        TestSpanHandler spans;

        StrictCurrentTraceContext braveCurrentTraceContext;

        BraveCurrentTraceContext bridgeContext;

        Tracing tracing;

        brave.Tracer braveTracer;

        Tracer tracer;

        Span createdSpan;

        Span startedSpan;

        @Setup
        public void setup() {
            this.spans = new TestSpanHandler();
            this.braveCurrentTraceContext = StrictCurrentTraceContext.create();
            this.bridgeContext = new BraveCurrentTraceContext(this.braveCurrentTraceContext);
            this.tracing = Tracing.newBuilder()
                .currentTraceContext(this.braveCurrentTraceContext)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(this.spans)
                .build();
            this.braveTracer = this.tracing.tracer();
            this.tracer = new BraveTracer(this.braveTracer, this.bridgeContext, new BraveBaggageManager());
            this.createdSpan = this.tracer.nextSpan().name("created-span");
            this.startedSpan = this.tracer.nextSpan().name("started-span").start();
        }

        @TearDown
        public void close() {
            tracing.close();
            braveCurrentTraceContext.close();
        }

    }

    @State(Scope.Benchmark)
    public static class BraveState {

        TestSpanHandler spans;

        StrictCurrentTraceContext braveCurrentTraceContext;

        Tracing tracing;

        brave.Tracer tracer;

        brave.Span createdSpan;

        brave.Span startedSpan;

        TraceContextOrSamplingFlags parentSpan;

        @Setup
        public void setup() {
            this.spans = new TestSpanHandler();
            this.braveCurrentTraceContext = StrictCurrentTraceContext.create();
            this.tracing = Tracing.newBuilder()
                .currentTraceContext(this.braveCurrentTraceContext)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(this.spans)
                .build();
            this.tracer = this.tracing.tracer();
            this.createdSpan = this.tracer.nextSpan().name("created-span");
            this.startedSpan = this.tracer.nextSpan().name("started-span").start();
            this.parentSpan = TraceContextOrSamplingFlags.create(this.startedSpan.context());
        }

        @TearDown
        public void close() {
            tracing.close();
            braveCurrentTraceContext.close();
        }

    }

    @Benchmark
    public void micrometerTracing_createSpan(MicrometerTracingState state, Blackhole blackhole) {
        Span span = state.tracer.nextSpan();
        blackhole.consume(span);
    }

    @Benchmark
    public void micrometerTracing_createSpanWithParent(MicrometerTracingState state, Blackhole blackhole) {
        Span span = state.tracer.nextSpan(state.startedSpan);
        blackhole.consume(span);
    }

    @Benchmark
    public void micrometerTracing_startStopSpan(MicrometerTracingState state, Blackhole blackhole) {
        Span span = state.createdSpan.start();
        span.end();
        blackhole.consume(span);
    }

    @Benchmark
    public void micrometerTracing_openCloseScope(MicrometerTracingState state, Blackhole blackhole) {
        Tracer.SpanInScope scope = state.tracer.withSpan(state.startedSpan);
        scope.close();
        blackhole.consume(scope);
    }

    @Benchmark
    public void micrometerTracing_tagASpan(MicrometerTracingState state, Blackhole blackhole) {
        Span span = state.startedSpan.tag("foo", "bar");
        blackhole.consume(span);
    }

    @Benchmark
    public void micrometerTracing_annotateASpan(MicrometerTracingState state, Blackhole blackhole) {
        Span span = state.startedSpan.event("event");
        blackhole.consume(span);
    }

    @Benchmark
    public void micrometerTracing_getCurrentSpanWhenNoSpanPresent(MicrometerTracingState state, Blackhole blackhole) {
        Span span = state.tracer.currentSpan();
        blackhole.consume(span);
    }

    @Benchmark
    public void tracer_createSpan(BraveState state, Blackhole blackhole) {
        brave.Span span = state.tracer.nextSpan();
        blackhole.consume(span);
    }

    @Benchmark
    public void tracer_createSpanWithParent(BraveState state, Blackhole blackhole) {
        brave.Span span = state.tracer.nextSpan(state.parentSpan);
        blackhole.consume(span);
    }

    @Benchmark
    public void tracer_startStopSpan(BraveState state, Blackhole blackhole) {
        brave.Span span = state.createdSpan.start();
        span.finish();
        blackhole.consume(span);
    }

    @Benchmark
    public void tracer_openCloseScope(BraveState state, Blackhole blackhole) {
        brave.Tracer.SpanInScope scope = state.tracer.withSpanInScope(state.startedSpan);
        scope.close();
        blackhole.consume(scope);
    }

    @Benchmark
    public void tracer_tagASpan(BraveState state, Blackhole blackhole) {
        brave.Span span = state.startedSpan.tag("foo", "bar");
        blackhole.consume(span);
    }

    @Benchmark
    public void tracer_annotateASpan(BraveState state, Blackhole blackhole) {
        brave.Span span = state.startedSpan.annotate("event");
        blackhole.consume(span);
    }

    @Benchmark
    public void tracer_getCurrentSpanWhenNoSpanPresent(BraveState state, Blackhole blackhole) {
        brave.Span span = state.tracer.currentSpan();
        blackhole.consume(span);
    }

}
