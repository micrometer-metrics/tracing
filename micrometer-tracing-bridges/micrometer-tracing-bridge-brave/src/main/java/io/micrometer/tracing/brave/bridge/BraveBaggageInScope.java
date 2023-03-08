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
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.TraceContext;

/**
 * Brave implementation of a {@link BaggageInScope}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class BraveBaggageInScope implements Baggage, BaggageInScope {

    private final BaggageField delegate;

    BraveBaggageInScope(BaggageField delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return this.delegate.name();
    }

    @Override
    public String get() {
        return this.delegate.getValue();
    }

    @Override
    public String get(TraceContext traceContext) {
        return this.delegate.getValue(BraveTraceContext.toBrave(traceContext));
    }

    @Override
    @Deprecated
    public Baggage set(String value) {
        this.delegate.updateValue(value);
        return this;
    }

    @Override
    @Deprecated
    public Baggage set(TraceContext traceContext, String value) {
        this.delegate.updateValue(BraveTraceContext.toBrave(traceContext), value);
        return this;
    }

    @Override
    public BaggageInScope makeCurrent() {
        return this;
    }

    @Override
    public BaggageInScope makeCurrent(String value) {
        this.delegate.updateValue(value);
        return makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent(TraceContext traceContext, String value) {
        this.delegate.updateValue(BraveTraceContext.toBrave(traceContext), value);
        return makeCurrent();
    }

    @Override
    public void close() {
    }

}
