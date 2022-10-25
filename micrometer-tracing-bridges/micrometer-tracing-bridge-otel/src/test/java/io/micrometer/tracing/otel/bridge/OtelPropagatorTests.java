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

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class OtelPropagatorTests {

    @Test
    void should_propagate_context_with_baggage() {
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn()).build();
        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();
        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");
        ContextPropagators contextPropagators = ContextPropagators.create(TextMapPropagator
                .composite(W3CBaggagePropagator.getInstance(), W3CTraceContextPropagator.getInstance()));
        OtelPropagator otelPropagator = new OtelPropagator(contextPropagators, otelTracer);
        OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", "00-3e425f2373d89640bde06e8285e7bf88-9a5fdefae3abb440-00");
        carrier.put("baggage", "foo=bar");
        Span.Builder extract = otelPropagator.extract(carrier, Map::get);

        Span span = extract.start();

        BaggageInScope baggage = new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(),
                Collections.emptyList()).getBaggage(span.context(), "foo");
        try {
            BDDAssertions.then(baggage.get(span.context())).isEqualTo("bar");
        }
        finally {
            baggage.close();
        }
    }

}
