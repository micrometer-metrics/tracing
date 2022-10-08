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

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of a baggage in scope.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleBaggageInScope implements BaggageInScope {

    private final SimpleBaggageManager.Baggage baggage;

    private volatile boolean inScope;

    private volatile boolean closed;

    /**
     * Creates a new instance of {@link SimpleBaggageInScope}.
     * @param baggage baggage
     */
    public SimpleBaggageInScope(SimpleBaggageManager.Baggage baggage) {
        this.baggage = baggage;
    }

    @Override
    public String name() {
        return this.baggage.getName();
    }

    @Override
    public String get() {
        return this.baggage.getValue();
    }

    @Override
    public String get(TraceContext traceContext) {
        return baggage.getValue();
    }

    @Override
    public BaggageInScope set(String value) {
        this.baggage.setValue(value);
        return this;
    }

    @Override
    public BaggageInScope set(TraceContext traceContext, String value) {
        this.baggage.setValue(value);
        return this;
    }

    @Override
    public BaggageInScope makeCurrent() {
        this.inScope = true;
        return this;
    }

    @Override
    public void close() {
        this.inScope = false;
        this.closed = true;
    }

    /**
     * Checks if baggage is in scope.
     * @return {@code true} when baggage in scope
     */
    public boolean isInScope() {
        return inScope;
    }

    /**
     * Checks if baggage scope was closed.
     * @return {@code true} when baggage was closed
     */
    public boolean isClosed() {
        return closed;
    }

}
