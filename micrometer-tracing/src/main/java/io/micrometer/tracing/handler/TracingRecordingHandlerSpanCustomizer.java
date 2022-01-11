/*
 * Copyright 2021-2021 the original author or authors.
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

package io.micrometer.tracing.handler;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.Nullable;
import io.micrometer.tracing.Span;

import java.time.Duration;

/**
 * Allows additional span customization before reporting.
 *
 * @param <T> type of handler context
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public interface TracingRecordingHandlerSpanCustomizer<T extends Timer.HandlerContext> {

    /**
     * @param handlerContext handler context, may be {@code null}
     * @return {@code true} when this handler context is supported
     */
    boolean supportsContext(@Nullable Timer.HandlerContext handlerContext);

    /**
     * Customizes the span when an error occurs.
     *
     * @param span      span to customize
     * @param sample    current sample
     * @param context   attached handler context
     * @param throwable thrown exception
     */
    default void customizeSpanOnError(Span span, Timer.Sample sample, @Nullable T context, Throwable throwable) {

    }

    /**
     * Customizes the span before its reporting.
     *
     * @param span     span to customize
     * @param sample   current sample
     * @param context  attached handler context
     * @param timer    corresponding timer
     * @param duration duration of the metric
     */
    default void customizeSpanOnStop(Span span, Timer.Sample sample, @Nullable T context, Timer timer, Duration duration) {

    }
}
