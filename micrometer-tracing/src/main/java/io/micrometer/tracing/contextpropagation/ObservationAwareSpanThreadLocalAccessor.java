/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.tracing.contextpropagation;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ThreadLocalAccessor} to put and restore current {@link Span} depending on
 * whether {@link ObservationThreadLocalAccessor} did some work or not (if
 * {@link ObservationThreadLocalAccessor} opened a scope, then this class doesn't want to
 * create yet another span).
 * <p>
 * In essence logic of this class is as follows:
 *
 * <ul>
 * <li>If {@link ObservationThreadLocalAccessor} created a {@link Span} via the
 * {@link TracingObservationHandler} - do nothing</li>
 * <li>else - take care of the creation of a {@link Span} and putting it in thread
 * local</li>
 * </ul>
 *
 * <b>IMPORTANT</b>: {@link ObservationAwareSpanThreadLocalAccessor} must be registered
 * AFTER the {@link ObservationThreadLocalAccessor}. The easiest way to achieve that is to
 * call {@link ContextRegistry#registerThreadLocalAccessor(ThreadLocalAccessor)} manually.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.4
 */
public class ObservationAwareSpanThreadLocalAccessor implements ThreadLocalAccessor<Span> {

    private static final InternalLogger log = InternalLoggerFactory
        .getInstance(ObservationAwareSpanThreadLocalAccessor.class);

    final Map<Thread, SpanAction> spanActions = new ConcurrentHashMap<>();

    /**
     * Key under which Micrometer Tracing is being registered.
     */
    public static final String KEY = "micrometer.tracing";

    private final Tracer tracer;

    private static final ObservationRegistry registry = ObservationRegistry.create();

    /**
     * Creates a new instance of {@link ObservationThreadLocalAccessor}.
     * @param tracer tracer
     */
    public ObservationAwareSpanThreadLocalAccessor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Span getValue() {
        Observation currentObservation = registry.getCurrentObservation();
        if (currentObservation != null) {
            // There's a current observation so OTLA hooked in
            // we will now check if the user created spans manually or not
            TracingObservationHandler.TracingContext tracingContext = currentObservation.getContext()
                .getOrDefault(TracingObservationHandler.TracingContext.class,
                        new TracingObservationHandler.TracingContext());
            Span currentSpan = tracer.currentSpan();
            // If there is a span in ThreadLocal and it's the same one as the one from a
            // tracing handler
            // then OTLA did its job and we should back off
            if (currentSpan != null && !currentSpan.equals(tracingContext.getSpan())) {
                // User created child spans manually and scoped them
                // the current span is not the same as the one from observation
                return currentSpan;
            }
            return null;
        }
        return this.tracer.currentSpan();
    }

    @Override
    public void setValue(Span value) {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        Tracer.SpanInScope scope = this.tracer.withSpan(value);
        if (spanAction == null) {
            spanAction = new SpanAction(spanActions);
            spanActions.put(Thread.currentThread(), spanAction);
        }
        spanAction.setScope(scope);
    }

    @Override
    public void setValue() {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        if (spanAction == null) {
            return;
        }
        Tracer.SpanInScope scope = this.tracer.withSpan(null);
        spanAction.setScope(scope);
    }

    @Override
    public void restore(Span previousValue) {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        if (spanAction == null) {
            return;
        }
        spanAction.close();
        Span currentSpan = tracer.currentSpan();
        if (!(previousValue.equals(currentSpan))) {
            String msg = "After closing the scope, current span <" + currentSpan
                    + "> is not the same as the one to which you want to revert <" + previousValue
                    + ">. Most likely you've opened a scope and forgotten to close it";
            log.warn(msg);
            assert false : msg;
        }
    }

    @Override
    public void restore() {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        if (spanAction != null) {
            spanAction.close();
        }
    }

    static class SpanAction implements Closeable {

        final SpanAction previous;

        final Map<Thread, SpanAction> todo;

        Closeable scope;

        SpanAction(Map<Thread, SpanAction> spanActions) {
            this.previous = spanActions.get(Thread.currentThread());
            this.todo = spanActions;
        }

        void setScope(Closeable scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            if (this.scope != null) {
                try {
                    this.scope.close();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (this.previous != null) {
                this.todo.put(Thread.currentThread(), this.previous);
            }
            else {
                this.todo.remove(Thread.currentThread());
            }
        }

    }

}
