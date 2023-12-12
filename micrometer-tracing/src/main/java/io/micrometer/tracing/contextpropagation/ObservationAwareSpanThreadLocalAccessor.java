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

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.micrometer.tracing.handler.TracingObservationHandler;

import java.io.Closeable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
 * @author Taeik Lim
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

    private final ObservationRegistry registry;

    private final Tracer tracer;

    /**
     * Creates a new instance of {@link ObservationThreadLocalAccessor}.
     * @param tracer tracer
     */
    public ObservationAwareSpanThreadLocalAccessor(Tracer tracer) {
        this(ObservationRegistry.create(), tracer);
    }

    /**
     * Creates a new instance of {@link ObservationThreadLocalAccessor}.
     * @param observationRegistry observationRegistry
     * @param tracer tracer
     */
    public ObservationAwareSpanThreadLocalAccessor(ObservationRegistry observationRegistry, Tracer tracer) {
        this.registry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
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
                return new SpanWithBaggage(this.tracer, currentSpan);
            }
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return span;
        }
        return new SpanWithBaggage(this.tracer, span);
    }

    @Override
    public void setValue(Span value) {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        Tracer.SpanInScope scope;
        if (value instanceof SpanWithBaggage) {
            scope = this.tracer.withSpan(((SpanWithBaggage) value).delegate);
        }
        else {
            scope = this.tracer.withSpan(value);
        }
        SpanAction newSpanAction = new SpanAction(spanActions, spanAction);
        spanActions.put(Thread.currentThread(), newSpanAction);
        Consumer<?> consumer = null;
        if (value instanceof SpanWithBaggage) {
            consumer = createNewScopesForAllPreviouslyStoredBaggage(value, consumer, scope);
        }
        else {
            consumer = o -> scope.close();
        }
        newSpanAction.setScope(consumer);
    }

    private Consumer<?> createNewScopesForAllPreviouslyStoredBaggage(Span value, Consumer<?> consumer,
            SpanInScope scope) {
        TraceContext context = value.context();
        SpanWithBaggage spanWithBaggage = (SpanWithBaggage) value;
        Map<String, String> storedBaggage = spanWithBaggage.baggage;
        for (Entry<String, String> entry : storedBaggage.entrySet()) {
            consumer = appendBaggageScopeClosing(entry, context, consumer);
        }
        return appendSpanScopeClosing(consumer, scope);
    }

    private Consumer<?> appendBaggageScopeClosing(Entry<String, String> entry, TraceContext context,
            @Nullable Consumer<?> consumer) {
        String baggageKey = entry.getKey();
        String baggageValue = entry.getValue();
        BaggageInScope baggageInScope = this.tracer.createBaggageInScope(context, baggageKey, baggageValue);
        if (consumer == null) {
            // first pass
            return o -> baggageInScope.close();
        }
        return consumer.andThen(o -> baggageInScope.close());
    }

    private static Consumer<?> appendSpanScopeClosing(Consumer<?> consumer, SpanInScope scope) {
        if (consumer != null) {
            return consumer.andThen(o -> scope.close());
        }
        // no baggage was present
        return o -> scope.close();
    }

    @Override
    public void setValue() {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        if (spanAction == null) {
            return;
        }
        Tracer.SpanInScope scope = this.tracer.withSpan(null);
        Consumer<?> consumer = o -> scope.close();
        spanAction.setScope(consumer);
    }

    @Override
    public void restore(Span previousValue) {
        SpanAction spanAction = spanActions.get(Thread.currentThread());
        if (spanAction == null) {
            return;
        }
        spanAction.close();
        Span currentSpan = tracer.currentSpan();
        if (previousValue instanceof SpanWithBaggage) {
            previousValue = ((SpanWithBaggage) previousValue).delegate;
        }
        if (!(Objects.equals(previousValue, currentSpan))) {
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

        Consumer<?> scope;

        SpanAction(Map<Thread, SpanAction> spanActions, SpanAction previous) {
            this.previous = previous;
            this.todo = spanActions;
        }

        void setScope(Consumer<?> scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            if (this.scope != null) {
                this.scope.accept(null);
            }
            if (this.previous != null) {
                this.todo.put(Thread.currentThread(), this.previous);
            }
            else {
                this.todo.remove(Thread.currentThread());
            }
        }

    }

    static class SpanWithBaggage implements Span {

        private final Span delegate;

        final Map<String, String> baggage;

        SpanWithBaggage(Tracer tracer, @NonNull Span delegate) {
            this.delegate = delegate;
            this.baggage = tracer.getAllBaggage(delegate.context());
        }

        @Override
        public boolean isNoop() {
            return this.delegate.isNoop();
        }

        @Override
        public TraceContext context() {
            return this.delegate.context();
        }

        @Override
        public Span start() {
            return this.delegate.start();
        }

        @Override
        public Span name(String name) {
            return this.delegate.name(name);
        }

        @Override
        public Span event(String value) {
            return this.delegate.event(value);
        }

        @Override
        public Span event(String value, long time, TimeUnit timeUnit) {
            return this.delegate.event(value, time, timeUnit);
        }

        @Override
        public Span tag(String key, String value) {
            return this.delegate.tag(key, value);
        }

        @Override
        public Span tag(String key, long value) {
            return this.delegate.tag(key, value);
        }

        @Override
        public Span tag(String key, double value) {
            return this.delegate.tag(key, value);
        }

        @Override
        public Span tag(String key, boolean value) {
            return this.delegate.tag(key, value);
        }

        @Override
        public Span error(Throwable throwable) {
            return this.delegate.error(throwable);
        }

        @Override
        public void end() {
            this.delegate.end();
        }

        @Override
        public void end(long time, TimeUnit timeUnit) {
            this.delegate.end(time, timeUnit);
        }

        @Override
        public void abandon() {
            this.delegate.abandon();
        }

        @Override
        public Span remoteServiceName(String remoteServiceName) {
            return this.delegate.remoteServiceName(remoteServiceName);
        }

        @Override
        public Span remoteIpAndPort(String ip, int port) {
            return this.delegate.remoteIpAndPort(ip, port);
        }

    }

}
