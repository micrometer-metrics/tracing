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

/**
 * Contains trace and span data.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface TraceContext {

    /**
     * A noop implementation.
     */
    TraceContext NOOP = new TraceContext() {
        @Override
        public String traceId() {
            return "";
        }

        @Override
        public String parentId() {
            return null;
        }

        @Override
        public String spanId() {
            return "";
        }

        @Override
        public Boolean sampled() {
            return null;
        }
    };

    /**
     * Trace id.
     * @return trace id of a span
     */
    String traceId();

    /**
     * Parent span id.
     * @return parent span id or {@code null} if one is not set
     */
    @Nullable
    String parentId();

    /**
     * Span id.
     * @return span id
     */
    String spanId();

    /**
     * @return {@code true} when sampled, {@code false} when not sampled and {@code null}
     * when sampling decision should be deferred
     */
    Boolean sampled();

    /**
     * Builder for {@link TraceContext}.
     *
     * @since 1.0.0
     */
    interface Builder {

        /**
         * A noop implementation.
         */
        Builder NOOP = new Builder() {
            @Override
            public Builder traceId(String traceId) {
                return this;
            }

            @Override
            public Builder parentId(String parentId) {
                return this;
            }

            @Override
            public Builder spanId(String spanId) {
                return this;
            }

            @Override
            public Builder sampled(Boolean sampled) {
                return this;
            }

            @Override
            public TraceContext build() {
                return TraceContext.NOOP;
            }
        };

        /**
         * Sets trace id on the trace context.
         * @param traceId trace id
         * @return this
         */
        TraceContext.Builder traceId(String traceId);

        /**
         * Sets parent id on the trace context.
         * @param parentId parent trace id
         * @return this
         */
        TraceContext.Builder parentId(String parentId);

        /**
         * Sets span id on the trace context.
         * @param spanId span id
         * @return this
         */
        TraceContext.Builder spanId(String spanId);

        /**
         * Sets sampled on the trace context.
         * @param sampled if span is sampled
         * @return this
         */
        TraceContext.Builder sampled(Boolean sampled);

        /**
         * Builds the trace context.
         * @return trace context
         */
        TraceContext build();

    }

}
