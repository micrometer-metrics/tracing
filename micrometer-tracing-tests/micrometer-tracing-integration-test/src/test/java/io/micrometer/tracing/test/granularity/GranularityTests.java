/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.tracing.test.granularity;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.otel.bridge.*;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn;

class GranularityTests {

    @Nested
    class BraveConfig extends AbstractGranularityTests {

        TestSpanHandler spanHandler = new TestSpanHandler();

        StrictCurrentTraceContext braveCurrentTraceContext = StrictCurrentTraceContext.create();

        CurrentTraceContext bridgeContext = new BraveCurrentTraceContext(this.braveCurrentTraceContext);

        Tracing tracing = Tracing.newBuilder()
            .currentTraceContext(this.braveCurrentTraceContext)
            .supportsJoin(false)
            .traceId128Bit(true)
            // For Baggage to work you need to provide a list of fields to propagate
            .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("tenant")))
                .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("tenant2")))
                .build())
            .sampler(Sampler.ALWAYS_SAMPLE)
            .addSpanHandler(this.spanHandler)
            .build();

        brave.Tracer braveTracer = this.tracing.tracer();

        BraveBaggageManager braveBaggageManager = new BraveBaggageManager();

        Tracer tracer = new BraveTracer(this.braveTracer, this.bridgeContext, this.braveBaggageManager);

        @Override
        Tracer getTracer() {
            return tracer;
        }

        @Override
        List<FinishedSpan> finishedSpans() {
            return this.spanHandler.spans().stream().map(BraveFinishedSpan::fromBrave).collect(Collectors.toList());
        }

        @AfterEach
        void close() {
            this.tracing.close();
            this.braveCurrentTraceContext.close();
            this.braveBaggageManager.close();
        }

    }

    @Nested
    class OTelConfig extends AbstractGranularityTests {

        ArrayListSpanProcessor spanExporter = new ArrayListSpanProcessor();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .setSampler(alwaysOn())
            .addSpanProcessor(spanExporter)
            .build();

        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
            .build();

        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracerProvider()
            .get("io.micrometer.micrometer-tracing");

        OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

        Slf4JEventListener slf4JEventListener = new Slf4JEventListener();

        Slf4JBaggageEventListener slf4JBaggageEventListener = new Slf4JBaggageEventListener(
                Arrays.asList("tenant", "tenant2"));

        OtelTracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
            slf4JEventListener.onEvent(event);
            slf4JBaggageEventListener.onEvent(event);
        }, new OtelBaggageManager(otelCurrentTraceContext, Arrays.asList("tenant", "tenant2"),
                Collections.emptyList()));

        @Override
        Tracer getTracer() {
            return tracer;
        }

        @Override
        List<FinishedSpan> finishedSpans() {
            return this.spanExporter.spans().stream().map(OtelFinishedSpan::fromOtel).collect(Collectors.toList());
        }

        @AfterEach
        void close() {
            this.sdkTracerProvider.close();
        }

    }

}
