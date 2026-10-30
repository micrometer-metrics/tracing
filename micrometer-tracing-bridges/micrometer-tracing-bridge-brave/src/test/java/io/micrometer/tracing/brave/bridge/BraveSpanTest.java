/*
 * Copyright 2023 the original author or authors.
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
package io.micrometer.tracing.brave.bridge;

import brave.Tracing;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.micrometer.tracing.TraceContext;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BraveSpanTest {

    TestSpanHandler handler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(handler).build();

    @AfterEach
    void cleanup() {
        tracing.close();
    }

    @Test
    void should_reuse_trace_context() {
        brave.Span delegate = tracing.tracer().nextSpan();
        BraveSpan span = new BraveSpan(delegate);
        TraceContext context = span.context();

        assertThat(span.context()).isSameAs(context);
        assertThat(BraveTraceContext.toBrave(context)).isSameAs(delegate.context());
        assertThat(context.traceId()).isEqualTo(delegate.context().traceIdString());
        assertThat(context.spanId()).isEqualTo(delegate.context().spanIdString());
    }

    @Test
    void should_reuse_trace_context_across_threads() throws Exception {
        BraveSpan span = new BraveSpan(tracing.tracer().nextSpan());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<TraceContext> lookup = () -> {
            ready.countDown();
            start.await();
            return span.context();
        };

        try {
            Future<TraceContext> first = executor.submit(lookup);
            Future<TraceContext> second = executor.submit(lookup);
            ready.await();
            start.countDown();

            assertThat(first.get()).isSameAs(second.get()).isSameAs(span.context());
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_not_materialize_current_span_before_abandon() {
        brave.Span delegate = tracing.tracer().nextSpan().start();
        try (brave.Tracer.SpanInScope ignored = tracing.tracer().withSpanInScope(delegate)) {
            brave.Span currentSpan = tracing.tracer().currentSpan();
            assertThat(currentSpan).isNotNull();

            new BraveSpan(currentSpan).abandon();
        }
        delegate.finish();

        assertThat(handler.spans()).hasSize(1);
        assertThat(handler.get(0).id()).isEqualTo(delegate.context().spanIdString());
    }

    @Test
    void should_abandon_current_span_after_accessing_context() {
        brave.Span delegate = tracing.tracer().nextSpan().start();
        try (brave.Tracer.SpanInScope ignored = tracing.tracer().withSpanInScope(delegate)) {
            brave.Span currentSpan = tracing.tracer().currentSpan();
            assertThat(currentSpan).isNotNull();
            BraveSpan span = new BraveSpan(currentSpan);

            span.context();
            span.abandon();
        }
        delegate.finish();

        assertThat(handler.spans()).isEmpty();
    }

    @Test
    void should_set_non_string_tags() {
        new BraveSpan(tracing.tracer().nextSpan()).start()
            .tag("string", "string")
            .tag("double", 2.5)
            .tag("long", 2)
            .tag("boolean", true)
            .end();

        assertThat(handler.get(0).tags()).containsEntry("string", "string")
            .containsEntry("double", "2.5")
            .containsEntry("long", "2")
            .containsEntry("boolean", "true");
    }

    @Test
    void should_set_multi_value_tags() {
        new BraveSpan(tracing.tracer().nextSpan()).start()
            .tagOfStrings("strings", Arrays.asList("s1", "s2", "s3"))
            .tagOfDoubles("doubles", Arrays.asList(1.0, 2.5, 3.7))
            .tagOfLongs("longs", Arrays.asList(2L, 3L, 4L))
            .tagOfBooleans("booleans", Arrays.asList(true, false, false))
            .end();

        assertThat(handler.get(0).tags()).containsEntry("strings", "s1,s2,s3")
            .containsEntry("doubles", "1.0,2.5,3.7")
            .containsEntry("longs", "2,3,4")
            .containsEntry("booleans", "true,false,false");
    }

}
