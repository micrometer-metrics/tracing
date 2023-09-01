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

import java.io.Closeable;
import java.util.Map;

import brave.Tracing;
import brave.baggage.BaggageField;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.TraceContext;

/**
 * Brave implementation of a {@link BaggageManager}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveBaggageManager implements Closeable, BaggageManager {

    @Override
    public Map<String, String> getAllBaggage() {
        return BaggageField.getAllValues();
    }

    @Override
    public Map<String, String> getAllBaggage(TraceContext traceContext) {
        if (traceContext == null) {
            return getAllBaggage();
        }
        return BaggageField.getAllValues(BraveTraceContext.toBrave(traceContext));
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
        return new BraveBaggageInScope(baggageField, BraveTraceContext.toBrave(traceContext));
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name) {
        return baggage(name);
    }

    private BraveBaggageInScope baggage(String name, TraceContext traceContext) {
        return new BraveBaggageInScope(BaggageField.create(name), BraveTraceContext.toBrave(traceContext));
    }

    private BraveBaggageInScope baggage(String name) {
        return new BraveBaggageInScope(BaggageField.create(name), currentTraceContext());
    }

    // Taken from BraveField
    private static brave.propagation.TraceContext currentTraceContext() {
        Tracing tracing = Tracing.current();
        return tracing != null ? tracing.currentTraceContext().get() : null;
    }

    @Override
    @Deprecated
    public Baggage createBaggage(String name, String value) {
        return baggage(name).set(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return baggage(name).makeCurrent(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return baggage(name, traceContext).makeCurrent(traceContext, value);
    }

    @Override
    public void close() {
        // We used to cache baggage fields
    }

}
