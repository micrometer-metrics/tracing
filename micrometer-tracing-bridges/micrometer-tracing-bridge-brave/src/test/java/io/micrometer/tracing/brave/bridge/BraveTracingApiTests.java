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
package io.micrometer.tracing.brave.bridge;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.handler.SpanHandler;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.context.ContextAccessor;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.*;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;

/**
 * Sources for tracing-api.adoc
 */
class BraveTracingApiTests {

    // [Brave component] Example of using a SpanHandler. SpanHandler is a component
    // that gets called when a span is finished.
    SpanHandler spanHandler = new TestSpanHandler();

    // [Brave component] CurrentTraceContext is a Brave component that allows you to
    // retrieve the current TraceContext.
    StrictCurrentTraceContext braveCurrentTraceContext = StrictCurrentTraceContext.create();

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for Brave's
    // CurrentTraceContext
    CurrentTraceContext bridgeContext = new BraveCurrentTraceContext(this.braveCurrentTraceContext);

    // [Brave component] Tracing is the root component that allows to configure the
    // tracer, handlers, context propagation etc.
    Tracing tracing = Tracing.newBuilder()
        .currentTraceContext(this.braveCurrentTraceContext)
        .supportsJoin(false)
        .traceId128Bit(true)
        // For Baggage to work you need to provide a list of fields to propagate
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("from_span_in_scope 1")))
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("from_span_in_scope 2")))
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("from_span")))
            .build())
        .sampler(Sampler.ALWAYS_SAMPLE)
        .addSpanHandler(this.spanHandler)
        .build();

    // [Brave component] Tracer is a component that handles the life-cycle of a span
    brave.Tracer braveTracer = this.tracing.tracer();

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for Brave's Tracer
    Tracer tracer = new BraveTracer(this.braveTracer, this.bridgeContext, new BraveBaggageManager());

    @AfterEach
    void close() {
        this.tracing.close();
        this.braveCurrentTraceContext.close();
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
                then(baggage.get()).as("[In scope] Baggage 1").isEqualTo("value 1");
                then(tracer.getBaggage("from_span_in_scope 1").get()).as("[In scope] Baggage 1").isEqualTo("value 1");
            }

            try (BaggageInScope baggage = baggageForSpanInScopeTwo.makeCurrent()) {
                then(baggage.get()).as("[In scope] Baggage 2").isEqualTo("value 2");
                then(tracer.getBaggage("from_span_in_scope 2").get()).as("[In scope] Baggage 2").isEqualTo("value 2");
            }
        }

        // Assuming that you have a handle to the span
        Baggage baggageForExplicitSpan = tracer.createBaggage("from_span").set(span.context(), "value 3");
        try (BaggageInScope baggage = baggageForExplicitSpan.makeCurrent()) {
            then(baggage.get(span.context())).as("[Span passed explicitly] Baggage 3").isEqualTo("value 3");
            then(tracer.getBaggage("from_span").get(span.context())).as("[Span passed explicitly] Baggage 3")
                .isEqualTo("value 3");
        }

        // Assuming that there's no span in scope
        Baggage baggageFour = tracer.createBaggage("from_span_in_scope 1", "value 1");

        // When there's no span in scope, there will never be any baggage - even if you
        // make it current
        try (BaggageInScope baggage = baggageFour.makeCurrent()) {
            then(baggage.get()).as("[Out of span scope] Baggage 1").isNull();
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
    void should_not_break_on_scopes() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));

        ContextRegistry.getInstance().registerContextAccessor(new ContextAccessor<Observation, Observation>() {
            @Override
            public Class<? extends Observation> readableType() {
                return Observation.class;
            }

            @Override
            public void readValues(Observation sourceContext, Predicate<Object> keyPredicate,
                    Map<Object, Object> readValues) {
                readValues.put(ObservationThreadLocalAccessor.KEY, sourceContext);
            }

            @Override
            public <T> T readValue(Observation sourceContext, Object key) {
                return (T) sourceContext;
            }

            @Override
            public Class<? extends Observation> writeableType() {
                return Observation.class;
            }

            @Override
            public Observation writeValues(Map<Object, Object> valuesToWrite, Observation targetContext) {
                return (Observation) valuesToWrite.get(ObservationThreadLocalAccessor.KEY);
            }
        });

        Observation obs0 = Observation.createNotStarted("observation-0", registry);
        Observation obs1 = Observation.createNotStarted("observation-1", registry);

        thenNoException().isThrownBy(() -> {
            try (Observation.Scope scope = obs0.start().openScope()) {
                try (Observation.Scope scope2 = obs1.start().openScope()) {
                    try (ContextSnapshot.Scope scope3 = ContextSnapshot.setAllThreadLocalsFrom(obs1)) {
                        // do sth here
                    }
                }
            }
        });
    }

}
