/*
 * Copyright 2024 VMware, Inc.
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
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.micrometer.tracing.handler.TracingObservationHandler;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * {@link ThreadLocalAccessor} used to propagate baggage via {@link BaggageToPropagate}.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.2
 */
public class ObservationAwareBaggageThreadLocalAccessor implements ThreadLocalAccessor<BaggageToPropagate> {

    private static final InternalLogger log = InternalLoggerFactory
        .getInstance(ObservationAwareBaggageThreadLocalAccessor.class);

    final Map<Thread, BaggageAndScope> baggageInScope = new ConcurrentHashMap<>();

    /**
     * Key under which Micrometer Tracing Baggage accessor is being registered.
     */
    public static final String KEY = "micrometer.tracing.baggage";

    private final Tracer tracer;

    private final ObservationRegistry registry;

    /**
     * Creates a new instance of this class.
     * @param observationRegistry observation registry
     * @param tracer tracer
     * @since 1.2.2
     */
    public ObservationAwareBaggageThreadLocalAccessor(ObservationRegistry observationRegistry, Tracer tracer) {
        this.registry = observationRegistry;
        this.tracer = tracer;
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public BaggageToPropagate getValue() {
        Observation currentObservation = registry.getCurrentObservation();
        Span currentSpan = getCurrentSpan(currentObservation);
        Map<String, String> baggage = currentSpan != null ? tracer.getAllBaggage(currentSpan.context())
                : tracer.getAllBaggage();
        if (log.isTraceEnabled()) {
            log.trace("Current baggage in scope in thread local [" + baggageInScope.get(Thread.currentThread())
                    + "], current baggage from tracer [" + baggage + "]");
        }
        return (baggage == null || baggage.isEmpty()) ? null : new BaggageToPropagate(baggage);
    }

    private Span getCurrentSpan(Observation currentObservation) {
        Span currentSpan = tracer.currentSpan();
        if (currentObservation != null) {
            // There's a current observation so OTLA hooked in
            // we will now check if the user created spans manually or not
            TracingObservationHandler.TracingContext tracingContext = currentObservation.getContext()
                .getOrDefault(TracingObservationHandler.TracingContext.class,
                        new TracingObservationHandler.TracingContext());
            // If there is a span in ThreadLocal and it's the same one as the one from a
            // tracing handler
            // then OTLA did its job and we should back off
            if (currentSpan != null && !currentSpan.equals(tracingContext.getSpan())) {
                // User created child spans manually and scoped them
                // the current span is not the same as the one from observation
                if (log.isTraceEnabled()) {
                    log.trace("User created child spans manually and scoped them, returning [" + currentSpan + "]");
                }
                return currentSpan;
            }
            if (log.isTraceEnabled()) {
                log.trace("Span created by OTLA, picking one from context [" + tracingContext.getSpan() + "]");
            }
            return tracingContext.getSpan();
        }
        else if (log.isTraceEnabled()) {
            log.trace("No span created by OTLA, retrieving current span from tracer [" + currentSpan + "]");
        }
        return currentSpan;
    }

    @Override
    public void setValue(BaggageToPropagate value) {
        BaggageAndScope previousScope = baggageInScope.get(Thread.currentThread());
        if (log.isTraceEnabled()) {
            log.trace("Baggage to set [" + value + "]. Previous scope [" + previousScope + "]");
        }
        BaggageAndScope scope = null;
        Map<String, String> storedMap = value.getBaggage();
        Set<Entry<String, String>> entries = storedMap.entrySet();
        Span span = tracer.currentSpan();
        if (span == null) {
            log.warn("There is no span to which we can attach baggage, will not set baggage");
            return;
        }
        scope = openScopeForEachBaggageEntry(entries, span, scope);
        baggageInScope.put(Thread.currentThread(), scopeRestoringBaggageAndScope(scope, previousScope));
        if (log.isTraceEnabled()) {
            log.trace("Finished setting value [" + baggageInScope.get(Thread.currentThread()) + "]");
        }
    }

    private BaggageAndScope openScopeForEachBaggageEntry(Set<Entry<String, String>> entries, Span span,
            BaggageAndScope scope) {
        for (Entry<String, String> entry : entries) {
            if (log.isTraceEnabled()) {
                Baggage baggage = tracer.getBaggage(span.context(), entry.getKey());
                String previousBaggage = baggage != null ? baggage.get() : null;
                log.trace("Current span [" + span + "], previous baggage [" + previousBaggage + "]");
            }
            BaggageInScope baggage = tracer.createBaggageInScope(span.context(), entry.getKey(), entry.getValue());
            if (log.isTraceEnabled()) {
                Baggage currentBaggage = tracer.getBaggage(span.context(), entry.getKey());
                String previousBaggage = currentBaggage != null ? currentBaggage.get() : null;
                log.trace("New baggage [" + baggage + " ] hashcode [" + baggage.hashCode() + "]. Entry to set ["
                        + entry.getValue() + "] already stored value [" + previousBaggage + "]");
            }
            scope = baggageScopeClosingScope(entry, scope, baggage);
        }
        return scope;
    }

    private static BaggageAndScope baggageScopeClosingScope(Entry<String, String> entry, BaggageAndScope scope,
            BaggageInScope baggage) {
        if (scope == null) {
            return scopeClosingBaggageAndScope(entry, baggage);
        }

        return scopeClosingBaggageAndScope(entry, baggage).andThen(scope);
    }

    private static BaggageAndScope scopeClosingBaggageAndScope(Entry<String, String> entry, BaggageInScope baggage) {
        return new BaggageAndScope(bs -> {
            if (log.isTraceEnabled()) {
                log.trace("Closing baggage [" + baggage + "] hashcode [" + baggage.hashCode() + "]");
            }
            baggage.close();
        }, entry);
    }

    @Override
    public void setValue() {
        BaggageAndScope previousScope = baggageInScope.get(Thread.currentThread());
        if (log.isTraceEnabled()) {
            log.trace("setValue to empty baggage scope, current baggage scope [" + previousScope + "]");
        }
        if (previousScope == null) {
            if (log.isTraceEnabled()) {
                log.trace("No action to perform");
            }
            return;
        }
        SpanInScope spanInScope = tracer.withSpan(null);
        BaggageAndScope currentScope = new BaggageAndScope(o -> spanInScope.close());
        baggageInScope.put(Thread.currentThread(), scopeRestoringBaggageAndScope(currentScope, previousScope));
        if (log.isTraceEnabled()) {
            log.trace("setValue no args finished, current baggage scope [" + baggageInScope.get(Thread.currentThread())
                    + "]");
        }
    }

    private BaggageAndScope scopeRestoringBaggageAndScope(BaggageAndScope currentScope, BaggageAndScope previousScope) {
        return currentScope.andThen(o -> {
            if (previousScope != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Putting previous scope [" + previousScope + "]");
                }
                baggageInScope.put(Thread.currentThread(), previousScope);
            }
            else {
                if (log.isTraceEnabled()) {
                    log.trace("No previous scope to put, clearing the thread [" + Thread.currentThread() + "]");
                }
                baggageInScope.remove(Thread.currentThread());
            }
        });
    }

    private void closeCurrentScope() {
        BaggageAndScope scope = baggageInScope.get(Thread.currentThread());
        if (log.isTraceEnabled()) {
            log.trace("Before close scope [" + scope + "]");
        }
        if (scope != null) {
            scope.accept(null);
            if (log.isTraceEnabled()) {
                log.trace("After close scope [" + baggageInScope.get(Thread.currentThread()) + "]");
            }
        }
        else if (log.isTraceEnabled()) {
            log.trace("No action to perform");
        }
    }

    @Override
    public void restore() {
        if (log.isTraceEnabled()) {
            log.trace("Restoring to empty baggage scope");
        }
        closeCurrentScope();
    }

    @Override
    public void restore(BaggageToPropagate value) {
        if (log.isTraceEnabled()) {
            log.trace("Calling restore(value)");
        }
        closeCurrentScope();
    }

    @Override
    @Deprecated
    public void reset() {
        if (log.isTraceEnabled()) {
            log.trace("Calling reset()");
        }
        ThreadLocalAccessor.super.reset();
    }

    static class BaggageAndScope implements Consumer<Object> {

        private static final InternalLogger log = InternalLoggerFactory.getInstance(BaggageInScope.class);

        private final Consumer<Object> consumer;

        private final Entry<String, String> entry;

        BaggageAndScope(Consumer<Object> consumer, Entry<String, String> entry) {
            this.consumer = consumer;
            this.entry = entry;
        }

        BaggageAndScope(Consumer<Object> consumer) {
            this.consumer = consumer;
            this.entry = null;
        }

        @Override
        public String toString() {
            return "BaggageAndScope{" + "consumer=" + consumer + ", entry=" + entry + '}';
        }

        @Override
        public void accept(Object o) {
            if (log.isTraceEnabled()) {
                log.trace("Accepting consumer [" + this + "]");
            }
            this.consumer.accept(o);
        }

        @Override
        public BaggageAndScope andThen(Consumer<? super Object> after) {
            return new BaggageAndScope(Consumer.super.andThen(after), entry);
        }

    }

}
