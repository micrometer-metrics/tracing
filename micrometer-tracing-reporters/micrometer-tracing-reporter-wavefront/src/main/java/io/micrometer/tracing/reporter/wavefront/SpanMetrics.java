/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.tracing.reporter.wavefront;

import java.util.concurrent.BlockingQueue;

/**
 * Reports metrics from {@link WavefrontSpanHandler}.
 *
 * @author Moritz Halbritter
 * @since 1.0.0
 */
public interface SpanMetrics {

    /**
     * Is called when a span has been dropped.
     */
    void reportDropped();

    /**
     * Is called when a span is received.
     */
    void reportReceived();

    /**
     * Is called when a span couldn't be sent.
     */
    void reportErrors();

    /**
     * Registers the size of the given {@code queue}.
     * @param queue queue which size should be registered
     */
    void registerQueueSize(BlockingQueue<?> queue);

    /**
     * Registers the remaining capacity of the given {@code queue}.
     * @param queue queue which remaining capacity should be registered
     */
    void registerQueueRemainingCapacity(BlockingQueue<?> queue);

    /**
     * No-op implementation.
     */
    SpanMetrics NOOP = new SpanMetrics() {
        @Override
        public void reportDropped() {
        }

        @Override
        public void reportReceived() {
        }

        @Override
        public void reportErrors() {
        }

        @Override
        public void registerQueueSize(BlockingQueue<?> queue) {
        }

        @Override
        public void registerQueueRemainingCapacity(BlockingQueue<?> queue) {
        }
    };

}
