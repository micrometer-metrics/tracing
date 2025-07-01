/**
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.tracing.test.simple;

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.TraceContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A test implementation of a baggage manager.
 *
 * @author Marcin Grzejszczak
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class SimpleBaggageManager implements BaggageManager {

    private final Map<TraceContext, ThreadLocal<Set<SimpleBaggageInScope>>> baggagesByContext = new ConcurrentHashMap<>();

    private final SimpleTracer simpleTracer;

    private final List<String> remoteFields;

    /**
     * Creates a new instance of {@link SimpleBaggageManager}.
     * @param simpleTracer simple tracer
     */
    public SimpleBaggageManager(SimpleTracer simpleTracer) {
        this.simpleTracer = simpleTracer;
        this.remoteFields = Collections.emptyList();
    }

    /**
     * Creates a new instance of {@link SimpleBaggageManager}.
     * @param simpleTracer simple tracer
     * @param remoteFields fields of baggage keys that should be propagated over the wire
     */
    public SimpleBaggageManager(SimpleTracer simpleTracer, List<String> remoteFields) {
        this.simpleTracer = simpleTracer;
        this.remoteFields = remoteFields;
    }

    @Override
    public Map<String, String> getAllBaggage() {
        TraceContext context = simpleTracer.currentTraceContext().context();
        if (context == null) {
            return Collections.emptyMap();
        }
        return getAllBaggageForCtx(context);
    }

    Map<String, String> getAllBaggageForCtx(TraceContext context) {
        Map<String, String> map = this.baggagesByContext
            .getOrDefault(context, ThreadLocal.withInitial(Collections::emptySet))
            .get()
            .stream()
            .filter(s -> s.get(context) != null)
            .collect(Collectors.toMap(Baggage::name, baggage -> baggage.get(context)));
        map.putAll(((SimpleTraceContext) context).baggageFromParent());
        return map;
    }

    @Override
    public Baggage getBaggage(String name) {
        TraceContext context = simpleTracer.currentTraceContext().context();
        Baggage baggage = context == null ? null : getBaggage(context, name);
        return baggageOrNoop(baggage);
    }

    @Override
    public Baggage getBaggage(TraceContext traceContext, String name) {
        SimpleBaggageInScope simpleBaggageInScope = baggageForName(traceContext, name);
        return baggageOrNoop(simpleBaggageInScope);
    }

    private static Baggage baggageOrNoop(@Nullable Baggage baggage) {
        if (baggage == null) {
            return Baggage.NOOP;
        }
        return baggage;
    }

    private @Nullable SimpleBaggageInScope baggageForName(@Nullable TraceContext traceContext, String name) {
        if (traceContext == null) {
            return null;
        }
        return this.baggagesByContext.getOrDefault(traceContext, ThreadLocal.withInitial(Collections::emptySet))
            .get()
            .stream()
            .filter(bag -> name.equalsIgnoreCase(bag.name()))
            .findFirst()
            .orElse(null);
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name) {
        return createSimpleBaggage(name);
    }

    // TODO figure out how to fix later
    @SuppressWarnings("NullAway")
    private Baggage createSimpleBaggage(String name) {
        TraceContext current = simpleTracer.currentTraceContext().context();
        SimpleBaggageInScope baggage = baggageForName(current, name);
        if (baggage == null) {
            ThreadLocal<Set<SimpleBaggageInScope>> baggages = this.baggagesByContext.getOrDefault(current,
                    ThreadLocal.withInitial(HashSet::new));
            baggage = new SimpleBaggageInScope(simpleTracer.currentTraceContext(), name, current);
            baggages.get().add(baggage);
            this.baggagesByContext.put(current, baggages);
        }
        return baggage;
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name, String value) {
        Baggage baggage = createSimpleBaggage(name);
        baggage.set(value);
        return baggage;
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return createSimpleBaggage(name).makeCurrent(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return createSimpleBaggage(name).makeCurrent(traceContext, value);
    }

    @Override
    public Map<String, String> getAllBaggage(@Nullable TraceContext traceContext) {
        if (traceContext == null) {
            return getAllBaggage();
        }
        return getAllBaggageForCtx(traceContext);
    }

    @Override
    public List<String> getBaggageFields() {
        return this.remoteFields;
    }

}
