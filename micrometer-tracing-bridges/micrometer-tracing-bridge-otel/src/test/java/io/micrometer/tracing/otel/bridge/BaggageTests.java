/**
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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.propagation.BaggageTextMapPropagator;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.then;

class BaggageTests {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(BaggageTests.class);

    public static final String KEY_1 = "key1";

    public static final String VALUE_1 = "value1";

    public static final String KEY_2 = "key2";

    public static final String VALUE_2 = "value2";

    public static final String TAG_KEY = "tagKey";

    public static final String TAG_VALUE = "tagValue";

    public static final String OBSERVATION_BAGGAGE_KEY = "observationKey";

    public static final String OBSERVATION_BAGGAGE_VALUE = "observationValue";

    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    // tag::baggageManager[]
    // There will be 3 baggage keys in total, 2 for remote fields and 1 as tag field
    OtelBaggageManager otelBaggageManager = new OtelBaggageManager(otelCurrentTraceContext,
            Arrays.asList(KEY_1, OBSERVATION_BAGGAGE_KEY), Collections.singletonList(TAG_KEY));

    // end::baggageManager[]

    ContextPropagators contextPropagators = ContextPropagators
        .create(TextMapPropagator.composite(W3CBaggagePropagator.getInstance(), W3CTraceContextPropagator.getInstance(),
                new BaggageTextMapPropagator(Arrays.asList(KEY_1, OBSERVATION_BAGGAGE_KEY), otelBaggageManager)));

    OtelPropagator propagator = new OtelPropagator(contextPropagators, otelTracer);

    Tracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
    }, otelBaggageManager);

    ObservationRegistry observationRegistry = ObservationThreadLocalAccessor.getInstance().getObservationRegistry();

    @BeforeEach
    void setupHandler() {
        // tag::observationRegistrySetup[]
        // For automated baggage scope creation the tracing handler is required
        observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
        // end::observationRegistrySetup[]
    }

    @Test
    void canSetAndGetBaggage() {
        // GIVEN
        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            // WHEN
            try (BaggageInScope bs1 = this.tracer.createBaggageInScope(KEY_1, VALUE_1);
                    BaggageInScope bs2 = this.tracer.createBaggageInScope(KEY_2, VALUE_2)) {
                // THEN
                then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
                then(tracer.getBaggage(KEY_2).get()).isEqualTo(VALUE_2);
            }
        }
    }

    @Test
    void canSetAndGetBaggageWithLegacyApi() {
        // GIVEN
        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            // WHEN
            try (BaggageInScope bs = this.tracer.createBaggage(KEY_1, VALUE_1).makeCurrent()) {
                // THEN
                then(tracer.getBaggage(KEY_1).get()).isEqualTo(VALUE_1);
            }
        }
    }

    @Test
    void injectAndExtractKeepsTheBaggage() {
        // GIVEN
        Map<String, String> carrier = new HashMap<>();

        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            try (BaggageInScope bs = this.tracer.createBaggageInScope(KEY_1, VALUE_1)) {
                // WHEN
                this.propagator.inject(tracer.currentTraceContext().context(), carrier, Map::put);

                // THEN
                then(carrier.get(KEY_1)).isEqualTo(VALUE_1);
            }
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
                then(tracer.currentSpan()).isEqualTo(span);
            }
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

        then(spanExporter.getFinishedSpanItems()).hasSize(1);
        SpanData spanData = spanExporter.getFinishedSpanItems().get(0);
        then(spanData.getAttributes().get(AttributeKey.stringKey(TAG_KEY))).isEqualTo(TAG_VALUE);
    }

    @Test
    void baggageWithObservationApiWithRemoteFields() {
        // tag::observation[]
        // An observation with low and high cardinality keys
        // with key names equal to baggage key entries set on the baggage manager
        Observation observation = Observation.start("foo", observationRegistry)
            .lowCardinalityKeyValue(KEY_1, TAG_VALUE)
            .highCardinalityKeyValue(OBSERVATION_BAGGAGE_KEY, OBSERVATION_BAGGAGE_VALUE);
        // end::observation[]
        then(tracer.getBaggage(KEY_1).get()).isNull();
        then(tracer.getBaggage(OBSERVATION_BAGGAGE_KEY).get()).isNull();

        // tag::observationScope[]
        // There is no baggage here
        try (Scope scope = observation.openScope()) {
            // Baggage here will be automatically put in scope
            then(tracer.getBaggage(KEY_1).get()).isEqualTo(TAG_VALUE);
            then(tracer.getBaggage(OBSERVATION_BAGGAGE_KEY).get()).isEqualTo(OBSERVATION_BAGGAGE_VALUE);
        }
        // There is no baggage here
        // end::observationScope[]
        then(tracer.currentSpan()).isNull();
        then(tracer.getBaggage(KEY_1).get()).isNull();
        then(tracer.getBaggage(OBSERVATION_BAGGAGE_KEY).get()).isNull();
        observation.stop();

        then(spanExporter.getFinishedSpanItems()).hasSize(1);
        SpanData spanData = spanExporter.getFinishedSpanItems().get(0);
        then(spanData.getAttributes().get(AttributeKey.stringKey(KEY_1))).isEqualTo(TAG_VALUE);
        then(spanData.getAttributes().get(AttributeKey.stringKey(OBSERVATION_BAGGAGE_KEY)))
            .isEqualTo(OBSERVATION_BAGGAGE_VALUE);
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

        then(spanExporter.getFinishedSpanItems()).hasSize(1);
        SpanData spanData = spanExporter.getFinishedSpanItems().get(0);
        then(spanData.getAttributes().get(AttributeKey.stringKey(TAG_KEY))).isEqualTo(TAG_VALUE);
    }

}
