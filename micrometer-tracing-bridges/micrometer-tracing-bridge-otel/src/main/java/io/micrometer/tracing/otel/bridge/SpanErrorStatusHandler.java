/**
 * Copyright 2026 the original author or authors.
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
package io.micrometer.tracing.otel.bridge;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * Decides the {@link StatusCode status} to set on an OpenTelemetry {@link Span} when an
 * error is reported via {@link io.micrometer.tracing.Span#error(Throwable)},
 * {@link io.micrometer.tracing.ScopedSpan#error(Throwable)} or
 * {@link io.micrometer.tracing.Span.Builder#error(Throwable)}. The exception is always
 * recorded on the span (via {@link Span#recordException(Throwable)}) before this handler
 * runs; this handler is responsible only for the status.
 * <p>
 * The {@link #DEFAULT default} implementation always sets the status to
 * {@link StatusCode#ERROR}. A custom implementation may, for example, leave the status
 * unset for exceptions that are considered expected, in which case the span will be
 * marked {@link StatusCode#OK} when it ends.
 *
 * @since 1.8.0
 */
@FunctionalInterface
public interface SpanErrorStatusHandler {

    /**
     * Default handler reproducing the historical behavior: always set the status to
     * {@link StatusCode#ERROR}, using the throwable's message as the description when
     * present.
     */
    SpanErrorStatusHandler DEFAULT = (error, span) -> {
        if (error.getMessage() == null) {
            span.setStatus(StatusCode.ERROR);
        }
        else {
            span.setStatus(StatusCode.ERROR, error.getMessage());
        }
    };

    /**
     * Sets the status on the given span based on the given error.
     * @param error the error that was reported
     * @param span the OpenTelemetry span whose status should be set
     */
    void handle(Throwable error, Span span);

}
