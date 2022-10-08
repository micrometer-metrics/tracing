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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of a baggage manager.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleBaggageManager implements BaggageManager {

    private final Map<TraceContext, Set<Baggage>> baggage = new ConcurrentHashMap<>();

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
        return this.baggage.values().stream().flatMap(Collection::stream)
                .collect(Collectors.toMap(bag -> bag.name, bag -> bag.value));
    }

    @Override
    public BaggageInScope getBaggage(String name) {
        return getBaggage(simpleTracer.currentTraceContext().context(), name);
    }

    private Baggage baggageForName(String name, Set<Baggage> baggages) {
        return baggages.stream().filter(bag -> name.equalsIgnoreCase(bag.name)).findFirst().orElse(null);
    }

    @Override
    public BaggageInScope getBaggage(TraceContext traceContext, String name) {
        Baggage baggage = baggageForName(traceContext, name);
        if (baggage == null) {
            return null;
        }
        return new SimpleBaggageInScope(baggage);
    }

    private Baggage baggageForName(TraceContext traceContext, String name) {
        Set<Baggage> baggages = this.baggage.get(traceContext);
        return baggageForName(name, baggages);
    }

    @Override
    public BaggageInScope createBaggage(String name) {
        Baggage bag = createdBaggage(name);
        return new SimpleBaggageInScope(bag);
    }

    private Baggage createdBaggage(String name) {
        TraceContext current = simpleTracer.currentTraceContext().context();
        Baggage bag = baggageForName(current, name);
        if (bag == null) {
            bag = new Baggage(name);
        }
        Set<Baggage> baggages = this.baggage.getOrDefault(current, new HashSet<>());
        baggages.add(bag);
        this.baggage.put(current, baggages);
        return bag;
    }

    @Override
    public BaggageInScope createBaggage(String name, String value) {
        Baggage bag = createdBaggage(name);
        bag.setValue(value);
        return new SimpleBaggageInScope(bag);
    }

    static class Baggage {

        private final String name;

        private volatile String value = null;

        Baggage(String name) {
            this.name = name;
        }

        String getName() {
            return this.name;
        }

        String getValue() {
            return this.value;
        }

        void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Baggage baggage = (Baggage) o;
            return Objects.equals(name, baggage.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

    }

}
