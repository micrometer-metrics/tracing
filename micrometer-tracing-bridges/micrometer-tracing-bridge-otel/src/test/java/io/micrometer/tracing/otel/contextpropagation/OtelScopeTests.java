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
package io.micrometer.tracing.otel.contextpropagation;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.test.tck.ScopeCompatibilityKit;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;

import java.util.Collections;

import static org.assertj.core.api.BDDAssertions.then;

@NullMarked
class OtelScopeTests extends ScopeCompatibilityKit {

    InMemorySpanExporter testSpanProcessor = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(this.testSpanProcessor))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(this.sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = this.openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    Tracer tracer = new OtelTracer(this.otelTracer, new OtelCurrentTraceContext(), event -> {
    }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.singletonList("foo"),
            Collections.emptyList()));

    @Override
    protected Tracer getTracer() {
        return this.tracer;
    }

    @AfterEach
    void ensureScopesNotLeaking() {
        try {
            then(Context.current()).as("All scopes must be closed").isSameAs(Context.root());
        }
        finally {
            this.sdkTracerProvider.close();
        }
    }

}
