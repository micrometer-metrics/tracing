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

/**
 * Inspired by OpenZipkin Brave's {@code BaggageField}. Since some tracer implementations
 * require a scope to be wrapped around baggage, baggage must be closed so that the scope
 * does not leak, see {@link BaggageInScope}. Some tracer implementations make baggage
 * immutable (e.g. OpenTelemetry), so when the value gets updated they might create new
 * scope (others will return the same one - e.g. OpenZipkin Brave).
 * <p>
 * Represents a single mutable baggage entry.
 *
 * @author Marcin Grzejszczak
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public interface Baggage extends BaggageView {

    /**
     * A noop implementation.
     */
    Baggage NOOP = new Baggage() {
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
        public Baggage set(String value) {
            return this;
        }

        @Override
        public Baggage set(TraceContext traceContext, String value) {
            return this;
        }

        @Override
        public BaggageInScope makeCurrent() {
            return BaggageInScope.NOOP;
        }
    };

    /**
     * Sets the baggage value.
     * @param value to set
     * @return itself
     */
    Baggage set(String value);

    /**
     * Sets the baggage value for the given {@link TraceContext}.
     * @param traceContext context containing baggage
     * @param value to set
     * @return itself
     */
    Baggage set(TraceContext traceContext, String value);

    /**
     * Sets the current baggage in scope.
     * @return a {@link BaggageInScope} instance
     */
    BaggageInScope makeCurrent();

}
