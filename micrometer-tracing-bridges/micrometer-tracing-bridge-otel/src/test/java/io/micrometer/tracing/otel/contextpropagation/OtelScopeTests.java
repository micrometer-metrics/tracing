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
package io.micrometer.tracing.otel.contextpropagation;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.test.tck.ScopeCompatibilityKit;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.jspecify.annotations.NullMarked;
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

@NullMarked
class OtelScopeTests extends ScopeCompatibilityKit {

    private static final int ITERATIONS = 100;

    InMemorySpanExporter testSpanProcessor = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(this.testSpanProcessor))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(this.sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = this.openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    Tracer tracer = new OtelTracer(this.otelTracer, new OtelCurrentTraceContext(), event -> {
    }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.singletonList("foo"),
            Collections.emptyList()));

    @Override
    protected Tracer getTracer() {
        return this.tracer;
    }

    @AfterEach
    void ensureScopesNotLeaking() {
        try {
            then(Context.current()).as("All scopes must be closed").isSameAs(Context.root());
        }
        finally {
            this.sdkTracerProvider.close();
        }
    }

    @Test
    void outOfOrderScopeCloseShouldNotRetainSpans() throws Exception {
        thenAtMostOneTraceContextIsRetainedByPooledThread(() -> {
            List<WeakReference<TraceContext>> traceContexts = new ArrayList<>();
            Tracer.SpanInScope previous = null;
            for (int i = 0; i < ITERATIONS; i++) {
                Span span = getTracer().spanBuilder().setNoParent().name("s" + i).start();
                Tracer.SpanInScope scope = getTracer().withSpan(span);
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
                Span outer = getTracer().nextSpan().name("outer " + i);
                Tracer.SpanInScope outerScope = getTracer().withSpan(outer);
                Span inner = getTracer().nextSpan().name("inner " + i);
                Tracer.SpanInScope innerScope = getTracer().withSpan(inner);
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
                Span span = getTracer().nextSpan().name("s" + i).start();
                Tracer.SpanInScope scope = getTracer().withSpan(span);
                try (BaggageInScope baggage = getTracer().createBaggageInScope("foo", "value " + i)) {
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
