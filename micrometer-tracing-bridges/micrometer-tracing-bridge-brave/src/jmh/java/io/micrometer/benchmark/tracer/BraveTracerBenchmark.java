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
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
public class BraveTracerBenchmark {

    @State(Scope.Benchmark)
    public static class MicrometerTracingState {

        @Param({"5"})
        public int childSpanCount;

        StrictCurrentTraceContext braveCurrentTraceContext;

        BraveCurrentTraceContext bridgeContext;

        Tracing tracing;

        brave.Tracer braveTracer;

        Tracer tracer;

        @Setup
        public void setup() {
            TestSpanHandler spanHandler = new TestSpanHandler();
            this.braveCurrentTraceContext = StrictCurrentTraceContext.create();
            this.bridgeContext = new BraveCurrentTraceContext(this.braveCurrentTraceContext);
            this.tracing = Tracing.newBuilder()
                .currentTraceContext(this.braveCurrentTraceContext)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(spanHandler)
                .build();
            this.tracing.setNoop(true);
            this.braveTracer = this.tracing.tracer();
            this.tracer = new BraveTracer(this.braveTracer, this.bridgeContext, new BraveBaggageManager());
        }

        @TearDown
        public void close() {
            this.tracing.close();
            this.braveCurrentTraceContext.close();
        }

    }

    @Benchmark
    public void micrometerTracing(MicrometerTracingState state, Blackhole blackhole) {
        Span parentSpan = state.tracer.nextSpan().name("parent-span").start();
        for (int i=0; i< state.childSpanCount; i++) {
            io.micrometer.tracing.Span span = state.tracer.nextSpan(parentSpan).name("new-span");
            span.start().tag("key", "value").event("event").end();
        }
        parentSpan.end();
        blackhole.consume(parentSpan);
    }

    @Benchmark
    public void micrometerTracingWithScope(MicrometerTracingState state, Blackhole blackhole) {
        ScopedSpan parentSpan = state.tracer.startScopedSpan("parent-span");
        for (int i=0; i< state.childSpanCount; i++) {
            ScopedSpan scopedSpan = state.tracer.startScopedSpan("new-span");
            scopedSpan.tag("key", "value").event("event").end();
        }
        parentSpan.end();
        blackhole.consume(parentSpan);
    }

    @State(Scope.Benchmark)
    public static class BraveState {

        @Param({"5"})
        public int childSpanCount;

        StrictCurrentTraceContext braveCurrentTraceContext;

        Tracing tracing;

        brave.Tracer tracer;


        @Setup
        public void setup() {
            TestSpanHandler spanHandler = new TestSpanHandler();
            this.braveCurrentTraceContext = StrictCurrentTraceContext.create();
            this.tracing = Tracing.newBuilder()
                .currentTraceContext(this.braveCurrentTraceContext)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(spanHandler)
                .build();
            this.tracing.setNoop(true);
            this.tracer = this.tracing.tracer();
        }

        @TearDown
        public void close() {
            this.braveCurrentTraceContext.close();
            this.tracing.close();
        }

    }

    @Benchmark
    public void braveTracing(BraveState state, Blackhole blackhole) {
        brave.Span parentSpan = state.tracer.nextSpan().name("parent-span").start();
        TraceContextOrSamplingFlags traceContext = TraceContextOrSamplingFlags.create(parentSpan.context());
        for (int i=0; i< state.childSpanCount; i++) {
            brave.Span span = state.tracer.nextSpan(traceContext).name("new-span");
            span.start().tag("key", "value").annotate("event").finish();
        }
        parentSpan.finish();
        blackhole.consume(parentSpan);
    }

}
