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

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * {@link ThreadLocalAccessor} used to propagate baggage via {@link BaggageToPropagate}.
 *
 * @since 1.2.2
 * @author Marcin Grzejszczak
 */
public class BaggageThreadLocalAccessor implements ThreadLocalAccessor<BaggageToPropagate> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(BaggageThreadLocalAccessor.class);

    final Map<Thread, BaggageAndScope> baggageInScope = new ConcurrentHashMap<>();

    /**
     * Key under which Micrometer Tracing Baggage accessor is being registered.
     */
    public static final String KEY = "micrometer.tracing.baggage";

    private final Tracer tracer;

    /**
     * Creates a new instance of this class.
     * @param tracer tracer
     * @since 1.2.2
     */
    public BaggageThreadLocalAccessor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public BaggageToPropagate getValue() {
        Map<String, String> baggage = tracer.getAllBaggage();
        if (log.isTraceEnabled()) {
            log.trace("Current baggage in thread local [" + baggageInScope.get(Thread.currentThread())
                    + "], current baggage from tracer [" + baggage + "]");
        }
        return (baggage == null || baggage.isEmpty()) ? null : new BaggageToPropagate(baggage);
    }

    @Override
    public void setValue(BaggageToPropagate value) {
        BaggageAndScope previousConsumer = baggageInScope.get(Thread.currentThread());
        if (log.isTraceEnabled()) {
            log.trace("Baggage to set [" + value + "]. Previous consumer [" + previousConsumer + "]");
        }
        BaggageAndScope consumer = null;
        Map<String, String> storedMap = value.getBaggage();
        Set<Entry<String, String>> entries = storedMap.entrySet();
        Span span = tracer.currentSpan();
        if (span == null) {
            log.warn("There is no span to which we can attach baggage, will not set baggage");
            return;
        }
        consumer = openConsumerForEachBaggageEntry(entries, span, consumer);
        baggageInScope.put(Thread.currentThread(), scopeRestoringBaggageAndScope(consumer, previousConsumer));
        if (log.isTraceEnabled()) {
            log.trace("Finished setting value [" + baggageInScope.get(Thread.currentThread()) + "]");
        }
    }

    private BaggageAndScope openConsumerForEachBaggageEntry(Set<Entry<String, String>> entries, Span span,
            BaggageAndScope consumer) {
        for (Entry<String, String> entry : entries) {
            String previousBaggage = tracer.getBaggage(entry.getKey()).get();
            if (log.isTraceEnabled()) {
                log.trace("Current span [" + span + "], previous baggage [" + previousBaggage + "]");
            }
            BaggageInScope baggage = tracer.createBaggageInScope(span.context(), entry.getKey(), entry.getValue());
            if (log.isTraceEnabled()) {
                String storedBaggage = tracer.getBaggage(entry.getKey()).get();
                log.trace("New baggage [" + baggage + " ] hashcode [" + baggage.hashCode() + "]. Entry to set ["
                        + entry.getValue() + "] already stored value [" + storedBaggage + "]");
            }
            consumer = baggageScopeClosingConsumer(entry, consumer, baggage);
        }
        return consumer;
    }

    @NonNull
    private static BaggageAndScope baggageScopeClosingConsumer(Entry<String, String> entry, BaggageAndScope consumer,
            BaggageInScope baggage) {
        if (consumer == null) {
            return scopeClosingBaggageAndScope(entry, baggage);
        }
        return consumer.andThen(scopeClosingBaggageAndScope(entry, baggage));
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
        BaggageAndScope previousConsumer = baggageInScope.get(Thread.currentThread());
        if (log.isTraceEnabled()) {
            log.trace("setValue no args, current baggage scope [" + baggageInScope.get(Thread.currentThread()) + "]");
        }
        SpanInScope spanInScope = tracer.withSpan(null);
        BaggageAndScope currentConsumer = new BaggageAndScope(o -> spanInScope.close());
        baggageInScope.put(Thread.currentThread(), scopeRestoringBaggageAndScope(currentConsumer, previousConsumer));
        if (log.isTraceEnabled()) {
            log.trace("setValue no args finished, current baggage scope [" + baggageInScope.get(Thread.currentThread())
                    + "]");
        }
    }

    private BaggageAndScope scopeRestoringBaggageAndScope(BaggageAndScope currentConsumer,
            BaggageAndScope previousConsumer) {
        return currentConsumer.andThen(o -> {
            if (previousConsumer != null) {
                baggageInScope.put(Thread.currentThread(), previousConsumer);
            }
            else {
                baggageInScope.remove(Thread.currentThread());
            }
        });
    }

    private void closeCurrentScope() {
        BaggageAndScope consumer = baggageInScope.get(Thread.currentThread());
        if (log.isTraceEnabled()) {
            log.trace("Before close scope [" + baggageInScope.get(Thread.currentThread()) + "]");
        }
        if (consumer != null) {
            consumer.accept(null);
        }
        if (log.isTraceEnabled()) {
            log.trace("After close scope [" + baggageInScope.get(Thread.currentThread()) + "]");
        }
    }

    @Override
    public void restore() {
        if (log.isTraceEnabled()) {
            log.trace("Calling restore()");
        }
        closeCurrentScope();
    }

    @Override
    public void restore(BaggageToPropagate value) {
        if (log.isTraceEnabled()) {
            log.trace("Calling restore(Map)");
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

        @NonNull
        @Override
        public BaggageAndScope andThen(@NonNull Consumer<? super Object> after) {
            return new BaggageAndScope(Consumer.super.andThen(after), entry);
        }

    }

}
