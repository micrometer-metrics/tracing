/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.tracing.brave.bridge;

import java.util.Map;

import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.docs.AssertingSpan;

/**
 * Brave implementation of a {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveTracer implements Tracer {

    private final brave.Tracer tracer;

    private final brave.propagation.CurrentTraceContext currentTraceContext;

    private final io.micrometer.tracing.brave.bridge.BraveBaggageManager braveBaggageManager;

    /**
     * @param tracer Brave delegate
     * @param currentTraceContext Brave current trace context
     * @param braveBaggageManager baggage manager
     */
    public BraveTracer(brave.Tracer tracer, brave.propagation.CurrentTraceContext currentTraceContext,
            io.micrometer.tracing.brave.bridge.BraveBaggageManager braveBaggageManager) {
        this.tracer = tracer;
        this.currentTraceContext = currentTraceContext;
        this.braveBaggageManager = braveBaggageManager;
    }

    @Override
    public Span nextSpan(Span parent) {
        if (parent == null) {
            return nextSpan();
        }
        brave.propagation.TraceContext context = (((io.micrometer.tracing.brave.bridge.BraveTraceContext) parent.context()).traceContext);
        if (context == null) {
            return null;
        }
        return new io.micrometer.tracing.brave.bridge.BraveSpan(this.tracer.nextSpan(TraceContextOrSamplingFlags.create(context)));
    }

    @Override
    public SpanInScope withSpan(Span span) {
        return new BraveSpanInScope(
                tracer.withSpanInScope(span == null ? null : ((io.micrometer.tracing.brave.bridge.BraveSpan) AssertingSpan.unwrap(span)).delegate));
    }

    @Override
    public SpanCustomizer currentSpanCustomizer() {
        return new io.micrometer.tracing.brave.bridge.BraveSpanCustomizer(this.tracer.currentSpanCustomizer());
    }

    @Override
    public Span currentSpan() {
        brave.Span currentSpan = this.tracer.currentSpan();
        if (currentSpan == null) {
            return null;
        }
        return new io.micrometer.tracing.brave.bridge.BraveSpan(currentSpan);
    }

    @Override
    public Span nextSpan() {
        return new io.micrometer.tracing.brave.bridge.BraveSpan(this.tracer.nextSpan());
    }

    @Override
    public ScopedSpan startScopedSpan(String name) {
        return new io.micrometer.tracing.brave.bridge.BraveScopedSpan(this.tracer.startScopedSpan(name));
    }

    @Override
    public Span.Builder spanBuilder() {
        return new io.micrometer.tracing.brave.bridge.BraveSpanBuilder(this.tracer);
    }

    @Override
    public TraceContext.Builder traceContextBuilder() {
        return new io.micrometer.tracing.brave.bridge.BraveTraceContextBuilder();
    }

    @Override
    public CurrentTraceContext currentTraceContext() {
        return new io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext(this.currentTraceContext);
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return this.braveBaggageManager.getAllBaggage();
    }

    @Override
    public BaggageInScope getBaggage(String name) {
        return this.braveBaggageManager.getBaggage(name);
    }

    @Override
    public BaggageInScope getBaggage(TraceContext traceContext, String name) {
        return this.braveBaggageManager.getBaggage(traceContext, name);
    }

    @Override
    public BaggageInScope createBaggage(String name) {
        return this.braveBaggageManager.createBaggage(name);
    }

    @Override
    public BaggageInScope createBaggage(String name, String value) {
        return this.braveBaggageManager.createBaggage(name).set(value);
    }

}

class BraveSpanInScope implements Tracer.SpanInScope {

    final brave.Tracer.SpanInScope delegate;

    BraveSpanInScope(brave.Tracer.SpanInScope delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() {
        this.delegate.close();
    }

}
