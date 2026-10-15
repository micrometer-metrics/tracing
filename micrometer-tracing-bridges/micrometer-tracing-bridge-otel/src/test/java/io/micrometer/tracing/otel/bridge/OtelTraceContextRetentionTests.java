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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
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

class OtelTraceContextRetentionTests {

    private static final int ITERATIONS = 100;

    private static final ContextKey<Integer> OTHER_KEY = ContextKey.named("other");

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
    void outOfOrderScopeCloseShouldNotRetainSpans() throws Exception {
        thenAtMostOneTraceContextIsRetainedByPooledThread(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            Tracer.SpanInScope previous = null;
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.spanBuilder().setNoParent().name("s" + i).start();
                Tracer.SpanInScope scope = this.tracer.withSpan(span);
                if (previous != null) {
                    previous.close();
                }
                previous = scope;
                span.end();
                traceContexts.add(new WeakReference<>(span.context()));
            }
            if (previous != null) {
                previous.close();
            }
            return traceContexts;
        });
    }

    @Test
    void outerScopeClosedBeforeInnerShouldNotRetainSpans() throws Exception {
        thenAtMostOneTraceContextIsRetainedByPooledThread(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                Span outer = this.tracer.nextSpan().name("outer " + i).start();
                Tracer.SpanInScope outerScope = this.tracer.withSpan(outer);
                Span inner = this.tracer.nextSpan().name("inner " + i).start();
                Tracer.SpanInScope innerScope = this.tracer.withSpan(inner);
                outerScope.close();
                innerScope.close();
                inner.end();
                outer.end();
                traceContexts.add(new WeakReference<>(outer.context()));
                traceContexts.add(new WeakReference<>(inner.context()));
            }
            return traceContexts;
        });
    }

    @Test
    void outOfOrderScopeCloseWithBaggageShouldNotRetainSpans() throws Exception {
        thenAtMostOneTraceContextIsRetainedByPooledThread(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            Tracer.SpanInScope previous = null;
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.nextSpan().name("s" + i).start();
                Tracer.SpanInScope scope = this.tracer.withSpan(span);
                try (BaggageInScope baggage = this.tracer.createBaggageInScope("foo", "value " + i)) {
                    // baggage scope is closed in order
                }
                if (previous != null) {
                    previous.close();
                }
                previous = scope;
                span.end();
                traceContexts.add(new WeakReference<>(span.context()));
            }
            if (previous != null) {
                previous.close();
            }
            return traceContexts;
        });
    }

    @Test
    @SuppressWarnings("MustBeClosedChecker")
    void baggageScopeLeftOpenInSpanScopeShouldNotRetainSpans() throws Exception {
        thenAtMostOneTraceContextIsRetainedByPooledThread(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.nextSpan().name("s" + i).start();
                try (Tracer.SpanInScope scope = this.tracer.withSpan(span)) {
                    // baggage scope is never closed
                    this.tracer.createBaggageInScope("foo", "value " + i);
                }
                span.end();
                traceContexts.add(new WeakReference<>(span.context()));
            }
            return traceContexts;
        });
    }

    @Test
    @SuppressWarnings("MustBeClosedChecker")
    void otelContextLeftOpenInSpanScopeShouldNotRetainSpans() throws Exception {
        thenAtMostOneTraceContextIsRetainedByPooledThread(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = this.tracer.nextSpan().name("s" + i).start();
                try (Tracer.SpanInScope scope = this.tracer.withSpan(span)) {
                    // OTel scope is never closed
                    Context.current().with(OTHER_KEY, i).makeCurrent();
                }
                span.end();
                traceContexts.add(new WeakReference<>(span.context()));
            }
            return traceContexts;
        });
    }

    /**
     * Runs the given action on a pooled thread and checks that, while that thread is
     * still alive, at most one of the trace contexts the action created is still
     * reachable. A context left current by an out-of-order scope close stays attached to
     * the thread that leaked it, which is expected to retain that one context but not the
     * contexts created before it.
     * @param action creates spans and returns weak references to their trace contexts
     */
    private static void thenAtMostOneTraceContextIsRetainedByPooledThread(
            Callable<List<WeakReference<TraceContext>>> action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<WeakReference<TraceContext>> traceContexts = executor.submit(action).get(10, TimeUnit.SECONDS);
            then(traceContexts).hasSizeGreaterThanOrEqualTo(ITERATIONS);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                System.gc();
                then(traceContexts.stream().filter(traceContext -> traceContext.get() != null).count())
                    .as("Trace contexts still reachable")
                    .isLessThanOrEqualTo(1);
            });
        }
        finally {
            executor.shutdownNow();
        }
    }

}
