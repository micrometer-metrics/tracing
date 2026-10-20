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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

/**
 * Checks that associating baggage with a trace context while another span is in scope
 * does not make trace contexts reference each other. See gh-1602.
 */
class OtelBaggageRetentionTests {

    private static final int ITERATIONS = 100;

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build())
        .build();

    OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();

    Tracer tracer = new OtelTracer(this.openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing"),
            this.currentTraceContext, event -> {
            }, new OtelBaggageManager(this.currentTraceContext, Collections.singletonList("foo"),
                    Collections.emptyList()));

    @AfterEach
    void close() {
        this.openTelemetrySdk.close();
    }

    @Test
    void baggageScopeForTraceContextOpenedWhileOtherSpanIsInScopeShouldNotRetainSpans() throws Exception {
        thenEarlierTraceContextsAreNotRetainedByLastOne(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            Span previous = this.tracer.nextSpan().name("first").start();
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.spanBuilder().setNoParent().name("s" + i).start();
                try (Tracer.SpanInScope ws = this.tracer.withSpan(previous);
                        BaggageInScope baggage = this.tracer.createBaggageInScope(span.context(), "foo",
                                "value " + i)) {
                    // baggage for span is set while previous is in scope
                }
                previous.end();
                traceContexts.add(new WeakReference<>(previous.context()));
                previous = span;
            }
            previous.end();
            return new Retained(previous.context(), traceContexts);
        });
    }

    @Test
    void spanScopeInsideBaggageScopeForTraceContextOpenedWhileOtherSpanIsInScopeShouldNotRetainSpans()
            throws Exception {
        thenEarlierTraceContextsAreNotRetainedByLastOne(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            Span previous = this.tracer.nextSpan().name("first").start();
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.spanBuilder().setNoParent().name("s" + i).start();
                try (Tracer.SpanInScope ws = this.tracer.withSpan(previous);
                        BaggageInScope baggage = this.tracer.createBaggageInScope(span.context(), "foo", "value " + i);
                        Tracer.SpanInScope ws2 = this.tracer.withSpan(span)) {
                    // span is put in scope inside its baggage scope
                }
                previous.end();
                traceContexts.add(new WeakReference<>(previous.context()));
                previous = span;
            }
            previous.end();
            return new Retained(previous.context(), traceContexts);
        });
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedBaggageSetForTraceContextWhileOtherSpanIsInScopeShouldNotRetainSpans() throws Exception {
        thenEarlierTraceContextsAreNotRetainedByLastOne(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            Span previous = this.tracer.nextSpan().name("first").start();
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.spanBuilder().setNoParent().name("s" + i).start();
                try (Tracer.SpanInScope ws = this.tracer.withSpan(previous)) {
                    // Baggage#set(TraceContext, String) does not open a scope
                    this.tracer.createBaggage("foo").set(span.context(), "value " + i);
                }
                previous.end();
                traceContexts.add(new WeakReference<>(previous.context()));
                previous = span;
            }
            previous.end();
            return new Retained(previous.context(), traceContexts);
        });
    }

    /**
     * Runs the given action on a pooled thread and checks that, while the last trace
     * context the action created is still reachable, none of the trace contexts created
     * before it are.
     * @param action creates spans and returns the last trace context and weak references
     * to the trace contexts created before it
     */
    private static void thenEarlierTraceContextsAreNotRetainedByLastOne(Callable<Retained> action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Retained retained = executor.submit(action).get(10, TimeUnit.SECONDS);
            then(retained.earlier).hasSize(ITERATIONS);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                System.gc();
                then(retained.earlier.stream().filter(traceContext -> traceContext.get() != null).count())
                    .as("Earlier trace contexts still reachable")
                    .isZero();
            });
            then(retained.last).isNotNull();
        }
        finally {
            executor.shutdownNow();
        }
    }

    static class Retained {

        final TraceContext last;

        final List<WeakReference<TraceContext>> earlier;

        Retained(TraceContext last, List<WeakReference<TraceContext>> earlier) {
            this.last = last;
            this.earlier = earlier;
        }

    }

}
