/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("unchecked")
class PropagatingReceiverTracingObservationHandlerBraveTests {

    TestSpanHandler testSpanHandler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(testSpanHandler).build();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
            new BraveBaggageManager());

    PropagatingReceiverTracingObservationHandler<ReceiverContext<?>> handler = new PropagatingReceiverTracingObservationHandler<>(
            tracer, new BravePropagator(tracing));

    @Test
    void should_be_applicable_for_non_null_context() {
        then(handler.supportsContext(new ReceiverContext<>((carrier, key) -> null))).isTrue();
    }

    @Test
    void should_not_be_applicable_for_null_context() {
        then(handler.supportsContext(null)).isFalse();
    }

    @Test
    void should_signal_events() {
        Event event = Event.of("foo", "bar");
        ReceiverContext<Object> receiverContext = new ReceiverContext<>((carrier, key) -> "val");
        receiverContext.setCarrier(new Object());
        receiverContext.setName("foo");

        handler.onStart(receiverContext);
        handler.onEvent(event, receiverContext);
        handler.onStop(receiverContext);

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
