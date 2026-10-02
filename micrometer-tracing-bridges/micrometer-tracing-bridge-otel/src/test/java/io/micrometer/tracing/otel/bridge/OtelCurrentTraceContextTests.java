/**
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.BDDAssertions.then;

class OtelCurrentTraceContextTests {

    private static final int ITERATIONS = 1_000;

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).build();

    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    OtelTracer tracer = new OtelTracer(openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing"),
            otelCurrentTraceContext, event -> {
            });

    // A scope closed out of order stays attached to the thread that closed it, so
    // every test runs on its own thread.
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void close() {
        executor.shutdownNow();
        openTelemetrySdk.close();
    }

    @Test
    void should_restore_trace_context_when_scope_is_closed_in_order() throws Exception {
        onFreshThread(() -> {
            Span span = tracer.nextSpan();
            OtelTraceContext traceContext = (OtelTraceContext) span.context();
            Context contextBeforeScope = traceContext.context();

            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                then(traceContext.context()).isNotSameAs(contextBeforeScope);
                then(Context.current()).isSameAs(traceContext.context());
            }
            span.end();

            then(traceContext.context()).isSameAs(contextBeforeScope);
            then(Context.current()).isSameAs(contextBeforeScope);
        });
    }

    @Test
    void should_not_chain_trace_contexts_when_scopes_are_closed_out_of_order() throws Exception {
        // Each iteration opens its scope before closing the previous one, so every close
        // but the last one is out of order and the thread never unwinds
        TraceContext last = onFreshThread(() -> {
            Span span = tracer.nextSpan().name("span 0");
            Tracer.SpanInScope previous = tracer.withSpan(span);
            span.end();
            for (int i = 1; i < ITERATIONS; i++) {
                span = tracer.nextSpan().name("span " + i);
                Tracer.SpanInScope scope = tracer.withSpan(span);
                previous.close();
                previous = scope;
                span.end();
            }
            previous.close();
            return span.context();
        });

        // only the span that was current when the last span was created
        then(chainedTraceContexts(last)).isEqualTo(1);
    }

    @Test
    void should_not_chain_trace_contexts_when_outer_scope_is_closed_before_inner() throws Exception {
        // Closing the outer scope while the inner one is still current leaves the outer
        // context on the thread, so every iteration starts from the previous one
        TraceContext last = onFreshThread(() -> {
            TraceContext inner = closeOuterScopeBeforeInner(0);
            for (int i = 1; i < ITERATIONS; i++) {
                inner = closeOuterScopeBeforeInner(i);
            }
            return inner;
        });

        // only the outer span that was current when the last inner span was created
        then(chainedTraceContexts(last)).isEqualTo(1);
    }

    private TraceContext closeOuterScopeBeforeInner(int iteration) {
        Span outer = tracer.nextSpan().name("outer " + iteration);
        Tracer.SpanInScope outerScope = tracer.withSpan(outer);
        Span inner = tracer.nextSpan().name("inner " + iteration);
        Tracer.SpanInScope innerScope = tracer.withSpan(inner);
        outerScope.close();
        innerScope.close();
        inner.end();
        outer.end();
        return inner.context();
    }

    private void onFreshThread(Runnable body) throws Exception {
        executor.submit(body).get();
    }

    private <T> T onFreshThread(Callable<T> body) throws Exception {
        return executor.submit(body).get();
    }

    /**
     * Counts the trace contexts reachable from the given one. A trace context stores the
     * context that was current when its span was created, and that context holds the
     * trace context that was current at that time.
     */
    private static int chainedTraceContexts(TraceContext traceContext) {
        Set<TraceContext> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        OtelTraceContext current = (OtelTraceContext) traceContext;
        while (current != null && seen.add(current)) {
            current = current.context().get(OtelCurrentTraceContext.OTEL_CONTEXT_KEY);
        }
        return seen.size() - 1;
    }

}
