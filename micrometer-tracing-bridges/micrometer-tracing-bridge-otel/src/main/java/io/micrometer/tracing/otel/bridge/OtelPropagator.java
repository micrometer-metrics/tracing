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
package io.micrometer.tracing.otel.bridge;

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenTelemetry implementation of a {@link Propagator}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelPropagator implements Propagator {

    private final TextMapPropagator propagator;

    private final Tracer tracer;

    /**
     * Creates a new instance of {@link OtelPropagator}.
     * @param propagation propagation
     * @param tracer tracer
     */
    public OtelPropagator(ContextPropagators propagation, Tracer tracer) {
        this.propagator = propagation.getTextMapPropagator();
        this.tracer = tracer;
    }

    @Override
    public List<String> fields() {
        return new ArrayList<>(this.propagator.fields());
    }

    @Override
    public <C> void inject(TraceContext traceContext, @Nullable C carrier, Setter<C> setter) {
        Context context = OtelTraceContext.toOtelContext(traceContext);
        this.propagator.inject(context, carrier, setter::set);
    }

    @Override
    public <C> Span.Builder extract(C carrier, Getter<C> getter) {
        Context extracted = this.propagator.extract(Context.current(), carrier, new TextMapGetter<C>() {
            @Override
            public Iterable<String> keys(C carrier) {
                return fields();
            }

            @Override
            public String get(@Nullable C carrier, String key) {
                return getter.get(carrier, key);
            }
        });
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContextOrNull(extracted);
        OtelTraceContext otelTraceContext = getOtelTraceContext(extracted, span);
        return OtelSpanBuilder.fromOtel(this.tracer).setParent(otelTraceContext);
    }

    private static OtelTraceContext getOtelTraceContext(Context extracted,
            io.opentelemetry.api.trace.@Nullable Span span) {
        if (span == null || span.equals(io.opentelemetry.api.trace.Span.getInvalid())) {
            io.opentelemetry.api.trace.Span invalid = io.opentelemetry.api.trace.Span.getInvalid();
            return new OtelTraceContext(extracted, invalid.getSpanContext(), invalid);
        }
        return new OtelTraceContext(extracted, span.getSpanContext(), span);
    }

}
