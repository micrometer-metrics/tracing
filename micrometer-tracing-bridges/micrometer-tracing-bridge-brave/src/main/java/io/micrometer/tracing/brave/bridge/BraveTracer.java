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

import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.tracing.*;

import java.util.Map;

/**
 * Brave implementation of a {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveTracer implements Tracer {

    private final brave.Tracer tracer;

    private final BaggageManager braveBaggageManager;

    private final CurrentTraceContext currentTraceContext;

    /**
     * Creates a new instance of {@link BraveTracer}.
     * @param tracer Brave Tracer
     * @param context Brave context
     * @param braveBaggageManager Brave baggage manager
     */
    public BraveTracer(brave.Tracer tracer, CurrentTraceContext context, BaggageManager braveBaggageManager) {
        this.tracer = tracer;
        this.braveBaggageManager = braveBaggageManager;
        this.currentTraceContext = context;
    }

    /**
     * Creates a new instance of {@link BraveTracer} with no baggage support.
     * @param tracer Brave Tracer
     * @param context Brave context
     */
    public BraveTracer(brave.Tracer tracer, CurrentTraceContext context) {
        this(tracer, context, NOOP);
    }

    @Override
    public Span nextSpan(Span parent) {
        if (parent == null) {
            return nextSpan();
        }
        brave.propagation.TraceContext context = (((BraveTraceContext) parent.context()).traceContext);
        if (context == null) {
            return null;
        }
        return new BraveSpan(this.tracer.nextSpan(TraceContextOrSamplingFlags.create(context)));
    }

    @Override
    public SpanInScope withSpan(Span span) {
        return new BraveSpanInScope(tracer.withSpanInScope(span == null ? null : ((BraveSpan) span).delegate));
    }

    @Override
    public SpanCustomizer currentSpanCustomizer() {
        return new BraveSpanCustomizer(this.tracer.currentSpanCustomizer());
    }

    @Override
    public Span currentSpan() {
        brave.Span currentSpan = this.tracer.currentSpan();
        if (currentSpan == null) {
            return null;
        }
        return new BraveSpan(currentSpan);
    }

    @Override
    public Span nextSpan() {
        return new BraveSpan(this.tracer.nextSpan());
    }

    @Override
    public ScopedSpan startScopedSpan(String name) {
        return new BraveScopedSpan(this.tracer.startScopedSpan(name));
    }

    @Override
    public Span.Builder spanBuilder() {
        return new BraveSpanBuilder(this.tracer);
    }

    @Override
    public TraceContext.Builder traceContextBuilder() {
        return new BraveTraceContextBuilder();
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return this.braveBaggageManager.getAllBaggage();
    }

    @Override
    public Baggage getBaggage(String name) {
        return this.braveBaggageManager.getBaggage(name);
    }

    @Override
    public Baggage getBaggage(TraceContext traceContext, String name) {
        return this.braveBaggageManager.getBaggage(traceContext, name);
    }

    @Override
    public Baggage createBaggage(String name) {
        return this.braveBaggageManager.createBaggage(name);
    }

    @Override
    public Baggage createBaggage(String name, String value) {
        return this.braveBaggageManager.createBaggage(name).set(value);
    }

    @Override
    public CurrentTraceContext currentTraceContext() {
        return this.currentTraceContext;
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
