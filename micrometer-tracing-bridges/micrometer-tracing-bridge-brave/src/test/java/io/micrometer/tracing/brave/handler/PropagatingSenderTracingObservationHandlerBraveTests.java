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
package io.micrometer.tracing.brave.handler;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.*;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.test.simple.SpanAssert;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("unchecked")
class PropagatingSenderTracingObservationHandlerBraveTests {

    TestSpanHandler testSpanHandler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(testSpanHandler).build();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
            new BraveBaggageManager());

    PropagatingSenderTracingObservationHandler<? super SenderContext<?>> handler = new PropagatingSenderTracingObservationHandler<>(
            tracer, new BravePropagator(tracing));

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
        registry.observationConfig()
            .observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(handler,
                    new DefaultTracingObservationHandler(tracer)));

        Observation parent = Observation.start("parent", registry);
        SenderContext<?> senderContext = new SenderContext<>((carrier, key, value) -> {
        });
        senderContext.setContextualName("HTTP GET");
        senderContext.setRemoteServiceName("remote service");
        senderContext.setRemoteServiceAddress("http://127.0.0.1:1234");
        Observation child = Observation.createNotStarted("child", () -> senderContext, registry)
            .parentObservation(parent)
            .start();

        child.stop();
        parent.stop();

        List<FinishedSpan> spans = testSpanHandler.spans()
            .stream()
            .map(BraveFinishedSpan::fromBrave)
            .collect(Collectors.toList());
        SpansAssert.then(spans).haveSameTraceId();
        FinishedSpan childFinishedSpan = spans.get(0);
        SpanAssert.then(childFinishedSpan)
            .hasNameEqualTo("HTTP GET")
            .hasRemoteServiceNameEqualTo("remote service")
            .hasIpEqualTo("127.0.0.1")
            .hasPortEqualTo(1234);
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

        MutableSpan data = takeOnlySpan();
        then(data.annotations()).hasSize(1);
        then(data.annotations().stream().findFirst()).isPresent().get().extracting(Map.Entry::getValue).isSameAs("bar");
    }

    private MutableSpan takeOnlySpan() {
        List<MutableSpan> spans = testSpanHandler.spans();
        then(spans).hasSize(1);
        MutableSpan mutableSpan = spans.get(0);
        return mutableSpan;
    }

}
