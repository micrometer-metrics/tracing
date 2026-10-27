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
package io.micrometer.tracing.test.tck;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Shared contracts for baggage propagation and properly nested baggage scopes. Both
 * {@code foo} and {@code bar} must be configured as remote baggage fields. The tracer
 * must create spans with valid, distinct identities. These tests do not require Spring,
 * Reactor, MDC correlation, or an external tracing backend.
 *
 * @author Tommy Ludwig
 * @since 1.8.0
 */
public abstract class BaggageCompatibilityKit {

    /**
     * Returns the tracer under test.
     * @return tracer under test
     */
    protected abstract Tracer getTracer();

    /**
     * Returns the propagator configured for the tracer under test.
     * @return propagator configured for the tracer under test
     */
    protected abstract Propagator getPropagator();

    @AfterEach
    void baggageScopesMustBeClosed() {
        then(getTracer().currentSpan()).as("Current span after test").isNull();
        then(getTracer().getAllBaggage()).as("Current baggage after test").isEmpty();
    }

    @Test
    void should_keep_extracted_baggage_after_nested_baggage_scope_was_closed() throws Exception {
        Span span = extractedSpanWithBaggage("foo", "extracted");
        try {
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                try (BaggageInScope baggage = getTracer().createBaggageInScope("bar", "nested")) {
                    then(getTracer().getBaggage("foo").get()).isEqualTo("extracted");
                    then(getTracer().getBaggage("bar").get()).isEqualTo("nested");
                }
                then(getTracer().getBaggage("foo").get()).isEqualTo("extracted");
                then(getTracer().getBaggage("bar").get()).isNull();
            }
            then(baggageFromAnotherThread(span.context())).containsEntry("foo", "extracted").doesNotContainKey("bar");
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                then(getTracer().getBaggage("foo").get()).isEqualTo("extracted");
                then(getTracer().getBaggage("bar").get()).isNull();
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_keep_extracted_baggage_after_nested_baggage_scope_for_same_key_was_closed() throws Exception {
        Span span = extractedSpanWithBaggage("foo", "extracted");
        try {
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                try (BaggageInScope baggage = getTracer().createBaggageInScope("foo", "nested")) {
                    then(getTracer().getBaggage("foo").get()).isEqualTo("nested");
                }
                then(getTracer().getBaggage("foo").get()).isEqualTo("extracted");
            }
            then(baggageFromAnotherThread(span.context())).containsEntry("foo", "extracted");
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                then(getTracer().getBaggage("foo").get()).isEqualTo("extracted");
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_restore_outer_baggage_of_same_trace_context_when_nested_baggage_scope_is_closed() throws Exception {
        Span span = getTracer().nextSpan().name("span").start();
        try {
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                thenNestedBaggageScopesAreRestored(span.context());
                then(getTracer().getBaggage("foo").get()).isNull();
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_restore_outer_baggage_of_trace_context_not_in_scope_when_nested_baggage_scope_is_closed()
            throws Exception {
        Span span = getTracer().nextSpan().name("span").start();
        try {
            thenNestedBaggageScopesAreRestored(span.context());
        }
        finally {
            span.end();
        }
    }

    private void thenNestedBaggageScopesAreRestored(TraceContext context) throws Exception {
        try (BaggageInScope outer = getTracer().createBaggageInScope(context, "foo", "outer")) {
            try (BaggageInScope inner = getTracer().createBaggageInScope(context, "foo", "inner")) {
                then(getTracer().getBaggage("foo").get(context)).isEqualTo("inner");
                then(baggageFromAnotherThread(context)).containsEntry("foo", "inner");
            }
            then(getTracer().getBaggage("foo").get(context)).isEqualTo("outer");
            then(baggageFromAnotherThread(context)).containsEntry("foo", "outer");
        }
        then(getTracer().getBaggage("foo").get(context)).isNull();
        then(baggageFromAnotherThread(context)).doesNotContainKey("foo");
    }

    @Test
    void should_restore_extracted_baggage_after_exception() throws Exception {
        Span span = extractedSpanWithBaggage("foo", "extracted");
        try {
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                assertThatIllegalStateException().isThrownBy(() -> {
                    try (BaggageInScope baggage = getTracer().createBaggageInScope("foo", "nested")) {
                        throw new IllegalStateException("application failure");
                    }
                });
                then(getTracer().getBaggage("foo").get()).isEqualTo("extracted");
                then(getTracer().currentSpan()).isEqualTo(span);
            }
            then(baggageFromAnotherThread(span.context())).containsEntry("foo", "extracted");
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_read_explicit_baggage_while_another_span_is_current() throws Exception {
        Span a = extractedSpanWithBaggage("foo", "a");
        Span b = extractedSpanWithBaggage("foo", "b");
        try {
            then(a.context().traceId()).isNotEqualTo(b.context().traceId());
            try (Tracer.SpanInScope scope = getTracer().withSpan(b);
                    BaggageInScope baggage = getTracer().createBaggageInScope("bar", "only-b")) {
                then(getTracer().getBaggage("foo").get()).isEqualTo("b");
                then(Objects.requireNonNull(getTracer().getBaggage(a.context(), "foo")).get()).isEqualTo("a");
                then(baggageFromAnotherThread(a.context())).containsEntry("foo", "a").doesNotContainKey("bar");
                then(getTracer().currentSpan()).isEqualTo(b);
            }
        }
        finally {
            b.end();
            a.end();
        }
    }

    @Test
    void should_preserve_current_span_when_scoping_baggage_for_another_span() throws Exception {
        Span a = extractedSpanWithBaggage("foo", "a");
        Span b = extractedSpanWithBaggage("foo", "b");
        try {
            try (Tracer.SpanInScope scope = getTracer().withSpan(b)) {
                try (BaggageInScope baggage = getTracer().createBaggageInScope(a.context(), "foo", "temporary")) {
                    then(getTracer().currentSpan()).isEqualTo(b);
                    then(baggageFromAnotherThread(a.context())).containsEntry("foo", "temporary");
                    then(baggageFromAnotherThread(b.context())).containsEntry("foo", "b");
                }
                then(getTracer().currentSpan()).isEqualTo(b);
                then(baggageFromAnotherThread(a.context())).containsEntry("foo", "a");
                then(baggageFromAnotherThread(b.context())).containsEntry("foo", "b");
            }
        }
        finally {
            b.end();
            a.end();
        }
    }

    /**
     * Round trips through the configured propagator, so the fixture works for either
     * bridge without manufacturing its implementation-specific trace context.
     * @param key remote baggage field
     * @param value baggage value
     * @return started span with extracted baggage; the caller must end it
     */
    protected final Span extractedSpanWithBaggage(String key, String value) {
        Map<String, String> carrier = new HashMap<>();
        Span remote = getTracer().nextSpan().name("remote").start();
        try {
            try (Tracer.SpanInScope scope = getTracer().withSpan(remote);
                    BaggageInScope baggage = getTracer().createBaggageInScope(key, value)) {
                getPropagator().inject(remote.context(), carrier,
                        (map, name, entry) -> Objects.requireNonNull(map).put(name, entry));
            }
        }
        finally {
            remote.end();
        }
        then(getTracer().currentSpan()).isNull();
        then(getTracer().getAllBaggage()).isEmpty();
        then(carrier).as("Trace and baggage must have been injected").containsValue(value);
        Span extracted = getPropagator().extract(carrier, Map::get).name("extracted").start();
        then(extracted.context().traceId()).isEqualTo(remote.context().traceId());
        then(extracted.context().spanId()).isNotEqualTo(remote.context().spanId());
        return extracted;
    }

    /**
     * Reads associated baggage without ambient baggage masking a missing stored value.
     * @param context explicit trace context
     * @return baggage visible through the explicit-context API on a fresh thread
     * @throws Exception if the read fails or times out
     */
    protected final Map<String, String> baggageFromAnotherThread(TraceContext context) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> {
                then(getTracer().currentSpan()).isNull();
                then(getTracer().getAllBaggage()).isEmpty();
                return getTracer().getAllBaggage(context);
            }).get(10, TimeUnit.SECONDS);
        }
        finally {
            executor.shutdownNow();
        }
    }

}
