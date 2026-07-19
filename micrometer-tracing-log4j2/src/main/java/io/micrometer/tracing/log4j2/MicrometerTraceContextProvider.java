/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.tracing.log4j2;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.logging.log4j.spi.TraceContextProvider;

/**
 * A Log4j 2 {@link TraceContextProvider} that extracts tracing metadata from the
 * Micrometer {@link Tracer}.
 * <p>
 * Because Log4j 2 instantiates this via the {@link java.util.ServiceLoader} mechanism
 * (which requires a no-arg constructor), the active {@link Tracer} must be supplied
 * statically during application startup (e.g., via Spring Boot Auto-Configuration).
 */
public class MicrometerTraceContextProvider implements TraceContextProvider {

    private static volatile Tracer tracer;

    /**
     * Sets the active Micrometer Tracer to be used by Log4j 2.
     *
     * @param activeTracer the tracer instance
     */
    public static void setTracer(Tracer activeTracer) {
        tracer = activeTracer;
    }

    @Override
    public String getTraceId() {
        if (tracer == null) {
            return null;
        }
        Span currentSpan = tracer.currentSpan();
        return (currentSpan != null && currentSpan.context() != null) ? currentSpan.context().traceId() : null;
    }

    @Override
    public String getSpanId() {
        if (tracer == null) {
            return null;
        }
        Span currentSpan = tracer.currentSpan();
        return (currentSpan != null && currentSpan.context() != null) ? currentSpan.context().spanId() : null;
    }

    @Override
    public String getTraceFlags() {
        // Micrometer's TraceContext does not natively expose W3C trace flags as a direct string.
        // We safely return null, allowing Log4j to default to empty string.
        return null;
    }
}
