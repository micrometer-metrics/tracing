/**
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Map;

/**
 * OpenTelemetry implementation of a {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelTracer implements Tracer {

    private final io.opentelemetry.api.trace.Tracer tracer;

    private final BaggageManager otelBaggageManager;

    private final OtelCurrentTraceContext otelCurrentTraceContext;

    private final EventPublisher publisher;

    /**
     * Creates a new instance of {@link OtelTracer}.
     * @param tracer tracer
     * @param otelCurrentTraceContext current trace context
     * @param publisher event publisher
     * @param otelBaggageManager baggage manager
     */
    public OtelTracer(io.opentelemetry.api.trace.Tracer tracer, OtelCurrentTraceContext otelCurrentTraceContext,
            EventPublisher publisher, BaggageManager otelBaggageManager) {
        this.tracer = tracer;
        this.publisher = publisher;
        this.otelBaggageManager = otelBaggageManager;
        this.otelCurrentTraceContext = otelCurrentTraceContext;
    }

    /**
     * Creates a new instance of {@link OtelTracer} with no baggage support.
     * @param tracer tracer
     * @param otelCurrentTraceContext current trace context
     * @param publisher event publisher
     */
    public OtelTracer(io.opentelemetry.api.trace.Tracer tracer, OtelCurrentTraceContext otelCurrentTraceContext,
            EventPublisher publisher) {
        this(tracer, otelCurrentTraceContext, publisher, NOOP);
    }

    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public Span nextSpan(@Nullable Span parent) {
        if (parent == null) {
            return nextSpan();
        }
        OtelSpan otelSpan = (OtelSpan) parent;
        Context otelContext = otelSpan.context().context();
        Scope scope = null;
        if (otelContext != null && Context.current() != otelContext) { // This shouldn't
                                                                       // happen
            scope = otelContext.makeCurrent();
        }
        try {
            return OtelSpan.fromOtel(this.tracer.spanBuilder("")
                .setParent(OtelTraceContext.toOtelContext(parent.context()))
                .startSpan());
        }
        finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    @Override
    public Tracer.SpanInScope withSpan(@Nullable Span span) {
        TraceContext traceContext = traceContext(span);
        CurrentTraceContext.Scope scope = this.otelCurrentTraceContext.maybeScope(traceContext);
        return new WrappedSpanInScope(scope);
    }

    private TraceContext traceContext(@Nullable Span span) {
        if (span == null) {
            // remove any existing span/baggage data from the current state of anything
            // that might be holding on to it.
            this.publisher.publishEvent(new EventPublishingContextWrapper.ScopeClosedEvent());
            return new OtelTraceContext(io.opentelemetry.api.trace.Span.getInvalid());
        }
        else if (span instanceof OtelSpan) {
            return span.context();
        }
        return new OtelTraceContext(io.opentelemetry.api.trace.Span.getInvalid());
    }

    @Override
    public SpanCustomizer currentSpanCustomizer() {
        return new OtelSpanCustomizer();
    }

    @Override
    public @Nullable Span currentSpan() {
        OtelTraceContext context = (OtelTraceContext) this.otelCurrentTraceContext.context();
        if (context != null && context.span != null) {
            if (io.opentelemetry.api.trace.Span.getInvalid().equals(context.span)) {
                return null;
            }
            return new OtelSpan(context);
        }
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        if (currentSpan == null || currentSpan.equals(io.opentelemetry.api.trace.Span.getInvalid())) {
            return null;
        }
        return new OtelSpan(currentSpan);
    }

    @Override
    public Span nextSpan() {
        return new OtelSpan(this.tracer.spanBuilder("").startSpan());
    }

    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public ScopedSpan startScopedSpan(String name) {
        io.opentelemetry.api.trace.Span span = this.tracer.spanBuilder(name).startSpan();
        return new OtelScopedSpan(span, span.makeCurrent());
    }

    @Override
    public Span.Builder spanBuilder() {
        return new OtelSpanBuilder(this.tracer);
    }

    @Override
    public TraceContext.Builder traceContextBuilder() {
        return new OtelTraceContextBuilder();
    }

    @Override
    public CurrentTraceContext currentTraceContext() {
        return this.otelCurrentTraceContext;
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return this.otelBaggageManager.getAllBaggage();
    }

    @Override
    public Map<String, String> getAllBaggage(@Nullable TraceContext traceContext) {
        return this.otelBaggageManager.getAllBaggage(traceContext);
    }

    @Override
    public @Nullable Baggage getBaggage(String name) {
        return this.otelBaggageManager.getBaggage(name);
    }

    @Override
    public @Nullable Baggage getBaggage(TraceContext traceContext, String name) {
        return this.otelBaggageManager.getBaggage(traceContext, name);
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name) {
        return this.otelBaggageManager.createBaggage(name);
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name, String value) {
        return this.otelBaggageManager.createBaggage(name, value);
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return this.otelBaggageManager.createBaggageInScope(name, value);
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return this.otelBaggageManager.createBaggageInScope(traceContext, name, value);
    }

    @Override
    public List<String> getBaggageFields() {
        return this.otelBaggageManager.getBaggageFields();
    }

    /**
     * Publisher of events.
     */
    public interface EventPublisher {

        /**
         * Publishes an event.
         * @param event event to publish
         */
        void publishEvent(Object event);

    }

    static class WrappedSpanInScope implements SpanInScope {

        final CurrentTraceContext.Scope scope;

        WrappedSpanInScope(CurrentTraceContext.Scope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            this.scope.close();
        }

    }

}
