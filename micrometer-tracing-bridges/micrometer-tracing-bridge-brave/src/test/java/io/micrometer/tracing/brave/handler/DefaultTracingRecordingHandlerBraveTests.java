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

import java.time.Duration;
import java.util.List;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.Timer;
import io.micrometer.api.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingRecordingHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("unchecked")
class DefaultTracingRecordingHandlerBraveTests {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    TestSpanHandler testSpanHandler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder()
            .addSpanHandler(testSpanHandler)
            .build();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());

    DefaultTracingRecordingHandler handler = new DefaultTracingRecordingHandler(tracer);

    @Test
    void should_be_applicable_for_non_null_context() {
        then(handler.supportsContext(new Timer.HandlerContext())).isTrue();
    }

    @Test
    void should_not_be_applicable_for_null_context() {
        then(handler.supportsContext(null)).isFalse();
    }

    @Test
    void should_put_and_remove_trace_from_thread_local_on_scope_change() {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer.HandlerContext context = new Timer.HandlerContext();
        long currentMillis = System.currentTimeMillis();

        handler.onStart(sample, context);

        then(tracer.currentSpan()).as("Span NOT put in scope").isNull();

        handler.onScopeOpened(sample, context);

        then(tracer.currentSpan()).as("Span put in scope").isNotNull();

        handler.onScopeClosed(sample, context);

        then(tracer.currentSpan()).as("Span removed from scope").isNull();

        handler.onStop(sample, context, Timer.builder("name").register(meterRegistry), Duration.ZERO);

        then(tracer.currentSpan()).as("Span still not in scope").isNull();
        thenSpanStartedAndStopped(currentMillis);
    }

    private void thenSpanStartedAndStopped(long currentMillis) {
        List<MutableSpan> spans = testSpanHandler.spans();
        then(spans).hasSize(1);
        long startTimestamp = spans.get(0).startTimestamp();
        then(startTimestamp).isGreaterThan(currentMillis);
        then(spans.get(0).finishTimestamp()).as("Span has to have a duration").isGreaterThan(startTimestamp);
    }

}
