/**
 * Copyright 2023 the original author or authors.
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
package io.micrometer.tracing.otel.handler;

import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static org.assertj.core.api.BDDAssertions.then;

class PropagatingReceiverTracingObservationHandlerOtelTests {

    InMemorySpanExporter testSpanProcessor = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(testSpanProcessor))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {
    }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.emptyList(), Collections.emptyList()));

    PropagatingReceiverTracingObservationHandler<ReceiverContext<?>> handler = new PropagatingReceiverTracingObservationHandler<>(
            tracer, new OtelPropagator(ContextPropagators.noop(), otelTracer));

    @Test
    void should_signal_events() {
        Event event = Event.of("foo", "bar");
        ReceiverContext<Object> receiverContext = new ReceiverContext<>((carrier, key) -> "val");
        receiverContext.setCarrier(new Object());
        receiverContext.setName("foo");

        handler.onStart(receiverContext);
        handler.onEvent(event, receiverContext);
        handler.onStop(receiverContext);

        SpanData data = takeOnlySpan();
        then(data.getEvents()).hasSize(1).element(0).extracting(EventData::getName).isEqualTo("bar");
    }

    @Test
    void should_set_remote_service_name() {
        ReceiverContext<Object> receiverContext = new ReceiverContext<>((carrier, key) -> "val");
        receiverContext.setCarrier(new Object());
        receiverContext.setName("foo");
        receiverContext.setRemoteServiceName("a-remote-service");

        handler.onStart(receiverContext);
        handler.onStop(receiverContext);

        SpanData data = takeOnlySpan();
        then(data.getAttributes().get(AttributeKey.stringKey("peer.service"))).isEqualTo("a-remote-service");
    }

    @Test
    void should_set_ip_and_port_name() {
        ReceiverContext<Object> receiverContext = new ReceiverContext<>((carrier, key) -> "val");
        receiverContext.setCarrier(new Object());
        receiverContext.setRemoteServiceAddress("http://127.0.0.1:1234");

        handler.onStart(receiverContext);
        handler.onStop(receiverContext);

        SpanData data = takeOnlySpan();
        then(data.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo("127.0.0.1");
        then(data.getAttributes().get(NETWORK_PEER_PORT)).isEqualTo(1234L);
    }

    private SpanData takeOnlySpan() {
        List<SpanData> spans = testSpanProcessor.getFinishedSpanItems();
        then(spans).hasSize(1);
        return testSpanProcessor.getFinishedSpanItems().get(0);
    }

}
