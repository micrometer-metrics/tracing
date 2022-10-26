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
package io.micrometer.tracing.brave.bridge;

import brave.baggage.BaggageField;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.TraceContext;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brave implementation of a {@link BaggageManager}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveBaggageManager implements Closeable, BaggageManager {

    private static final Map<String, Baggage> CACHE = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> getAllBaggage() {
        return BaggageField.getAllValues();
    }

    @Override
    public Baggage getBaggage(String name) {
        return createBaggage(name);
    }

    @Override
    public Baggage getBaggage(TraceContext traceContext, String name) {
        BaggageField baggageField = BaggageField.getByName(BraveTraceContext.toBrave(traceContext), name);
        if (baggageField == null) {
            return null;
        }
        return new BraveBaggageInScope(baggageField);
    }

    @Override
    public Baggage createBaggage(String name) {
        return CACHE.computeIfAbsent(name, s -> new BraveBaggageInScope(BaggageField.create(s)));
    }

    @Override
    public Baggage createBaggage(String name, String value) {
        return createBaggage(name).set(value);
    }

    @Override
    public void close() {
        CACHE.clear();
    }

}
