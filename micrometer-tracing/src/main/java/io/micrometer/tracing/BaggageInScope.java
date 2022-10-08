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
package io.micrometer.tracing;

import io.micrometer.common.lang.Nullable;

import java.io.Closeable;

/**
 * Inspired by OpenZipkin Brave's {@code BaggageField}. Since some tracer implementations
 * require a scope to be wrapped around baggage, baggage must be closed so that the scope
 * does not leak. Some tracer implementations make baggage immutable (e.g. OpenTelemetry),
 * so when the value gets updated they might create new scope (others will return the same
 * one - e.g. OpenZipkin Brave).
 * <p>
 * Represents a single baggage entry.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface BaggageInScope extends Closeable {

    /**
     * A noop implementation.
     */
    BaggageInScope NOOP = new BaggageInScope() {
        @Override
        public String name() {
            return null;
        }

        @Override
        public String get() {
            return null;
        }

        @Override
        public String get(TraceContext traceContext) {
            return null;
        }

        @Override
        public BaggageInScope set(String value) {
            return this;
        }

        @Override
        public BaggageInScope set(TraceContext traceContext, String value) {
            return this;
        }

        @Override
        public BaggageInScope makeCurrent() {
            return this;
        }

        @Override
        public void close() {

        }
    };

    /**
     * @return name of the baggage entry
     */
    String name();

    /**
     * @return value of the baggage entry or {@code null} if not set.
     */
    @Nullable
    String get();

    /**
     * Retrieves baggage from the given {@link TraceContext}.
     * @param traceContext context containing baggage
     * @return value of the baggage entry or {@code null} if not set.
     */
    @Nullable
    String get(TraceContext traceContext);

    /**
     * Sets the baggage value.
     * @param value to set
     * @return new scope
     */
    BaggageInScope set(String value);

    /**
     * Sets the baggage value for the given {@link TraceContext}.
     * @param traceContext context containing baggage
     * @param value to set
     * @return new scope
     */
    BaggageInScope set(TraceContext traceContext, String value);

    /**
     * Sets the current baggage in scope.
     * @return this in scope
     */
    BaggageInScope makeCurrent();

    @Override
    void close();

}
