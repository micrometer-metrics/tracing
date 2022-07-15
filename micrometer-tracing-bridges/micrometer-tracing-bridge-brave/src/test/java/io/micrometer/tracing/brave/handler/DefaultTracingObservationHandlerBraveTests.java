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

import java.util.List;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SpanAssert;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("unchecked")
class DefaultTracingObservationHandlerBraveTests {

    TestSpanHandler testSpanHandler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder()
            .addSpanHandler(testSpanHandler)
            .build();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());

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
        Observation.Context context = new Observation.Context().setName("foo");
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

        List<FinishedSpan> spans = testSpanHandler.spans().stream().map(BraveFinishedSpan::fromBrave).collect(Collectors.toList());
        SpansAssert.then(spans)
                .haveSameTraceId();
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
    void should_use_contextual_name() {
        Observation.Context context = new Observation.Context().setName("foo").setContextualName("bar");

        handler.onStart(context);
        handler.onStop(context);

        MutableSpan data = takeOnlySpan();
        then(data.name()).isEqualTo("bar");
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
