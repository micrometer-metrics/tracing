/**
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.handler;

import io.micrometer.common.KeyValue;
import org.jspecify.annotations.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationView;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.SpanNameUtil;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Marker interface for tracing handlers.
 *
 * @param <T> type of handler context
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface TracingObservationHandler<T extends Observation.Context> extends ObservationHandler<T> {

    /**
     * Tags the span.
     * @param context handler context
     * @param span span to tag
     */
    default void tagSpan(T context, Span span) {
        for (KeyValue keyValue : context.getAllKeyValues()) {
            if (!keyValue.getKey().equalsIgnoreCase("ERROR")) {
                span.tag(keyValue.getKey(), keyValue.getValue());
            }
            else {
                span.error(new RuntimeException(keyValue.getValue()));
            }
        }
    }

    /**
     * Get the span name.
     * @param context handler context
     * @return name for the span
     */
    default String getSpanName(T context) {
        String name = context.getName();
        if (StringUtils.isNotBlank(context.getContextualName())) {
            name = context.getContextualName();
        }
        return SpanNameUtil.toLowerHyphen(name);
    }

    /**
     * Puts the span in scope.
     * @param context recording with context containing scope
     */
    @Override
    default void onScopeOpened(T context) {
        TracingContext tracingContext = getTracingContext(context);
        Span span = tracingContext.getSpan();
        setMaybeScopeOnTracingContext(tracingContext, span);
    }

    /**
     * Creates or reuses an existing {@link CurrentTraceContext.Scope} given the
     * {@link Span}. {@link Span} can be {@code null} in which a scope that resets current
     * scope but remembers the previously present value will be created.
     * @param tracingContext handler's tracing context
     * @param newSpan span, whose context will be used to create a new scope. Span can be
     * {@code null}.
     * @since 1.0.4
     */
    default void setMaybeScopeOnTracingContext(TracingContext tracingContext, @Nullable Span newSpan) {
        Span spanFromThisObservation = tracingContext.getSpan();
        TraceContext newContext = newSpan != null ? newSpan.context() : null;
        CurrentTraceContext.Scope scope = getTracer().currentTraceContext().maybeScope(newContext);
        CurrentTraceContext.Scope previousScopeOnThisObservation = tracingContext.getScope();
        RevertingScope revertingScope = new RevertingScope(tracingContext, scope, previousScopeOnThisObservation);
        revertingScope = RevertingScope.maybeWithBaggage(getTracer(), tracingContext, newContext, revertingScope,
                previousScopeOnThisObservation);
        tracingContext.setSpanAndScope(spanFromThisObservation, revertingScope);
    }

    @Override
    default void onScopeReset(T context) {
        TracingContext tracingContext = getTracingContext(context);
        CurrentTraceContext.Scope scope = tracingContext.getScope();
        while (scope != null) {
            scope.close();
            scope = tracingContext.getScope();
        }
        getTracer().currentTraceContext().maybeScope(null);
    }

    @Override
    default void onEvent(Event event, T context) {
        long timestamp = event.getWallTime();
        if (timestamp == 0) {
            getRequiredSpan(context).event(event.getContextualName());
        }
        else {
            getRequiredSpan(context).event(event.getContextualName(), timestamp, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    default void onError(T context) {
        if (context.getError() != null) {
            getRequiredSpan(context).error(context.getError());
        }
    }

    /**
     * Cleans the scope present in the context.
     * @param context recording with context containing scope
     */
    @Override
    default void onScopeClosed(T context) {
        TracingContext tracingContext = getTracingContext(context);
        CurrentTraceContext.Scope scope = tracingContext.getScope();
        if (scope != null) {
            scope.close();
        }
    }

    /**
     * Get the current span from parent if applicable.
     * @param context a {@link Observation.ContextView}
     * @return parent span or {@code null} when there's none
     */
    @Nullable default Span getParentSpan(Observation.ContextView context) {
        // This would mean that the user has manually created a tracing context
        TracingContext tracingContext = context.get(TracingContext.class);
        Span currentSpan = getTracer().currentSpan();
        if (tracingContext == null) {
            ObservationView observation = context.getParentObservation();
            if (observation != null) {
                tracingContext = observation.getContextView().get(TracingContext.class);
                if (tracingContext != null) {
                    Span spanFromParentObservation = tracingContext.getSpan();
                    if (spanFromParentObservation == null && currentSpan != null) {
                        return currentSpan;
                    }
                    else if (currentSpan != null && !currentSpan.equals(spanFromParentObservation)) {
                        // User manually created a span
                        return currentSpan;
                    }
                    // No manually created span
                    return spanFromParentObservation;
                }
            }
        }
        else {
            return tracingContext.getSpan();
        }
        return null;
    }

    /**
     * Get the current tracing context and updates the context if it's missing.
     * @param context a {@link Observation.Context}
     * @return tracing context
     */
    default TracingContext getTracingContext(T context) {
        TracingContext tracingContext = context.computeIfAbsent(TracingContext.class, clazz -> new TracingContext());
        tracingContext.setContext(context);
        return tracingContext;
    }

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context != null;
    }

    /**
     * Returns the span from the context or throws an exception if it's not there.
     * @param context context
     * @return span or exception
     */
    default Span getRequiredSpan(T context) {
        Span span = getTracingContext(context).getSpan();
        if (span == null) {
            throw new IllegalStateException("Span wasn't started - an observation must be started (not only created)");
        }
        return span;
    }

    /**
     * Ends span and clears resources.
     * @param context context
     * @param span span to end
     */
    default void endSpan(T context, Span span) {
        TracingContext tracingContext = getTracingContext(context);
        tracingContext.close();
        span.end();
    }

    /**
     * Returns the {@link Tracer}.
     * @return tracer
     */
    Tracer getTracer();

    /**
     * Basic tracing context.
     *
     * @author Marcin Grzejszczak
     * @since 1.0.0
     */
    class TracingContext implements AutoCloseable {

        private Span span;

        private Map<Thread, CurrentTraceContext.Scope> scopes = new ConcurrentHashMap<>();

        private Observation.ContextView context;

        /**
         * Returns the span.
         * @return span
         */
        public Span getSpan() {
            return this.span;
        }

        /**
         * Sets the span.
         * @param span span to set
         */
        public void setSpan(Span span) {
            this.span = span;
        }

        /**
         * Returns the scope of the span.
         * @return scope of the span
         */
        public CurrentTraceContext.Scope getScope() {
            return this.scopes.get(Thread.currentThread());
        }

        /**
         * Sets the current trace context scope.
         * @param scope scope to set
         */
        public void setScope(CurrentTraceContext.Scope scope) {
            if (scope == null) {
                this.scopes.remove(Thread.currentThread());
            }
            else {
                this.scopes.put(Thread.currentThread(), scope);
            }
        }

        /**
         * Returns the baggage corresponding to this span.
         * @return baggage attached to the span
         * @deprecated scheduled for removal in 1.5.0
         */
        @Deprecated
        public Map<String, String> getBaggage() {
            return Collections.emptyMap();
        }

        /**
         * Sets the baggage
         * @param baggage baggage to set
         * @deprecated scheduled for removal in 1.5.0
         */
        @Deprecated
        public void setBaggage(Map<String, String> baggage) {
        }

        /**
         * Convenience method to set both span and scope.
         * @param span span to set
         * @param scope scope to set
         */
        public void setSpanAndScope(Span span, CurrentTraceContext.Scope scope) {
            setSpan(span);
            setScope(scope);
        }

        @Override
        public void close() {

        }

        @Override
        public String toString() {
            return "TracingContext{" + "span=" + traceContextFromSpan() + '}';
        }

        private String traceContextFromSpan() {
            if (span != null) {
                return span.context().toString();
            }
            return "null";
        }

        void setContext(Observation.ContextView context) {
            this.context = context;
        }

        Observation.@Nullable ContextView getContext() {
            return this.context;
        }

    }

}
