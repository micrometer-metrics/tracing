/**
 * Copyright 2022 the original author or authors.
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
package io.micrometer.tracing.brave.bridge;

import brave.Span;
import brave.Tracing;
import brave.baggage.BaggageField;
import io.micrometer.common.lang.Nullable;
import io.micrometer.tracing.*;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Brave implementation of a {@link BaggageManager}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveBaggageManager implements Closeable, BaggageManager {

    private final List<String> tagFields;

    @Nullable
    private Tracer tracer;

    /**
     * Create an instance of {@link BraveBaggageManager}.
     * @param tagFields fields of baggage keys that should become tags on a span
     */
    public BraveBaggageManager(List<String> tagFields) {
        this.tagFields = tagFields;
    }

    /**
     * Create an instance of {@link BraveBaggageManager} that uses no tag fields (span
     * will not be tagged with baggage entries).
     */
    public BraveBaggageManager() {
        this.tagFields = Collections.emptyList();
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return BaggageField.getAllValues();
    }

    @Override
    public Map<String, String> getAllBaggage(TraceContext traceContext) {
        if (traceContext == null) {
            return getAllBaggage();
        }
        return BaggageField.getAllValues(BraveTraceContext.toBrave(traceContext));
    }

    @Override
    public Baggage getBaggage(String name) {
        return createBaggage(name);
    }

    @Override
    public Baggage getBaggage(TraceContext traceContext, String name) {
        BaggageField baggageField = BaggageField.getByName(BraveTraceContext.toBrave(traceContext), name);
        if (baggageField == null) {
            return null;
        }
        Span span = currentSpan();
        return new BraveBaggageInScope(baggageField, BraveTraceContext.toBrave(traceContext), span, this.tagFields);
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name) {
        return baggage(name);
    }

    private BraveBaggageInScope baggage(String name, TraceContext traceContext) {
        Span span = currentSpan();
        return new BraveBaggageInScope(BaggageField.create(name), BraveTraceContext.toBrave(traceContext), span,
                tagFields);
    }

    private BraveBaggageInScope baggage(String name) {
        Span span = currentSpan();
        return new BraveBaggageInScope(BaggageField.create(name), span != null ? span.context() : null, span,
                this.tagFields);
    }

    // Taken from BraveField
    private Span currentSpan() {
        if (tracer != null) {
            io.micrometer.tracing.Span span = tracer.currentSpan();
            return BraveSpan.toBrave(span);
        }
        Tracing tracing = Tracing.current();
        return tracing != null ? tracing.tracer().currentSpan() : null;
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name, String value) {
        return baggage(name).set(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return baggage(name).makeCurrent(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return baggage(name, traceContext).makeCurrent(traceContext, value);
    }

    @Override
    public void close() {
        // We used to cache baggage fields
    }

    void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

}
