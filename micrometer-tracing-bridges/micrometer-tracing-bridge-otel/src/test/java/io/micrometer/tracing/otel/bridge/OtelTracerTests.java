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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;

class OtelTracerTests {

    RecordingSpanProcessor spanProcessor = new RecordingSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().addSpanProcessor(this.spanProcessor).build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(this.sdkTracerProvider).build();

    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    OtelTracer tracer = new OtelTracer(this.openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing"),
            this.otelCurrentTraceContext, event -> {
            }, new OtelBaggageManager(this.otelCurrentTraceContext, Collections.singletonList("foo"),
                    Collections.emptyList()));

    {
        this.spanProcessor.tracer = this.tracer;
    }

    @AfterEach
    void close() {
        this.openTelemetrySdk.close();
    }

    @Test
    void nextSpanWithParentShouldNotChangeCurrentSpanWhileStartingChild() {
        Span grandparent = this.tracer.nextSpan().name("grandparent").start();
        Span parent;
        try (Tracer.SpanInScope ws = this.tracer.withSpan(grandparent)) {
            parent = this.tracer.nextSpan().name("parent").start();
        }
        Span other = this.tracer.nextSpan().name("other").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(other)) {
            Span child = Objects.requireNonNull(this.tracer.nextSpan(parent)).name("child").start();

            then(this.spanProcessor.currentSpanIdOnStart).isEqualTo(other.context().spanId());
            then(this.spanProcessor.currentOtelSpanIdOnStart).isEqualTo(other.context().spanId());
            then(child.context().traceId()).isEqualTo(parent.context().traceId());
            then(child.context().parentId()).isEqualTo(parent.context().spanId());
            child.end();
        }
        other.end();
        parent.end();
        grandparent.end();
    }

    @Test
    void nextSpanWithParentShouldNotSetCurrentSpanWhileStartingChildWhenNoSpanIsInScope() {
        Span grandparent = this.tracer.nextSpan().name("grandparent").start();
        Span parent;
        try (Tracer.SpanInScope ws = this.tracer.withSpan(grandparent)) {
            parent = this.tracer.nextSpan().name("parent").start();
        }
        Span child = Objects.requireNonNull(this.tracer.nextSpan(parent)).name("child").start();

        then(this.spanProcessor.currentSpanIdOnStart).isNull();
        then(this.spanProcessor.currentOtelSpanIdOnStart).isNull();
        then(child.context().parentId()).isEqualTo(parent.context().spanId());
        child.end();
        parent.end();
        grandparent.end();
    }

    @Test
    void nextSpanWithParentInScopeShouldKeepParentAsCurrentSpanWhileStartingChild() {
        Span parent = this.tracer.nextSpan().name("parent").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(parent)) {
            Span child = Objects.requireNonNull(this.tracer.nextSpan(parent)).name("child").start();

            then(this.spanProcessor.currentSpanIdOnStart).isEqualTo(parent.context().spanId());
            then(this.spanProcessor.currentOtelSpanIdOnStart).isEqualTo(parent.context().spanId());
            child.end();
        }
        parent.end();
    }

    @Test
    void nextSpanWithParentShouldPropagateParentBaggageToChildOnAnotherThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Span parent = this.tracer.nextSpan().name("parent").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(parent);
                BaggageInScope baggage = this.tracer.createBaggageInScope(parent.context(), "foo", "bar")) {
            String baggageInChildScope = executor.submit(() -> {
                Span child = Objects.requireNonNull(this.tracer.nextSpan(parent)).name("child").start();
                try (Tracer.SpanInScope childScope = this.tracer.withSpan(child)) {
                    return this.tracer.getBaggage("foo").get();
                }
                finally {
                    child.end();
                }
            }).get(5, TimeUnit.SECONDS);

            then(this.spanProcessor.parentContextBaggageOnStart).isEqualTo("bar");
            then(baggageInChildScope).isEqualTo("bar");
        }
        finally {
            executor.shutdownNow();
        }
        parent.end();
    }

    static class RecordingSpanProcessor implements SpanProcessor {

        @Nullable Tracer tracer;

        volatile @Nullable String currentSpanIdOnStart;

        volatile @Nullable String currentOtelSpanIdOnStart;

        volatile @Nullable String parentContextBaggageOnStart;

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            Span currentSpan = Objects.requireNonNull(this.tracer).currentSpan();
            this.currentSpanIdOnStart = currentSpan != null ? currentSpan.context().spanId() : null;
            io.opentelemetry.api.trace.Span currentOtelSpan = io.opentelemetry.api.trace.Span.current();
            this.currentOtelSpanIdOnStart = currentOtelSpan.getSpanContext().isValid()
                    ? currentOtelSpan.getSpanContext().getSpanId() : null;
            this.parentContextBaggageOnStart = Baggage.fromContext(parentContext).getEntryValue("foo");
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
        }

        @Override
        public boolean isEndRequired() {
            return false;
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

    }

}
