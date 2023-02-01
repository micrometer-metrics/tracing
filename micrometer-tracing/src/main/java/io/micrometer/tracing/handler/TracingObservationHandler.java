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
package io.micrometer.tracing.handler;

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationView;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.SpanNameUtil;

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
        if (span == null) {
            return;
        }
        CurrentTraceContext.Scope scope = getTracer().currentTraceContext().maybeScope(span.context());
        CurrentTraceContext.Scope previousScopeOnThisObservation = tracingContext.getScope();
        tracingContext.setSpanAndScope(span, () -> {
            scope.close();
            tracingContext.setScope(previousScopeOnThisObservation);
        });
    }

    @Override
    default void onScopeReset(T context) {
        getTracer().withSpan(null);
    }

    @Override
    default void onEvent(Event event, T context) {
        getRequiredSpan(context).event(event.getContextualName());
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
        tracingContext.getScope().close();
    }

    /**
     * Get the current span from parent if applicable.
     * @param context a {@link Observation.ContextView}
     * @return parent span or {@code null} when there's none
     */
    @Nullable
    default Span getParentSpan(Observation.ContextView context) {
        // This would mean that the user has manually created a tracing context
        TracingContext tracingContext = context.get(TracingContext.class);
        if (tracingContext == null) {
            ObservationView observation = context.getParentObservation();
            if (observation != null) {
                tracingContext = observation.getContextView().get(TracingContext.class);
                if (tracingContext != null) {
                    return tracingContext.getSpan();
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
    @NonNull
    default TracingContext getTracingContext(T context) {
        return context.computeIfAbsent(TracingContext.class, clazz -> new TracingContext());
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
    class TracingContext {

        private Span span;

        private CurrentTraceContext.Scope scope;

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
            return this.scope;
        }

        /**
         * Sets the current trace context scope.
         * @param scope scope to set
         */
        public void setScope(CurrentTraceContext.Scope scope) {
            this.scope = scope;
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
        public String toString() {
            return "TracingContext{" + "span=" + span + ", scope=" + scope + '}';
        }

    }

}
