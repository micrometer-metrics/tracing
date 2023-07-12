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

import io.micrometer.tracing.*;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn;
import static org.assertj.core.api.BDDAssertions.then;

class OtelTracingApiTests {

    // [OTel component] Example of using a SpanExporter. SpanExporter is a component
    // that gets called when a span is finished.
    ArrayListSpanProcessor spanExporter = new ArrayListSpanProcessor();

    // [OTel component] SdkTracerProvider is a SDK implementation for TracerProvider
    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(alwaysOn())
        .addSpanProcessor(spanExporter)
        .build();

    // [OTel component] The SDK implementation of OpenTelemetry
    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    // [OTel component] Tracer is a component that handles the life-cycle of a span
    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracerProvider()
        .get("io.micrometer.micrometer-tracing");

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for OTel
    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    // [Micrometer Tracing component] A Micrometer Tracing listener for setting up MDC
    Slf4JEventListener slf4JEventListener = new Slf4JEventListener();

    // [Micrometer Tracing component] A Micrometer Tracing listener for setting
    // Baggage in MDC. Customizable
    // with correlation fields (currently we're setting empty list)
    Slf4JBaggageEventListener slf4JBaggageEventListener = new Slf4JBaggageEventListener(Collections.emptyList());

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for OTel's Tracer.
    // You can consider
    // customizing the baggage manager with correlation and remote fields (currently
    // we're setting empty lists)
    OtelTracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
        slf4JEventListener.onEvent(event);
        slf4JBaggageEventListener.onEvent(event);
    }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));

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
    void should_work_with_baggage_with_legacy_api() {
        Span span = tracer.nextSpan().name("parent").start();

        // Assuming that there's a span in scope...
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            // Not passing a TraceContext explicitly will bind the baggage to the current
            // TraceContext
            Baggage baggageForSpanInScopeOne = tracer.createBaggage("from_span_in_scope 1", "value 1");
            Baggage baggageForSpanInScopeTwo = tracer.createBaggage("from_span_in_scope 2").set("value 2");

            try (BaggageInScope baggage = baggageForSpanInScopeOne.makeCurrent()) {
                then(baggage.get()).as("[In scope] Baggage 1").isEqualTo("value 1");
                then(tracer.getBaggage("from_span_in_scope 1").get()).as("[In scope] Baggage 1").isEqualTo("value 1");
            }

            try (BaggageInScope baggage = baggageForSpanInScopeTwo.makeCurrent()) {
                then(baggage.get()).as("[In scope] Baggage 2").isEqualTo("value 2");
                then(tracer.getBaggage("from_span_in_scope 2").get()).as("[In scope] Baggage 2").isEqualTo("value 2");
            }
        }

        then(tracer.currentSpan()).isNull();

        // Assuming that you have a handle to the span
        Baggage baggageForExplicitSpan = tracer.createBaggage("from_span").set(span.context(), "value 3");
        try (BaggageInScope baggage = baggageForExplicitSpan.makeCurrent()) {
            then(baggage.get(span.context())).as("[Span passed explicitly] Baggage 3").isEqualTo("value 3");
            then(tracer.getBaggage("from_span").get(span.context())).as("[Span passed explicitly] Baggage 3")
                .isEqualTo("value 3");
        }

        then(tracer.currentSpan()).isNull();

        // Assuming that there's no span in scope
        Baggage baggageFour = tracer.createBaggage("from_span_in_scope 1", "value 1");

        then(tracer.currentSpan()).isNull();

        // When there's no span in scope, baggage can still be there (that's incosistent
        // with Brave)
        try (BaggageInScope baggage = baggageFour.makeCurrent()) {
            then(baggage.get()).as("[Out of span scope] Baggage 1").isNotNull();
            then(tracer.getBaggage("from_span_in_scope 1").get()).as("[Out of span scope] Baggage 1").isNotNull();
        }
        then(tracer.getBaggage("from_span_in_scope 1").get()).as("[Out of scope] Baggage 1").isNull();
        then(tracer.getBaggage("from_span_in_scope 2").get()).as("[Out of scope] Baggage 2").isNull();
        then(tracer.getBaggage("from_span").get()).as("[Out of scope] Baggage 3").isNull();

        // You will retrieve the baggage value ALWAYS when you pass the context explicitly
        then(tracer.getBaggage("from_span").get(span.context())).as("[Out of scope - with context] Baggage 3")
            .isEqualTo("value 3");
    }

    @Test
    void should_work_with_baggage() {
        Span span = tracer.nextSpan().name("parent").start();

        // Assuming that there's a span in scope...
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            try (BaggageInScope baggageForSpanInScopeOne = tracer.createBaggageInScope("from_span_in_scope 1",
                    "value 1")) {
                then(baggageForSpanInScopeOne.get()).as("[In scope] Baggage 1").isEqualTo("value 1");
                then(tracer.getBaggage("from_span_in_scope 1").get()).as("[In scope] Baggage 1").isEqualTo("value 1");
            }

            try (BaggageInScope baggageForSpanInScopeTwo = tracer.createBaggageInScope("from_span_in_scope 2",
                    "value 2");) {
                then(baggageForSpanInScopeTwo.get()).as("[In scope] Baggage 2").isEqualTo("value 2");
                then(tracer.getBaggage("from_span_in_scope 2").get()).as("[In scope] Baggage 2").isEqualTo("value 2");
            }
        }

        // Assuming that you have a handle to the span
        try (BaggageInScope baggageForExplicitSpan = tracer.createBaggageInScope(span.context(), "from_span",
                "value 3")) {
            then(baggageForExplicitSpan.get(span.context())).as("[Span passed explicitly] Baggage 3")
                .isEqualTo("value 3");
            then(tracer.getBaggage("from_span").get(span.context())).as("[Span passed explicitly] Baggage 3")
                .isEqualTo("value 3");
        }

        // Assuming that there's no span in scope
        // When there's no span in scope, there will never be any baggage - even if you
        // make it current (this is inconsistent with Brave)
        try (BaggageInScope baggageFour = tracer.createBaggageInScope("from_span_in_scope 1", "value 1");) {
            then(baggageFour.get()).as("[Out of span scope] Baggage 1").isNotNull();
            then(tracer.getBaggage("from_span_in_scope 1").get()).as("[Out of span scope] Baggage 1").isNotNull();
        }
        then(tracer.getBaggage("from_span_in_scope 1").get()).as("[Out of scope] Baggage 1").isNull();
        then(tracer.getBaggage("from_span_in_scope 2").get()).as("[Out of scope] Baggage 2").isNull();
        then(tracer.getBaggage("from_span").get()).as("[Out of scope] Baggage 3").isNull();

        // You will retrieve the baggage value ALWAYS when you pass the context explicitly
        then(tracer.getBaggage("from_span").get(span.context())).as("[Out of scope - with context] Baggage 3")
            .isEqualTo("value 3");
    }

    @Test
    void testSlf4JEventListener() {
        String customTraceIdKey = "customTraceId";
        String customSpanIdKey = "customSpanId";
        Slf4JEventListener customSlf4JEventListener = new Slf4JEventListener(customTraceIdKey, customSpanIdKey);

        Span newSpan = this.tracer.nextSpan().name("testMDC");
        try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
            Context current = Context.current();

            EventPublishingContextWrapper.ScopeAttachedEvent scopeAttachedEvent = new EventPublishingContextWrapper.ScopeAttachedEvent(
                    current);
            slf4JEventListener.onEvent(scopeAttachedEvent);
            customSlf4JEventListener.onEvent(scopeAttachedEvent);

            String traceId = MDC.get("traceId");
            String customTraceId = MDC.get(customTraceIdKey);

            then(traceId).isNotBlank();
            then(customTraceId).isNotBlank();
            then(traceId).isEqualTo(customTraceId);

            String spanId = MDC.get("spanId");
            String customSpanId = MDC.get(customSpanIdKey);

            then(spanId).isNotBlank();
            then(customSpanId).isNotBlank();
            then(spanId).isEqualTo(customSpanId);

            EventPublishingContextWrapper.ScopeRestoredEvent scopeRestoredEvent = new EventPublishingContextWrapper.ScopeRestoredEvent(
                    current);
            slf4JEventListener.onEvent(scopeRestoredEvent);
            customSlf4JEventListener.onEvent(scopeRestoredEvent);

            traceId = MDC.get("traceId");
            customTraceId = MDC.get(customTraceIdKey);

            then(traceId).isNotBlank();
            then(customTraceId).isNotBlank();
            then(traceId).isEqualTo(customTraceId);

            spanId = MDC.get("spanId");
            customSpanId = MDC.get(customSpanIdKey);

            then(spanId).isNotBlank();
            then(customSpanId).isNotBlank();
            then(spanId).isEqualTo(customSpanId);
        }
        finally {
            newSpan.end();
        }

        EventPublishingContextWrapper.ScopeClosedEvent scopeClosedEvent = new EventPublishingContextWrapper.ScopeClosedEvent();
        slf4JEventListener.onEvent(scopeClosedEvent);
        customSlf4JEventListener.onEvent(scopeClosedEvent);

        then(MDC.get("traceId")).isNull();
        then(MDC.get(customTraceIdKey)).isNull();
        then(MDC.get("spanId")).isNull();
        then(MDC.get(customSpanIdKey)).isNull();

    }

    @Test
    void should_add_links() throws InterruptedException {
        // Let's say that we want to add 2 links to our span
        // Create a span using builder to add links
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tag", "value");

        Span newSpan = this.tracer.spanBuilder()
            .name("foo")
            .addLink(new Link(tracer.traceContextBuilder()
                .traceId("0af7651916cd43dd8448eb211c80319c")
                .spanId("b7ad6b7169203331")
                .sampled(true)
                .build()))
            .addLink(new Link(tracer.traceContextBuilder()
                .traceId("0af7651916cd43ddb7ad6b7169203331")
                .spanId("8448eb211c80319c")
                .sampled(true)
                .build(), attributes))
            .start();

        // do some logic then end the span

        newSpan.end();

        Queue<SpanData> spans = spanExporter.spans();
        then(spans).hasSize(1);
        SpanData data = spans.poll();
        then(data.getLinks()).hasSize(2);
        LinkData linkData = data.getLinks().get(0);
        then(linkData.getSpanContext().getTraceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        then(linkData.getSpanContext().getSpanId()).isEqualTo("b7ad6b7169203331");
        linkData = data.getLinks().get(1);
        then(linkData.getSpanContext().getTraceId()).isEqualTo("0af7651916cd43ddb7ad6b7169203331");
        then(linkData.getSpanContext().getSpanId()).isEqualTo("8448eb211c80319c");
        then(linkData.getAttributes().asMap()).containsEntry(AttributeKey.stringKey("tag"), "value");
    }

    @Test
    void should_work_with_with_scope_for_tracer_and_current_trace_context() {
        Span span = tracer.nextSpan();

        then(io.opentelemetry.api.trace.Span.current()).isSameAs(io.opentelemetry.api.trace.Span.getInvalid());
        then(tracer.currentSpan()).isNull();

        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            OtelCurrentTraceContext.WrappedScope wrappedScope = getScopeFromTracerScope(
                    (OtelTracer.WrappedSpanInScope) ws);
            then(wrappedScope.scope).isNotSameAs(Scope.noop());
            then(io.opentelemetry.api.trace.Span.current()).isSameAs(OtelSpan.toOtel(span));
            then(tracer.currentSpan()).isEqualTo(span);
            try (Tracer.SpanInScope ws2 = tracer.withSpan(span)) {
                OtelCurrentTraceContext.WrappedScope wrappedScope2 = getScopeFromTracerScope(
                        (OtelTracer.WrappedSpanInScope) ws2);
                then(wrappedScope2.scope).isSameAs(Scope.noop());
                then(io.opentelemetry.api.trace.Span.current()).isSameAs(OtelSpan.toOtel(span));
                then(tracer.currentSpan()).isEqualTo(span);
            }
        }

        then(io.opentelemetry.api.trace.Span.current()).isSameAs(io.opentelemetry.api.trace.Span.getInvalid());
        then(tracer.currentSpan()).isNull();

        try (CurrentTraceContext.Scope ws = otelCurrentTraceContext.maybeScope(span.context())) {
            Scope scope = ((OtelCurrentTraceContext.WrappedScope) ws).scope;
            then(scope).isNotSameAs(Scope.noop());
            then(io.opentelemetry.api.trace.Span.current()).isSameAs(OtelSpan.toOtel(span));
            then(tracer.currentSpan()).isEqualTo(span);
            try (CurrentTraceContext.Scope ws2 = otelCurrentTraceContext.maybeScope(span.context())) {
                Scope scope2 = ((OtelCurrentTraceContext.WrappedScope) ws2).scope;
                then(scope2).isSameAs(Scope.noop());
                then(io.opentelemetry.api.trace.Span.current()).isSameAs(OtelSpan.toOtel(span));
                then(tracer.currentSpan()).isEqualTo(span);
            }
        }

        then(io.opentelemetry.api.trace.Span.current()).isSameAs(io.opentelemetry.api.trace.Span.getInvalid());
        then(tracer.currentSpan()).isNull();
    }

    private static OtelCurrentTraceContext.WrappedScope getScopeFromTracerScope(OtelTracer.WrappedSpanInScope ws) {
        return (OtelCurrentTraceContext.WrappedScope) ws.scope;
    }

}
