/*
 * Copyright 2021-2021 the original author or authors.
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

package io.micrometer.tracing.handler;

import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.instrument.observation.ObservationHandler;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.SpanNameUtil;

/**
 * Marker interface for tracing handlers.
 *
 * @author Marcin Grzejszczak
 * @param <T> type of handler context
 * @since 1.0.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public interface TracingObservationHandler<T extends Observation.Context>
        extends ObservationHandler<T> {

    /**
     * Tags the span.
     *
     * @param context handler context
     * @param span span to tag
     */
    default void tagSpan(T context, Span span) {
        for (Tag tag : context.getAllTags()) {
            if (!tag.getKey().equalsIgnoreCase("ERROR")) {
                span.tag(tag.getKey(), tag.getValue());
            }
            else {
                // TODO: Does this make sense?
                span.error(new RuntimeException(tag.getValue()));
            }
        }
    }

    /**
     * Get the span name.
     *
     * @param context handler context
     * @return name for the span
     */
    default String getSpanName(T context) {
        return SpanNameUtil.toLowerHyphen(context.getName());
    }

    /**
     * Puts the span in scope.
     *
     * @param context recording with context containing scope
     */
    @Override
    default void onScopeOpened(T context) {
        Span span = getTracingContext(context).getSpan();
        if (span == null) {
            return;
        }
        CurrentTraceContext.Scope scope = getTracer().currentTraceContext()
                .maybeScope(span.context());
        getTracingContext(context).setSpanAndScope(span, scope);
    }

    /**
     * Cleans the scope present in the context.
     *
     * @param context recording with context containing scope
     */
    @Override
    default void onScopeClosed(T context) {
        TracingContext tracingContext = getTracingContext(context);
        tracingContext.getScope().close();
    }

    /**
     * Get the current tracing context.
     *
     * @param context a {@link Observation.Context}
     * @return tracing context
     */
    default TracingContext getTracingContext(T context) {
        // maybe consider returning a null ?
        return context.computeIfAbsent(TracingContext.class,
                clazz -> new TracingContext());
    }

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context != null;
    }

    /**
     * Returns the {@link Tracer}.
     *
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
         *
         * @return span
         */
        public Span getSpan() {
            return this.span;
        }

        /**
         * Sets the span.
         *
         * @param span span to set
         */
        public void setSpan(Span span) {
            this.span = span;
        }

        /**
         * Returns the scope of the span.
         *
         * @return scope of the span
         */
        public CurrentTraceContext.Scope getScope() {
            return this.scope;
        }

        /**
         * Sets the current trace context scope.
         *
         * @param scope scope to set
         */
        public void setScope(CurrentTraceContext.Scope scope) {
            this.scope = scope;
        }

        /**
         * Convenience method to set both span and scope.
         *
         * @param span span to set
         * @param scope scope to set
         */
        public void setSpanAndScope(Span span, CurrentTraceContext.Scope scope) {
            setSpan(span);
            setScope(scope);
        }

    }

}
