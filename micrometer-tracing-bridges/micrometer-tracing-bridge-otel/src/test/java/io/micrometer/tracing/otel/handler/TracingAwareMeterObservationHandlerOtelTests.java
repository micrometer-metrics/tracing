/**
 * Copyright 2023 the original author or authors.
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
package io.micrometer.tracing.otel.handler;

import io.micrometer.core.instrument.observation.MeterObservationHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.TracingAwareMeterObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that use {@link OtelTracer} for {@link TracingAwareMeterObservationHandler}.
 *
 * @author Jonatan Ivanov
 */
class TracingAwareMeterObservationHandlerOtelTests {

    private final SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
        .build();

    private final OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    private final io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk
        .getTracer("io.micrometer.micrometer-tracing");

    private final Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {
    }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.emptyList(), Collections.emptyList()));

    private final TestMeterObservationHandler delegate = new TestMeterObservationHandler(tracer);

    private final TracingAwareMeterObservationHandler<Observation.Context> handler = new TracingAwareMeterObservationHandler<>(
            delegate, tracer);

    @Test
    void delegateShouldGetTheCurrentSpanAtStop() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig()
            .observationHandler(new DefaultTracingObservationHandler(tracer))
            .observationHandler(handler);

        Span span;
        Observation observation = Observation.start("test", registry);
        try (Observation.Scope ignored = observation.openScope()) {
            span = tracer.currentSpan();
        }
        finally {
            observation.stop();
        }

        assertThat(delegate.getCurrentSpan()).isNotNull().isEqualTo(span);
    }

    static class TestMeterObservationHandler implements MeterObservationHandler<Observation.Context> {

        private final Tracer tracer;

        private Span currentSpan;

        TestMeterObservationHandler(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void onStop(Observation.Context context) {
            this.currentSpan = tracer.currentSpan();
        }

        Span getCurrentSpan() {
            return currentSpan;
        }

    }

}
