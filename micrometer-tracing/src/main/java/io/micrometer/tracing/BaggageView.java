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

import org.jspecify.annotations.Nullable;

/**
 * Inspired by OpenZipkin Brave's {@code BaggageField}. Represents a single, immutable
 * baggage entry.
 *
 * @author Marcin Grzejszczak
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public interface BaggageView {

    /**
     * A noop implementation.
     */
    BaggageView NOOP = new BaggageView() {
        @Override
        public String name() {
            return "no-op";
        }

        @Override
        public @Nullable String get() {
            return null;
        }

        @Override
        public @Nullable String get(TraceContext traceContext) {
            return null;
        }
    };

    /**
     * Retrieves baggage name.
     * @return name of the baggage entry
     */
    String name();

    /**
     * Retrieves baggage value.
     * @return value of the baggage entry or {@code null} if not set.
     */
    @Nullable String get();

    /**
     * Retrieves baggage value from the given {@link TraceContext}.
     * @param traceContext context containing baggage
     * @return value of the baggage entry or {@code null} if not set.
     */
    @Nullable String get(TraceContext traceContext);

}
