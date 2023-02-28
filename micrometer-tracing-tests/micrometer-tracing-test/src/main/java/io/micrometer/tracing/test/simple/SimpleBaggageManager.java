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
package io.micrometer.tracing.test.simple;

import io.micrometer.common.lang.Nullable;
import io.micrometer.tracing.Baggage;
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

    private final Map<TraceContext, Set<SimpleBaggageInScope>> baggagesByContext = new ConcurrentHashMap<>();

    private final SimpleTracer simpleTracer;

    /**
     * Creates a new instance of {@link SimpleBaggageManager}.
     * @param simpleTracer simple tracer
     */
    public SimpleBaggageManager(SimpleTracer simpleTracer) {
        this.simpleTracer = simpleTracer;
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return this.baggagesByContext.values().stream().flatMap(Collection::stream)
                .collect(Collectors.toMap(Baggage::name, Baggage::get));
    }

    @Override
    public Baggage getBaggage(String name) {
        return getBaggage(simpleTracer.currentTraceContext().context(), name);
    }

    @Override
    public Baggage getBaggage(TraceContext traceContext, String name) {
        return baggageForName(traceContext, name);
    }

    @Nullable
    private SimpleBaggageInScope baggageForName(TraceContext traceContext, String name) {
        return this.baggagesByContext.getOrDefault(traceContext, Collections.emptySet()).stream()
                .filter(bag -> name.equalsIgnoreCase(bag.name())).findFirst().orElse(null);
    }

    @Override
    public Baggage createBaggage(String name) {
        return createSimpleBaggage(name);
    }

    private Baggage createSimpleBaggage(String name) {
        TraceContext current = simpleTracer.currentTraceContext().context();
        SimpleBaggageInScope baggage = baggageForName(current, name);
        if (baggage == null) {
            baggage = new SimpleBaggageInScope(name);
        }
        Set<SimpleBaggageInScope> baggages = this.baggagesByContext.getOrDefault(current, new HashSet<>());
        baggages.add(baggage);
        this.baggagesByContext.put(current, baggages);
        return baggage;
    }

    @Override
    public Baggage createBaggage(String name, String value) {
        Baggage baggage = createSimpleBaggage(name);
        baggage.set(value);
        return baggage;
    }

}
