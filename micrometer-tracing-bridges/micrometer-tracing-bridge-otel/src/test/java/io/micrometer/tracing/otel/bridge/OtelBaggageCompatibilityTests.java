/*
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

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.propagation.BaggageTextMapPropagator;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.tck.BaggageCompatibilityKit;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.BDDAssertions.then;

class OtelBaggageCompatibilityTests extends BaggageCompatibilityKit {

    private final OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();

    private final OtelBaggageManager baggageManager = new OtelBaggageManager(this.currentTraceContext,
            Arrays.asList("foo", "bar"), Collections.emptyList());

    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
            .build())
        .setPropagators(ContextPropagators.create(
                TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance(),
                        new BaggageTextMapPropagator(Arrays.asList("foo", "bar"), this.baggageManager))))
        .build();

    private final Tracer tracer = new OtelTracer(this.sdk.getTracer("baggage-contracts"), this.currentTraceContext,
            event -> {
            }, this.baggageManager);

    @Override
    protected Tracer getTracer() {
        return this.tracer;
    }

    @Override
    protected Propagator getPropagator() {
        return new OtelPropagator(this.sdk.getPropagators(), this.sdk.getTracer("baggage-contracts"));
    }

    @AfterEach
    void close() {
        try {
            then(Context.current()).as("All native OTel scopes must be closed").isSameAs(Context.root());
        }
        finally {
            this.sdk.close();
        }
    }

}
