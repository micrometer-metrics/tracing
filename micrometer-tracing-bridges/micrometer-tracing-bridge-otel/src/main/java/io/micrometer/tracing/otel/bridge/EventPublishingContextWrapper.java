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

package io.micrometer.tracing.otel.bridge;

import java.util.function.Function;

import io.micrometer.core.lang.Nullable;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;

public final class EventPublishingContextWrapper implements Function<ContextStorage, ContextStorage> {

    private final OtelTracer.EventPublisher publisher;

    public EventPublishingContextWrapper(OtelTracer.EventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public ContextStorage apply(ContextStorage contextStorage) {
        return new ContextStorage() {
            @Override
            public io.opentelemetry.context.Scope attach(Context context) {
                Context currentContext = Context.current();
                io.opentelemetry.context.Scope scope = contextStorage.attach(context);
                if (scope == io.opentelemetry.context.Scope.noop()) {
                    return scope;
                }
                publisher.publishEvent(new ScopeAttachedEvent(context));
                return () -> {
                    scope.close();
                    publisher.publishEvent(new ScopeClosedEvent());
                    publisher.publishEvent(new ScopeRestoredEvent(currentContext));
                };
            }

            @Override
            public Context current() {
                return contextStorage.current();
            }
        };
    }

    public static class ScopeAttachedEvent {

        /**
         * Context corresponding to the attached scope. Might be {@code null}.
         */
        final Context context;

        /**
         * Create a new event.
         * @param context corresponding otel context
         */
        public ScopeAttachedEvent(@Nullable Context context) {
            this.context = context;
        }

        Span getSpan() {
            return Span.fromContextOrNull(context);
        }

        Baggage getBaggage() {
            return Baggage.fromContextOrNull(context);
        }

        @Override
        public String toString() {
            return "ScopeAttached{context: [span: " + getSpan() + "] [baggage: " + getBaggage() + "]}";
        }

    }

    public static class ScopeClosedEvent {

    }

    public static class ScopeRestoredEvent {

        /**
         * {@link Context} corresponding to the scope being restored. Might be
         * {@code null}.
         */
        final Context context;

        /**
         * Create a new event.
         * @param context corresponding otel context
         */
        public ScopeRestoredEvent(@Nullable Context context) {
            this.context = context;
        }

        Span getSpan() {
            return Span.fromContextOrNull(context);
        }

        Baggage getBaggage() {
            return Baggage.fromContextOrNull(context);
        }

        @Override
        public String toString() {
            return "ScopeRestored{context: [span: " + getSpan() + "] [baggage: " + getBaggage() + "]}";
        }

    }

}
