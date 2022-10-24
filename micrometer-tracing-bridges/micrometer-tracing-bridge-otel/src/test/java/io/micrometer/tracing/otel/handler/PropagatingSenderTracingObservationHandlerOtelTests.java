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
package io.micrometer.tracing.otel.handler;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.*;
import io.micrometer.tracing.test.simple.SpanAssert;
import io.micrometer.tracing.test.simple.SpansAssert;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("unchecked")
class PropagatingSenderTracingObservationHandlerOtelTests {

    ArrayListSpanProcessor testSpanProcessor = new ArrayListSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
            .addSpanProcessor(SimpleSpanProcessor.create(testSpanProcessor)).build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {
    }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.emptyList(), Collections.emptyList()));

    PropagatingSenderTracingObservationHandler<? super SenderContext<?>> handler = new PropagatingSenderTracingObservationHandler<>(
            tracer, new OtelPropagator(ContextPropagators.noop(), otelTracer));

    @Test
    void should_be_applicable_for_non_null_context() {
        then(handler.supportsContext(new SenderContext<>((carrier, key, value) -> {
        }))).isTrue();
    }

    @Test
    void should_not_be_applicable_for_null_context() {
        then(handler.supportsContext(null)).isFalse();
    }

    @Test
    void should_create_a_child_span_when_parent_was_present() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                handler, new DefaultTracingObservationHandler(tracer)));

        Observation parent = Observation.start("parent", registry);
        SenderContext<?> senderContext = new SenderContext<>((carrier, key, value) -> {
        });
        senderContext.setContextualName("HTTP GET");
        senderContext.setRemoteServiceName("remote service");
        senderContext.setRemoteServiceAddress("http://127.0.0.1:1234");
        Observation child = Observation.createNotStarted("child", () -> senderContext, registry)
                .parentObservation(parent).start();

        child.stop();
        parent.stop();

        List<FinishedSpan> spans = testSpanProcessor.spans().stream().map(OtelFinishedSpan::fromOtel)
                .collect(Collectors.toList());
        SpansAssert.then(spans).haveSameTraceId();
        FinishedSpan childFinishedSpan = spans.get(0);
        SpanAssert.then(childFinishedSpan).hasNameEqualTo("HTTP GET").hasRemoteServiceNameEqualTo("remote service")
                .hasIpEqualTo("127.0.0.1").hasPortEqualTo(1234);
        FinishedSpan parentFinishedSpan = spans.get(1);
        SpanAssert.then(parentFinishedSpan).hasNameEqualTo("parent");

        then(childFinishedSpan.getParentId()).isEqualTo(parentFinishedSpan.getSpanId());
    }

    @Test
    void should_signal_events() {
        Event event = Event.of("foo", "bar");
        SenderContext<?> senderContext = new SenderContext<>((carrier, key, value) -> {
            // no-op
        });
        senderContext.setName("foo");

        handler.onStart(senderContext);
        handler.onEvent(event, senderContext);
        handler.onStop(senderContext);

        SpanData data = takeOnlySpan();
        then(data.getEvents()).hasSize(1).element(0).extracting(EventData::getName).isEqualTo("bar");
    }

    private SpanData takeOnlySpan() {
        Queue<SpanData> spans = testSpanProcessor.spans();
        then(spans).hasSize(1);
        return testSpanProcessor.takeLocalSpan();
    }

}
