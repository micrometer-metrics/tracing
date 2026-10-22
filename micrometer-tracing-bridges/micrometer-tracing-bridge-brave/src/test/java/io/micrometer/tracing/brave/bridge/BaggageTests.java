/*
 * Copyright 2024 the original author or authors.
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
import brave.handler.MutableSpan;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.*;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.then;

class BaggageTests {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(BaggageTests.class);

    public static final String KEY_1 = "key1";

    public static final String VALUE_1 = "value1";

    public static final String TAG_KEY = "tagKey";

    public static final String TAG_VALUE = "tagValue";

    public static final String OBSERVATION_BAGGAGE_KEY = "observationKey";

    public static final String OBSERVATION_BAGGAGE_VALUE = "observationValue";

    TestSpanHandler spanHandler = new TestSpanHandler();

    StrictCurrentTraceContext braveCurrentTraceContext = StrictCurrentTraceContext.create();

    CurrentTraceContext bridgeContext = new BraveCurrentTraceContext(this.braveCurrentTraceContext);

    Tracing tracing = Tracing.newBuilder()
        .currentTraceContext(this.braveCurrentTraceContext)
        .supportsJoin(false)
        .traceId128Bit(true)
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create(KEY_1)))
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create(OBSERVATION_BAGGAGE_KEY)))
            .add(BaggagePropagationConfig.SingleBaggageField.local(BaggageField.create(TAG_KEY)))
            .build())
        .sampler(Sampler.ALWAYS_SAMPLE)
        .addSpanHandler(this.spanHandler)
        .build();

    brave.Tracer braveTracer = this.tracing.tracer();

    BravePropagator propagator = new BravePropagator(tracing);

    Tracer tracer = new BraveTracer(this.braveTracer, this.bridgeContext,
            new BraveBaggageManager(Collections.singletonList(TAG_KEY), Arrays.asList(KEY_1, OBSERVATION_BAGGAGE_KEY)));

    ObservationRegistry observationRegistry = ObservationThreadLocalAccessor.getInstance().getObservationRegistry();

    @BeforeEach
    void setupHandler() {
        observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
    }

    @AfterEach
    void cleanup() {
        tracing.close();
    }

    @Test
    void canSetAndGetBaggage() {
        // GIVEN
        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            // WHEN
            try (BaggageInScope baggageInScope = this.tracer.getBaggage(KEY_1).makeCurrent(VALUE_1)) {
                // THEN
                then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
            }
        }
    }

    @Test
    void canSetAndGetBaggageWithLegacyApi() {
        // GIVEN
        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            // WHEN
            this.tracer.getBaggage(KEY_1).set(VALUE_1);

            // THEN
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
        }
    }

    @Test
    void injectAndExtractKeepsTheBaggage() {
        // GIVEN
        Map<String, String> carrier = new HashMap<>();
        Propagator.Setter<Map<String, String>> setter = (stringStringMap, key,
                value) -> Objects.requireNonNull(stringStringMap).put(key, value);

        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            try (BaggageInScope baggageInScope = this.tracer.createBaggageInScope(KEY_1, VALUE_1)) {
                // WHEN
                this.propagator.inject(Objects.requireNonNull(tracer.currentTraceContext().context()), carrier, setter);
            }

            // THEN
            then(carrier.get(KEY_1)).isEqualTo(VALUE_1);
        }

        // WHEN
        Span extractedSpan = propagator.extract(carrier, Map::get).start();

        // THEN
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(extractedSpan)) {
            then(tracer.getBaggage(KEY_1).get(extractedSpan.context())).isEqualTo(VALUE_1);
            try (BaggageInScope baggageInScope = tracer.getBaggage(KEY_1).makeCurrent()) {
                then(baggageInScope.get()).isEqualTo(VALUE_1);
            }
        }
    }

    @Test
    void injectAndExtractKeepsTheBaggageWithLegacyApi() {
        // GIVEN
        Map<String, String> carrier = new HashMap<>();
        Propagator.Setter<Map<String, String>> setter = (stringStringMap, key,
                value) -> Objects.requireNonNull(stringStringMap).put(key, value);

        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            this.tracer.createBaggage(KEY_1, VALUE_1);

            // WHEN
            this.propagator.inject(Objects.requireNonNull(tracer.currentTraceContext().context()), carrier, setter);

            // THEN
            then(carrier.get(KEY_1)).isEqualTo(VALUE_1);
        }

        // WHEN
        Span extractedSpan = propagator.extract(carrier, Map::get).start();

        // THEN
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(extractedSpan)) {
            then(tracer.getBaggage(KEY_1).get(extractedSpan.context())).isEqualTo(VALUE_1);
            try (BaggageInScope baggageInScope = tracer.getBaggage(KEY_1).makeCurrent()) {
                then(baggageInScope.get()).isEqualTo(VALUE_1);
            }
        }
    }

    @Test
    void baggageWithContextPropagation() throws InterruptedException, ExecutionException, TimeoutException {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ObservationAwareSpanThreadLocalAccessor(tracer));
        Hooks.enableAutomaticContextPropagation();
        ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        Scheduler scheduler = Schedulers.fromExecutor(executorService);

        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            try (BaggageInScope scope = this.tracer.createBaggageInScope(KEY_1, VALUE_1)) {
                String baggageOutside = this.tracer.getBaggage(KEY_1).get();
                then(baggageOutside).isEqualTo(VALUE_1);
                log.info(
                        "BAGGAGE OUTSIDE OF REACTOR [" + baggageOutside + "], thread [" + Thread.currentThread() + "]");
                String baggageFromReactor = Mono.just(KEY_1)
                    .delayElement(Duration.ofMillis(1), scheduler)
                    .tap(Micrometer.observation(observationRegistry))
                    .publishOn(Schedulers.boundedElastic())
                    .flatMap(s -> Mono.just(this.tracer.getBaggage(s).get())
                        .doOnNext(baggage -> log.info("BAGGAGE IN OF REACTOR [" + baggageOutside + "], thread ["
                                + Thread.currentThread() + "]")))
                    .block();
                then(baggageFromReactor).isEqualTo(VALUE_1);
            }
            then(tracer.currentSpan()).isEqualTo(span);
        }
        then(tracer.currentSpan()).isNull();

        Future<Boolean> submit = executorService.submit(() -> tracer.currentSpan() == null);
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();
    }

    @Test
    void baggageWithContextPropagationWithLegacyApi() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ObservationAwareSpanThreadLocalAccessor(tracer));
        Hooks.enableAutomaticContextPropagation();

        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            try (BaggageInScope scope = this.tracer.createBaggage(KEY_1, VALUE_1).makeCurrent()) {
                String baggageOutside = this.tracer.getBaggage(KEY_1).get();
                then(baggageOutside).isEqualTo(VALUE_1);
                log.info(
                        "BAGGAGE OUTSIDE OF REACTOR [" + baggageOutside + "], thread [" + Thread.currentThread() + "]");
                String baggageFromReactor = Mono.just(KEY_1)
                    .publishOn(Schedulers.boundedElastic())
                    .flatMap(s -> Mono.just(this.tracer.getBaggage(s).get())
                        .doOnNext(baggage -> log.info("BAGGAGE IN OF REACTOR [" + baggageOutside + "], thread ["
                                + Thread.currentThread() + "]")))
                    .block();
                then(baggageFromReactor).isEqualTo(VALUE_1);
            }
        }
    }

    @AfterAll
    static void clear() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor.KEY);
    }

    @Test
    void baggageTagKey() {
        ScopedSpan span = this.tracer.startScopedSpan("call1");
        try {
            try (BaggageInScope scope7 = this.tracer.createBaggageInScope(TAG_KEY, TAG_VALUE)) {
                // span should get tagged with baggage
            }
        }
        catch (RuntimeException | Error ex) {
            span.error(ex);
            throw ex;
        }
        finally {
            span.end();
        }

        then(spanHandler.spans()).hasSize(1);
        MutableSpan mutableSpan = spanHandler.spans().get(0);
        then(mutableSpan.tags().get(TAG_KEY)).isEqualTo(TAG_VALUE);
    }

    @Test
    void baggageWithObservationApiWithRemoteFields() {
        Observation observation = Observation.start("foo", observationRegistry)
            .lowCardinalityKeyValue(KEY_1, TAG_VALUE)
            .highCardinalityKeyValue(OBSERVATION_BAGGAGE_KEY, OBSERVATION_BAGGAGE_VALUE);
        then(tracer.getBaggage(KEY_1).get()).isNull();
        then(tracer.getBaggage(OBSERVATION_BAGGAGE_KEY).get()).isNull();

        try (Scope scope = observation.openScope()) {
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(TAG_VALUE);
            then(tracer.getBaggage(OBSERVATION_BAGGAGE_KEY).get()).isEqualTo(OBSERVATION_BAGGAGE_VALUE);
        }

        then(tracer.currentSpan()).isNull();
        then(tracer.getBaggage(KEY_1).get()).isNull();
        then(tracer.getBaggage(OBSERVATION_BAGGAGE_KEY).get()).isNull();
        observation.stop();

        then(spanHandler.spans()).hasSize(1);
        MutableSpan mutableSpan = spanHandler.spans().get(0);
        then(mutableSpan.tags().get(KEY_1)).isEqualTo(TAG_VALUE);
        then(mutableSpan.tags().get(OBSERVATION_BAGGAGE_KEY)).isEqualTo(OBSERVATION_BAGGAGE_VALUE);
    }

    @Test
    void baggageTagKeyWithLegacyApi() {
        ScopedSpan span = this.tracer.startScopedSpan("call1");
        try {
            try (BaggageInScope scope7 = this.tracer.createBaggage(TAG_KEY, TAG_VALUE).makeCurrent()) {
                // span should get tagged with baggage
            }
        }
        catch (RuntimeException | Error ex) {
            span.error(ex);
            throw ex;
        }
        finally {
            span.end();
        }

        then(spanHandler.spans()).hasSize(1);
        MutableSpan mutableSpan = spanHandler.spans().get(0);
        then(mutableSpan.tags().get(TAG_KEY)).isEqualTo(TAG_VALUE);
    }

    @Test
    void should_keep_extracted_baggage_after_nested_baggage_scope_was_closed() {
        Span extractedSpan = extractedSpanWithBaggage(KEY_1, VALUE_1);
        try (Tracer.SpanInScope scope1 = tracer.withSpan(extractedSpan)) {
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
            try (BaggageInScope baggage = tracer.createBaggageInScope(TAG_KEY, TAG_VALUE)) {
                then(tracer.getBaggage(TAG_KEY).get()).isEqualTo(TAG_VALUE);
                then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
            }
            then(tracer.getBaggage(TAG_KEY).get()).isNull();
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
        }
        then(tracer.getBaggage(KEY_1).get()).isNull();
        then(tracer.getAllBaggage(extractedSpan.context())).containsEntry(KEY_1, VALUE_1).doesNotContainKey(TAG_KEY);

        try (Tracer.SpanInScope scope2 = tracer.withSpan(extractedSpan)) {
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
            then(tracer.getBaggage(TAG_KEY).get()).isNull();
        }
        extractedSpan.end();
    }

    @Test
    void should_keep_extracted_baggage_after_nested_baggage_scope_for_same_key_was_closed() {
        Span extractedSpan = extractedSpanWithBaggage(KEY_1, VALUE_1);
        try (Tracer.SpanInScope scope1 = tracer.withSpan(extractedSpan)) {
            try (BaggageInScope baggage = tracer.createBaggageInScope(KEY_1, "overridden")) {
                then(tracer.getBaggage(KEY_1).get()).isEqualTo("overridden");
            }
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
        }
        then(tracer.getAllBaggage(extractedSpan.context())).containsEntry(KEY_1, VALUE_1);

        try (Tracer.SpanInScope scope2 = tracer.withSpan(extractedSpan)) {
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
        }
        extractedSpan.end();
    }

    @Test
    void should_restore_outer_baggage_of_same_trace_context_when_nested_baggage_scope_is_closed() {
        Span span = tracer.nextSpan().name("span").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            try (BaggageInScope outer = tracer.createBaggageInScope(span.context(), KEY_1, "outer")) {
                try (BaggageInScope inner = tracer.createBaggageInScope(span.context(), KEY_1, "inner")) {
                    then(tracer.getBaggage(KEY_1).get()).isEqualTo("inner");
                    then(tracer.getBaggage(KEY_1).get(span.context())).isEqualTo("inner");
                    then(tracer.getAllBaggage(span.context())).containsEntry(KEY_1, "inner");
                }
                then(tracer.getBaggage(KEY_1).get()).isEqualTo("outer");
                then(tracer.getBaggage(KEY_1).get(span.context())).isEqualTo("outer");
                then(tracer.getAllBaggage(span.context())).containsEntry(KEY_1, "outer");
            }
            then(tracer.getBaggage(KEY_1).get()).isNull();
            then(tracer.getBaggage(KEY_1).get(span.context())).isNull();
            then(tracer.getAllBaggage(span.context())).doesNotContainKey(KEY_1);
        }
        span.end();
    }

    @Test
    void should_restore_outer_baggage_of_trace_context_not_in_scope_when_nested_baggage_scope_is_closed() {
        Span span = tracer.nextSpan().name("span").start();
        try (BaggageInScope outer = tracer.createBaggageInScope(span.context(), KEY_1, "outer")) {
            try (BaggageInScope inner = tracer.createBaggageInScope(span.context(), KEY_1, "inner")) {
                then(tracer.getBaggage(KEY_1).get(span.context())).isEqualTo("inner");
                then(tracer.getAllBaggage(span.context())).containsEntry(KEY_1, "inner");
            }
            then(tracer.getBaggage(KEY_1).get(span.context())).isEqualTo("outer");
            then(tracer.getAllBaggage(span.context())).containsEntry(KEY_1, "outer");
        }
        then(tracer.getBaggage(KEY_1).get(span.context())).isNull();
        then(tracer.getAllBaggage(span.context())).doesNotContainKey(KEY_1);
        span.end();
    }

    // Same as ScopeCompatibilityKit on main, which is not available on this branch
    @Test
    void should_isolate_baggage_per_span_and_unmerge_from_thread_on_scope_close() {
        Span span1 = tracer.nextSpan().name("span1").start();
        try (Tracer.SpanInScope ws1 = tracer.withSpan(span1)) {
            try (BaggageInScope b1 = tracer.createBaggageInScope(span1.context(), KEY_1, "value1")) {
                then(tracer.getBaggage(KEY_1).get()).isEqualTo("value1");
                then(Objects.requireNonNull(tracer.getBaggage(span1.context(), KEY_1)).get()).isEqualTo("value1");

                Span span2 = tracer.nextSpan().name("span2").start();
                try (Tracer.SpanInScope ws2 = tracer.withSpan(span2)) {
                    try (BaggageInScope b2 = tracer.createBaggageInScope(span2.context(), KEY_1, "value2")) {
                        then(tracer.getBaggage(KEY_1).get()).isEqualTo("value2");
                        then(Objects.requireNonNull(tracer.getBaggage(span2.context(), KEY_1)).get())
                            .isEqualTo("value2");
                    }
                    then(tracer.getBaggage(KEY_1).get()).isEqualTo("value1");
                }
                span2.end();

                then(tracer.getBaggage(KEY_1).get()).isEqualTo("value1");
                then(Objects.requireNonNull(tracer.getBaggage(span1.context(), KEY_1)).get()).isEqualTo("value1");
            }
            then(tracer.getBaggage(KEY_1).get()).isNull();
        }
        span1.end();
        then(tracer.getBaggage(KEY_1).get()).isNull();
    }

    // Same as ScopeCompatibilityKit on main, which is not available on this branch
    @Test
    void should_keep_baggage_of_span_after_baggage_scope_was_closed_in_previous_scope() {
        Span parent = tracer.nextSpan().name("parent").start();
        Span child;
        try (Tracer.SpanInScope parentScope = tracer.withSpan(parent);
                BaggageInScope parentBaggage = tracer.createBaggageInScope(parent.context(), KEY_1, "parent")) {
            child = tracer.nextSpan().name("child").start();
        }
        try (Tracer.SpanInScope scope1 = tracer.withSpan(child)) {
            then(tracer.getBaggage(KEY_1).get()).isEqualTo("parent");
            try (BaggageInScope baggage = tracer.createBaggageInScope(KEY_1, "child")) {
                then(tracer.getBaggage(KEY_1).get()).isEqualTo("child");
            }
            then(tracer.getBaggage(KEY_1).get()).isEqualTo("parent");
        }
        then(tracer.getBaggage(KEY_1).get()).isNull();

        try (Tracer.SpanInScope scope2 = tracer.withSpan(child)) {
            then(tracer.currentSpan()).isEqualTo(child);
            then(tracer.getBaggage(KEY_1).get()).isEqualTo("parent");
        }
        child.end();
        parent.end();
    }

    // Same as ScopeCompatibilityKit on main, which is not available on this branch
    @Test
    void should_open_scope_for_span_inside_baggage_scope_for_that_span() {
        Span span = tracer.nextSpan().name("span").start();
        try (BaggageInScope baggage = tracer.createBaggageInScope(span.context(), KEY_1, "bar")) {
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                then(tracer.currentSpan()).isEqualTo(span);
                then(tracer.getBaggage(KEY_1).get()).isEqualTo("bar");
            }
        }
        span.end();
    }

    // Same as ScopeCompatibilityKit on main, which is not available on this branch
    @Test
    void should_open_scope_for_span_inside_baggage_scope_for_that_span_while_other_span_is_in_scope() {
        Span other = tracer.nextSpan().name("other").start();
        Span span = tracer.nextSpan().name("span").start();
        try (Tracer.SpanInScope otherScope = tracer.withSpan(other)) {
            try (BaggageInScope baggage = tracer.createBaggageInScope(span.context(), KEY_1, "bar")) {
                try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                    then(tracer.currentSpan()).isEqualTo(span);
                    then(tracer.getBaggage(KEY_1).get()).isEqualTo("bar");
                }
                then(tracer.currentSpan()).isEqualTo(other);
            }
        }
        span.end();
        other.end();
    }

    private Span extractedSpanWithBaggage(String key, String value) {
        Map<String, String> carrier = new HashMap<>();
        Span span = tracer.nextSpan().name("remote").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span);
                BaggageInScope baggage = tracer.createBaggageInScope(key, value)) {
            propagator.inject(Objects.requireNonNull(tracer.currentTraceContext().context()), carrier,
                    (map, k, v) -> Objects.requireNonNull(map).put(k, v));
        }
        span.end();
        then(tracer.currentSpan()).isNull();
        then(tracer.getBaggage(key).get()).isNull();
        return propagator.extract(carrier, Map::get).start();
    }

}
