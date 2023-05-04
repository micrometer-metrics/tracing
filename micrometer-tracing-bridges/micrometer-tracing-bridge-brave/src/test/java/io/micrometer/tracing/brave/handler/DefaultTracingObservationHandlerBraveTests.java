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
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.test.simple.SpanAssert;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("unchecked")
class DefaultTracingObservationHandlerBraveTests {

    TestSpanHandler testSpanHandler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(testSpanHandler).build();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
            new BraveBaggageManager());

    DefaultTracingObservationHandler handler = new DefaultTracingObservationHandler(tracer);

    @Test
    void should_be_applicable_for_non_null_context() {
        then(handler.supportsContext(new Observation.Context())).isTrue();
    }

    @Test
    void should_not_be_applicable_for_null_context() {
        then(handler.supportsContext(null)).isFalse();
    }

    @Test
    void should_put_and_remove_trace_from_thread_local_on_scope_change() {
        Observation.Context context = new Observation.Context();
        context.setName("foo");
        long currentMillis = System.currentTimeMillis();

        handler.onStart(context);

        then(tracer.currentSpan()).as("Span NOT put in scope").isNull();

        handler.onScopeOpened(context);

        then(tracer.currentSpan()).as("Span put in scope").isNotNull();

        handler.onScopeClosed(context);

        then(tracer.currentSpan()).as("Span removed from scope").isNull();

        handler.onStop(context);

        then(tracer.currentSpan()).as("Span still not in scope").isNull();
        thenSpanStartedAndStopped(currentMillis);
    }

    @Test
    void should_create_parent_child_relationship_via_observations() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        Observation parent = Observation.start("parent", registry);
        Observation child = Observation.createNotStarted("child", registry).parentObservation(parent).start();
        Observation grandchild = Observation.createNotStarted("grandchild", registry).parentObservation(child).start();

        grandchild.stop();
        child.stop();
        parent.stop();

        List<FinishedSpan> spans = testSpanHandler.spans()
            .stream()
            .map(BraveFinishedSpan::fromBrave)
            .collect(Collectors.toList());
        SpansAssert.then(spans).haveSameTraceId();
        FinishedSpan grandchildFinishedSpan = spans.get(0);
        SpanAssert.then(grandchildFinishedSpan).hasNameEqualTo("grandchild");
        FinishedSpan childFinishedSpan = spans.get(1);
        SpanAssert.then(childFinishedSpan).hasNameEqualTo("child");
        FinishedSpan parentFinishedSpan = spans.get(2);
        SpanAssert.then(parentFinishedSpan).hasNameEqualTo("parent");

        then(grandchildFinishedSpan.getParentId()).isEqualTo(childFinishedSpan.getSpanId());
        then(childFinishedSpan.getParentId()).isEqualTo(parentFinishedSpan.getSpanId());
    }

    @Test
    void should_create_parent_child_relationship_via_observations_and_manual_spans() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        Span surprise = null;
        Observation parent = Observation.start("parent", registry);
        try (Observation.Scope scope = parent.openScope()) {
            surprise = tracer.nextSpan().name("surprise").start();
            try (Tracer.SpanInScope scope2 = tracer.withSpan(surprise)) {
                Observation child = Observation.createNotStarted("child", registry).start();
                child.scoped(() -> {
                    Observation grandchild = Observation.createNotStarted("grandchild", registry)
                        .parentObservation(child)
                        .start();
                    grandchild.stop();
                });
                child.stop();
            }
            surprise.end();
        }
        parent.stop();

        List<FinishedSpan> spans = testSpanHandler.spans()
            .stream()
            .map(BraveFinishedSpan::fromBrave)
            .collect(Collectors.toList());
        SpansAssert.then(spans).haveSameTraceId();
        FinishedSpan grandchildFinishedSpan = spans.get(0);
        SpanAssert.then(grandchildFinishedSpan).hasNameEqualTo("grandchild");
        FinishedSpan childFinishedSpan = spans.get(1);
        SpanAssert.then(childFinishedSpan).hasNameEqualTo("child");
        FinishedSpan surpriseSpan = spans.get(2);
        SpanAssert.then(surpriseSpan).hasNameEqualTo("surprise");
        FinishedSpan parentFinishedSpan = spans.get(3);
        SpanAssert.then(parentFinishedSpan).hasNameEqualTo("parent");

        then(grandchildFinishedSpan.getParentId()).isEqualTo(childFinishedSpan.getSpanId());
        then(childFinishedSpan.getParentId()).isEqualTo(surprise.context().spanId());
        then(surprise.context().parentId()).isEqualTo(parentFinishedSpan.getSpanId());
    }

    @Test
    void should_use_contextual_name() {
        Observation.Context context = new Observation.Context();
        context.setName("foo");
        context.setContextualName("bar");

        handler.onStart(context);
        handler.onStop(context);

        MutableSpan data = takeOnlySpan();
        then(data.name()).isEqualTo("bar");
    }

    @Test
    void should_signal_errors() {
        Exception error = new IOException("simulated");
        Observation.Context context = new Observation.Context();
        context.setName("foo");
        context.setError(error);

        handler.onStart(context);
        handler.onError(context);
        handler.onStop(context);

        MutableSpan data = takeOnlySpan();
        then(data.error()).isSameAs(error);
    }

    @Test
    void should_signal_events() {
        Event event = Event.of("foo", "bar");
        Observation.Context context = new Observation.Context();
        context.setName("foo");

        handler.onStart(context);
        handler.onEvent(event, context);
        handler.onStop(context);

        MutableSpan data = takeOnlySpan();
        then(data.annotations()).hasSize(1);
        then(data.annotations().stream().findFirst()).isPresent().get().extracting(Map.Entry::getValue).isSameAs("bar");
    }

    @Test
    void should_put_and_remove_trace_from_thread_local_on_scope_change_for_the_same_observation() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        Observation parent = Observation.start("parent", registry);
        Span parentSpan = getSpanFromObservation(parent);

        then(parentSpan).isNotNull();

        parent.scoped(() -> {

            then(tracer.currentSpan()).isEqualTo(parentSpan);

            Observation child = Observation.start("child", registry);
            Span childSpan = getSpanFromObservation(child);

            child.scoped(() -> {
                then(tracer.currentSpan()).isEqualTo(childSpan);
                child.scoped(() -> {
                    then(tracer.currentSpan()).isEqualTo(childSpan);
                });
                then(tracer.currentSpan()).isEqualTo(childSpan);
            });

            then(tracer.currentSpan()).isEqualTo(parentSpan);
        });

        then(tracer.currentSpan()).isNull();
    }

    @Test
    void should_not_break_when_dealing_with_threads() throws InterruptedException {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        Observation parent = Observation.start("parent", registry);
        Span parentSpan = getSpanFromObservation(parent);

        then(parentSpan).isNotNull();

        try (Observation.Scope scope = parent.openScope()) {
            then(tracer.currentSpan()).isEqualTo(parentSpan);
            AtomicReference<Span> span = new AtomicReference<>();
            new Thread(ContextSnapshot.captureAll().wrap(() -> span.set(tracer.currentSpan()))).start();
            await().pollDelay(Duration.ofMillis(10)).atMost(Duration.ofMillis(100)).until(() -> span.get() != null);

            then(span.get()).isEqualTo(parentSpan);
            then(tracer.currentSpan()).isEqualTo(parentSpan);
        }

        then(tracer.currentSpan()).isNull();
    }

    private static Span getSpanFromObservation(Observation parent) {
        TracingObservationHandler.TracingContext tracingContext = parent.getContextView()
            .getOrDefault(TracingObservationHandler.TracingContext.class,
                    new TracingObservationHandler.TracingContext());
        return tracingContext.getSpan();
    }

    private void thenSpanStartedAndStopped(long currentMillis) {
        MutableSpan mutableSpan = takeOnlySpan();
        long startTimestamp = mutableSpan.startTimestamp();
        then(startTimestamp).isGreaterThan(currentMillis);
        then(mutableSpan.finishTimestamp()).as("Span has to have a duration").isGreaterThan(startTimestamp);
    }

    private MutableSpan takeOnlySpan() {
        List<MutableSpan> spans = testSpanHandler.spans();
        then(spans).hasSize(1);
        MutableSpan mutableSpan = spans.get(0);
        return mutableSpan;
    }

}
