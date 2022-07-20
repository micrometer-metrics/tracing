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

package io.micrometer.tracing.otel;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Test class to be embedded in the docs. They use Tracing's API with Brave as tracer
 * implementation.
 *
 * @author Marcin Grzejszczak
 */
class BaseTests {

    ArrayListSpanProcessor spans = new ArrayListSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setSampler(alwaysOn()).addSpanProcessor(spans)
            .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracerProvider()
            .get("io.micrometer.micrometer-tracing");

    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    OtelTracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
    }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));

    @BeforeEach
    void setup() {
        this.spans.clear();
    }

    @AfterEach
    void close() {
        this.sdkTracerProvider.close();
    }

    @Test
    void should_create_a_span_with_tracer() {
        String taxValue = "10";

        // tag::manual_span_creation[]
        // Start a span. If there was a span present in this thread it will become
        // the `newSpan`'s parent.
        Span newSpan = this.tracer.nextSpan().name("calculateTax");
        try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
            // ...
            // You can tag a span
            newSpan.tag("taxValue", taxValue);
            // ...
            // You can log an event on a span
            newSpan.event("taxCalculated");
        }
        finally {
            // Once done remember to end the span. This will allow collecting
            // the span to send it to a distributed tracing system e.g. Zipkin
            newSpan.end();
        }
        // end::manual_span_creation[]

        then(this.spans.spans()).hasSize(1);
        SpanData spanData = this.spans.takeLocalSpan();
        then(spanData.getName()).isEqualTo("calculateTax");
        then(spanData.getAttributes().asMap()).containsEntry(AttributeKey.stringKey("taxValue"), "10");
        then(spanData.getEvents()).hasSize(1);
    }

    @Test
    void should_continue_a_span_with_tracer() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        String taxValue = "10";
        // tag::manual_span_continuation[]
        Span spanFromThreadX = this.tracer.nextSpan().name("calculateTax");
        try (Tracer.SpanInScope ws = this.tracer.withSpan(spanFromThreadX.start())) {
            executorService.submit(() -> {
                // Pass the span from thread X
                Span continuedSpan = spanFromThreadX;
                // ...
                // You can tag a span
                continuedSpan.tag("taxValue", taxValue);
                // ...
                // You can log an event on a span
                continuedSpan.event("taxCalculated");
            }).get();
        }
        finally {
            spanFromThreadX.end();
        }
        // end::manual_span_continuation[]

        then(spans.spans()).hasSize(1);
        SpanData spanData = this.spans.takeLocalSpan();
        then(spanData.getName()).isEqualTo("calculateTax");
        then(spanData.getAttributes().asMap()).containsEntry(AttributeKey.stringKey("taxValue"), "10");
        then(spanData.getEvents()).hasSize(1);
        executorService.shutdown();
    }

    @Test
    void should_start_a_span_with_explicit_parent() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        String commissionValue = "10";
        Span initialSpan = this.tracer.nextSpan().name("calculateTax").start();

        executorService.submit(() -> {
            // tag::manual_span_joining[]
            // let's assume that we're in a thread Y and we've received
            // the `initialSpan` from thread X. `initialSpan` will be the parent
            // of the `newSpan`
            Span newSpan = null;
            try (Tracer.SpanInScope ws = this.tracer.withSpan(initialSpan)) {
                newSpan = this.tracer.nextSpan().name("calculateCommission");
                // ...
                // You can tag a span
                newSpan.tag("commissionValue", commissionValue);
                // ...
                // You can log an event on a span
                newSpan.event("commissionCalculated");
            }
            finally {
                // Once done remember to end the span. This will allow collecting
                // the span to send it to e.g. Zipkin. The tags and events set on the
                // newSpan will not be present on the parent
                if (newSpan != null) {
                    newSpan.end();
                }
            }
            // end::manual_span_joining[]
        }).get();
        ;
        Optional<SpanData> calculateTax = spans.spans().stream()
                .filter(span -> span.getName().equals("calculateCommission")).findFirst();
        then(calculateTax).isPresent();
        then(calculateTax.get().getAttributes().asMap()).containsEntry(AttributeKey.stringKey("commissionValue"), "10");
        then(calculateTax.get().getEvents()).hasSize(1);
        executorService.shutdown();
    }

}
