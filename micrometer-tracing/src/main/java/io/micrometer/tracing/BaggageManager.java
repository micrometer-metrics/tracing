/**
 * Copyright 2024 the original author or authors.
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
package io.micrometer.tracing;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Manages {@link Baggage} entries. Upon retrieval / creation of a baggage entry puts it
 * in scope. Scope must be closed.
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface BaggageManager {

    /**
     * A noop implementation.
     */
    BaggageManager NOOP = new BaggageManager() {
        @Override
        public Map<String, String> getAllBaggage() {
            return Collections.emptyMap();
        }

        @Override
        public Baggage getBaggage(String name) {
            return Baggage.NOOP;
        }

        @Override
        public Baggage getBaggage(TraceContext traceContext, String name) {
            return Baggage.NOOP;
        }

        @Override
        public Baggage createBaggage(String name) {
            return Baggage.NOOP;
        }

        @Override
        public Baggage createBaggage(String name, String value) {
            return Baggage.NOOP;
        }

        @Override
        public BaggageInScope createBaggageInScope(String name, String value) {
            return BaggageInScope.NOOP;
        }

        @Override
        public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
            return BaggageInScope.NOOP;
        }
    };

    /**
     * @return mapping of all baggage entries from the given scope
     */
    Map<String, String> getAllBaggage();

    /**
     * @param traceContext trace context with baggage. If {@code null} will try to get all
     * baggage from current available context
     * @return mapping of all baggage entries from the given scope
     */
    default Map<String, String> getAllBaggage(@Nullable TraceContext traceContext) {
        if (traceContext == null) {
            return getAllBaggage();
        }
        return Collections.emptyMap();
    }

    /**
     * Retrieves {@link Baggage} for the given name.
     * @param name baggage name
     * @return baggage if present or creates a new one if missing with {@code null} value
     */
    Baggage getBaggage(String name);

    /**
     * Retrieves {@link Baggage} for the given name.
     * @param traceContext trace context with baggage attached to it
     * @param name baggage name
     * @return baggage or {@code null} if not present on the {@link TraceContext}
     */
    @Nullable Baggage getBaggage(TraceContext traceContext, String name);

    /**
     * Creates a new {@link Baggage} entry for the given name or returns an existing one
     * if it's already present.
     * @param name baggage name
     * @return new or already created baggage
     * @deprecated use {@link BaggageManager#createBaggageInScope(String, String)}
     */
    @Deprecated
    Baggage createBaggage(String name);

    /**
     * Creates a new {@link Baggage} entry for the given name or returns an existing one
     * if it's already present.
     * @param name baggage name
     * @param value baggage value
     * @return new or already created baggage
     * @deprecated use {@link BaggageManager#createBaggageInScope(String, String)}
     */
    @Deprecated
    Baggage createBaggage(String name, String value);

    /**
     * Creates a new {@link Baggage} entry, sets a value on it and puts it in scope.
     * @param name baggage name
     * @param value baggage value
     * @return baggage with value
     */
    default BaggageInScope createBaggageInScope(String name, String value) {
        return createBaggage(name).makeCurrent(value);
    }

    /**
     * Creates a new {@link Baggage} entry, sets a value on it and puts it in scope.
     * @param traceContext trace context with baggage attached to it
     * @param name baggage name
     * @param value baggage value
     * @return baggage with value
     */
    default BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return createBaggage(name).makeCurrent(traceContext, value);
    }

    /**
     * Returns all names of baggage fields.
     * @return baggage fields
     * @since 1.3.0
     */
    default List<String> getBaggageFields() {
        return Collections.emptyList();
    }

}
