/*
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn;
import static org.assertj.core.api.BDDAssertions.then;

class OtelTracingApiTests {

    // [OTel component] Example of using a SpanExporter. SpanExporter is a component
    // that gets called when a span is finished.
    SpanExporter spanExporter = new ArrayListSpanProcessor();

    // [OTel component] SdkTracerProvider is a SDK implementation for TracerProvider
    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setSampler(alwaysOn())
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build()).build();

    // [OTel component] The SDK implementation of OpenTelemetry
    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();

    // [OTel component] Tracer is a component that handles the life-cycle of a span
    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracerProvider()
            .get("io.micrometer.micrometer-tracing");

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for OTel
    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    // [Micrometer Tracing component] A Micrometer Tracing listener for setting up MDC
    Slf4JEventListener slf4JEventListener = new Slf4JEventListener();

    Slf4JEventListener slf4JEventListenerCustom = new Slf4JEventListener("customTraceId", "customSpanId");

    // [Micrometer Tracing component] A Micrometer Tracing listener for setting
    // Baggage in MDC. Customizable
    // with correlation fields (currently we're setting empty list)
    Slf4JBaggageEventListener slf4JBaggageEventListener = new Slf4JBaggageEventListener(Collections.emptyList());

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for OTel's Tracer.
    // You can consider
    // customizing the baggage manager with correlation and remote fields (currently
    // we're setting empty lists)
    OtelTracer tracer;

    {
        OtelTracer.EventPublisher eventPublisher = event -> {
            slf4JEventListener.onEvent(event);
            slf4JEventListenerCustom.onEvent(event);
            slf4JBaggageEventListener.onEvent(event);
        };
        ContextStorage.addWrapper(new EventPublishingContextWrapper(eventPublisher));
        tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, eventPublisher,
                new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
    }

    @BeforeEach
    void setup() {
        this.spanExporter.close();
    }

    @AfterEach
    void close() {
        this.sdkTracerProvider.close();
    }

    @Test
    void should_create_a_span_with_tracer() {
        String taxValue = "10";

        // Create a span. If there was a span present in this thread it will become
        // the `newSpan`'s parent.
        Span newSpan = this.tracer.nextSpan().name("calculateTax");
        // Start a span and put it in scope. Putting in scope means putting the span
        // in thread local
        // and, if configured, adjust the MDC to contain tracing information
        try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
            // ...
            // You can tag a span - put a key value pair on it for better debugging
            newSpan.tag("taxValue", taxValue);
            // ...
            // You can log an event on a span - an event is an annotated timestamp
            newSpan.event("taxCalculated");
        }
        finally {
            // Once done remember to end the span. This will allow collecting
            // the span to send it to a distributed tracing system e.g. Zipkin
            newSpan.end();
        }
    }

    @Test
    void should_continue_a_span_with_tracer() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        String taxValue = "10";
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

        executorService.shutdown();
    }

    @Test
    void should_start_a_span_with_explicit_parent() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        String commissionValue = "10";
        Span initialSpan = this.tracer.nextSpan().name("calculateTax").start();

        executorService.submit(() -> {
            // let's assume that we're in a thread Y and we've received
            // the `initialSpan` from thread X. `initialSpan` will be the parent
            // of the `newSpan`
            Span newSpan = this.tracer.nextSpan(initialSpan).name("calculateCommission");
            // ...
            // You can tag a span
            newSpan.tag("commissionValue", commissionValue);
            // ...
            // You can log an event on a span
            newSpan.event("commissionCalculated");
            // Once done remember to end the span. This will allow collecting
            // the span to send it to e.g. Zipkin. The tags and events set on the
            // newSpan will not be present on the parent
            newSpan.end();
        }).get();

        executorService.shutdown();
    }

    @Test
    void should_work_with_baggage() {
        Span span = tracer.nextSpan().name("parent").start();

        // Assuming that there's a span in scope...
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            // Not passing a TraceContext explicitly will bind the baggage to the current
            // TraceContext
            Baggage baggageForSpanInScopeOne = tracer.createBaggage("from_span_in_scope 1", "value 1");
            Baggage baggageForSpanInScopeTwo = tracer.createBaggage("from_span_in_scope 2").set("value 2");

            try (BaggageInScope baggage = baggageForSpanInScopeOne.makeCurrent()) {
                then(baggageForSpanInScopeOne.get()).as("[In scope] Baggage 1").isEqualTo("value 1");
                then(tracer.getBaggage("from_span_in_scope 1").get()).as("[In scope] Baggage 1").isEqualTo("value 1");
            }

            try (BaggageInScope baggage = baggageForSpanInScopeTwo.makeCurrent()) {
                then(baggageForSpanInScopeTwo.get()).as("[In scope] Baggage 2").isEqualTo("value 2");
                then(tracer.getBaggage("from_span_in_scope 2").get()).as("[In scope] Baggage 2").isEqualTo("value 2");
            }
        }

        // Assuming that you have a handle to the span
        Baggage baggageForExplicitSpan = tracer.createBaggage("from_span").set(span.context(), "value 3");
        try (BaggageInScope baggage = baggageForExplicitSpan.makeCurrent()) {
            then(baggageForExplicitSpan.get(span.context())).as("[Span passed explicitly] Baggage 3")
                    .isEqualTo("value 3");
            then(tracer.getBaggage("from_span").get(span.context())).as("[Span passed explicitly] Baggage 3")
                    .isEqualTo("value 3");
        }

        // Assuming that there's no span in scope
        Baggage baggageFour = tracer.createBaggage("from_span_in_scope 1", "value 1");

        // When there's no span in scope, there will never be any baggage - even if you
        // make it current
        try (BaggageInScope baggage = baggageFour.makeCurrent()) {
            then(baggageFour.get()).as("[Out of span scope] Baggage 1").isNull();
            then(tracer.getBaggage("from_span_in_scope 1").get()).as("[Out of span scope] Baggage 1").isNull();
        }
        then(tracer.getBaggage("from_span_in_scope 1").get()).as("[Out of scope] Baggage 1").isNull();
        then(tracer.getBaggage("from_span_in_scope 2").get()).as("[Out of scope] Baggage 2").isNull();
        then(tracer.getBaggage("from_span").get()).as("[Out of scope] Baggage 3").isNull();

        // You will retrieve the baggage value ALWAYS when you pass the context explicitly
        then(tracer.getBaggage("from_span").get(span.context())).as("[Out of scope - with context] Baggage 3")
                .isEqualTo("value 3");
    }

    @Test
    void testMDC() {
        Span newSpan = this.tracer.nextSpan().name("testMDC");
        try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
            then(MDC.get("traceId")).isNotBlank();
            then(MDC.get("customTraceId")).isNotBlank();
        }
        finally {

            newSpan.end();
        }
    }

}
