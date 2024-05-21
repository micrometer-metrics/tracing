/**
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.tracing.otel.bridge;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class OtelTraceContextTests {

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .build();

    OpenTelemetrySdkBuilder openTelemetrySdkBuilder = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider);

    @Test
    void should_return_null_when_parent_invalid() {

        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span span = otelTracer.spanBuilder("foo").startSpan();

            OtelTraceContext otelTraceContext = new OtelTraceContext(span);

            then(otelTraceContext.parentId()).isNull();
        }

    }

    @Test
    void should_return_parentid_when_parent_valid() {
        try (OpenTelemetrySdk openTelemetrySdk = openTelemetrySdkBuilder.build()) {
            Tracer otelTracer = tracer(openTelemetrySdk);
            Span parentSpan = otelTracer.spanBuilder("parent").startSpan();
            Span span = otelTracer.spanBuilder("foo")
                .setParent(parentSpan.storeInContext(Context.current()))
                .startSpan();

            OtelTraceContext otelTraceContext = new OtelTraceContext(span);

            then(otelTraceContext.parentId()).isEqualTo(parentSpan.getSpanContext().getSpanId());
        }

    }

    private static Tracer tracer(OpenTelemetrySdk openTelemetrySdk) {
        return openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");
    }

}
