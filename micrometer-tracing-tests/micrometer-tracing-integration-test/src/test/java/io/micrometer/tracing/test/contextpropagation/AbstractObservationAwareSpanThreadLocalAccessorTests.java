/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.tracing.test.contextpropagation;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.micrometer.tracing.contextpropagation.*;
import io.micrometer.tracing.contextpropagation.reactor.ReactorBaggage;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

abstract class AbstractObservationAwareSpanThreadLocalAccessorTests {

    static final Logger log = LoggerFactory.getLogger(AbstractObservationAwareSpanThreadLocalAccessorTests.class);

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    // tag::setup[]
    ContextRegistry contextRegistry = ContextRegistry.getInstance();

    ObservationAwareSpanThreadLocalAccessor accessor;

    ObservationAwareBaggageThreadLocalAccessor observationAwareBaggageThreadLocalAccessor;

    // end::setup[]

    ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

    ExecutorService executorService = ContextExecutorService.wrap(Executors.newSingleThreadExecutor(),
            () -> ContextSnapshot.captureAll(contextRegistry));

    abstract Tracer getTracer();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(getTracer()));
        // tag::setup_accessors[]
        accessor = new ObservationAwareSpanThreadLocalAccessor(observationRegistry, getTracer());
        observationAwareBaggageThreadLocalAccessor = new ObservationAwareBaggageThreadLocalAccessor(observationRegistry,
                getTracer());
        contextRegistry.loadThreadLocalAccessors()
            .registerThreadLocalAccessor(accessor)
            .registerThreadLocalAccessor(observationAwareBaggageThreadLocalAccessor);
        Hooks.enableAutomaticContextPropagation();
        // end::setup_accessors[]
    }

    @AfterEach
    void close() {
        executorService.shutdown();
        threadPoolTaskExecutor.shutdown();
        then(getTracer().currentSpan()).isNull();
        then(observationRegistry.getCurrentObservationScope()).isNull();
        Awaitility.await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> BDDAssertions.then(TestObservationAwareSpanThreadLocalAccessor.spanActions(accessor))
                .isEmpty());
        Awaitility.await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> BDDAssertions
                .then(TestBaggageThreadLocalAccessor.baggageInScope(observationAwareBaggageThreadLocalAccessor))
                .isEmpty());
        contextRegistry.removeThreadLocalAccessor(ObservationThreadLocalAccessor.KEY);
        contextRegistry.removeThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor.KEY);
        contextRegistry.removeThreadLocalAccessor(ObservationAwareBaggageThreadLocalAccessor.KEY);
        Hooks.disableAutomaticContextPropagation();
    }

    @Test
    void asyncTracingTestWithObservationAndManualSpans()
            throws ExecutionException, InterruptedException, TimeoutException {
        Observation firstSpan = Observation.createNotStarted("First span", observationRegistry)
            .highCardinalityKeyValue("test", "test");
        try (Observation.Scope scope = firstSpan.start().openScope()) {
            logWithSpan("Async in test with observation - before call");

            Span secondSpan = getTracer().nextSpan().name("Second span").tag("test", "test");
            try (Tracer.SpanInScope scope2 = getTracer().withSpan(secondSpan.start())) {
                try (BaggageInScope baggageInScope = getTracer().createBaggageInScope("tenant", "tenantValue")) {
                    logWithSpan("Async in test with span - before call");
                    Future<String> future = executorService.submit(this::asyncCall);
                    String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
                    logWithSpan("Async in test with span - after call");
                    then(spanIdFromFuture).isEqualTo(secondSpan.context().spanId());

                    Future<String> futureBaggage = executorService.submit(this::asyncBaggageCall);
                    String baggageFromFuture = futureBaggage.get(1, TimeUnit.SECONDS);
                    then(baggageFromFuture).isEqualTo("tenantValue");
                }
            }
            finally {
                secondSpan.end();
            }

            logWithSpan("Async in test with observation - after call");
        }
        finally {
            firstSpan.stop();
        }

        Future<Boolean> submit = executorService.submit(() -> {
            logWithSpan("There should be no span here");
            return getTracer().currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();
    }

    @Test
    void asyncTracingTestWithManualSpansAndObservation()
            throws ExecutionException, InterruptedException, TimeoutException {
        Span firstSpan = getTracer().nextSpan().name("First span").tag("test", "test");
        try (Tracer.SpanInScope scope2 = getTracer().withSpan(firstSpan.start())) {
            try (BaggageInScope baggageInScope = getTracer().createBaggageInScope("tenant", "tenantValue")) {
                logWithSpan("Async in test with span - before call");
                Observation observation = Observation.createNotStarted("Second span", observationRegistry)
                    .highCardinalityKeyValue("test", "test");
                try (Observation.Scope scope = observation.start().openScope()) {
                    Span secondSpan = getTracer().currentSpan();
                    logWithSpan("Async in test with observation - before call");
                    Future<String> future = executorService.submit(this::asyncCall);
                    String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
                    then(spanIdFromFuture).isEqualTo(secondSpan.context().spanId());
                    logWithSpan("Async in test with observation - after call");

                    Future<String> futureBaggage = executorService.submit(this::asyncBaggageCall);
                    String baggageFromFuture = futureBaggage.get(1, TimeUnit.SECONDS);
                    then(baggageFromFuture).isEqualTo("tenantValue");
                }
                logWithSpan("Async in test with span - after call");

                Future<String> futureBaggage = executorService.submit(this::asyncBaggageCall);
                String baggageFromFuture = futureBaggage.get(1, TimeUnit.SECONDS);
                then(baggageFromFuture).isEqualTo("tenantValue");
            }
        }
        finally {
            firstSpan.end();
        }

        Future<Boolean> submit = executorService.submit(() -> {
            logWithSpan("There should be no span here");
            return getTracer().currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();
    }

    @Test
    void asyncTracingTestWithJustObservation() throws ExecutionException, InterruptedException, TimeoutException {
        Observation firstObservation = Observation.createNotStarted("First span", observationRegistry)
            .highCardinalityKeyValue("test", "test");
        try (Observation.Scope scope = firstObservation.start().openScope()) {
            logWithSpan("Async in test with span - before call");
            String currentSpanId = getTracer().currentSpan().context().spanId();
            Future<String> future = executorService.submit(this::asyncCall);
            String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
            logWithSpan("Async in test with span - after call");
            then(spanIdFromFuture).isEqualTo(currentSpanId);
        }
        finally {
            firstObservation.stop();
        }

        Future<Boolean> submit = executorService.submit(() -> {
            logWithSpan("There should be no span here");
            return getTracer().currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();
    }

    @Test
    void asyncTracingTestWithJustSpans() throws ExecutionException, InterruptedException, TimeoutException {
        Span secondSpan = getTracer().nextSpan().name("Second span").tag("test", "test");
        try (Tracer.SpanInScope scope2 = getTracer().withSpan(secondSpan.start())) {
            try (BaggageInScope baggageInScope = getTracer().createBaggageInScope("tenant", "tenantValue")) {
                logWithSpan("Async in test with span - before call");
                Future<String> future = executorService.submit(this::asyncCall);
                String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
                logWithSpan("Async in test with span - after call");
                then(spanIdFromFuture).isEqualTo(secondSpan.context().spanId());

                Future<String> futureBaggage = executorService.submit(this::asyncBaggageCall);
                String baggageFromFuture = futureBaggage.get(1, TimeUnit.SECONDS);
                then(baggageFromFuture).isEqualTo("tenantValue");
            }
        }
        finally {
            secondSpan.end();
        }

        Future<Boolean> submit = executorService.submit(() -> {
            logWithSpan("There should be no span here");
            return getTracer().currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();
    }

    @Test
    void threadPoolTaskExecutorTracingWithSpansAndBaggage() {
        threadPoolTaskExecutor.setTaskDecorator(runnable -> ContextSnapshotFactory.builder()
            .contextRegistry(contextRegistry)
            .build()
            .captureAll()
            .wrap(runnable));
        threadPoolTaskExecutor.initialize();

        Span span = getTracer().nextSpan().name("test").start();
        AtomicBoolean noError = new AtomicBoolean();

        try (SpanInScope ws = getTracer().withSpan(span)) {
            try (BaggageInScope baggageInScope = getTracer().createBaggageInScope("tenant", "tenantValue")) {
                String tenant2 = getTracer().getBaggage("tenant").get();
                then(tenant2).as("We're within the scope of baggage").isEqualTo("tenantValue");
                threadPoolTaskExecutor.submit(() -> {
                    String tenant = getTracer().getBaggage("tenant").get();
                    then(tenant).as("We're within the scope of baggage in a new thread").isEqualTo("tenantValue");
                    noError.set(true);
                    span.end();
                });
            }
            String tenant = getTracer().getBaggage("tenant").get();
            then(tenant).as("We're out of scope of baggage").isNull();
        }
        String tenant = getTracer().getBaggage("tenant").get();
        then(tenant).as("We're out of scope of baggage").isNull();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAtomic(noError, Matchers.is(true));
    }

    // @formatter:off
    @Test
    void onlyReactorPropagatesBaggageForDocs() {
        // tag::docs[]
        Hooks.enableAutomaticContextPropagation();
        Observation observation = Observation.start("parent", observationRegistry);

        List<String> hello = Mono.just("hello")
            .subscribeOn(Schedulers.single())
            .flatMap(s -> {
                Mono<List<String>> mono = Mono.defer(() -> Mono.just(Arrays.asList(
                    getTracer().getBaggage("tenant").get(),
                    getTracer().getBaggage("tenant2").get())
                ));
            return mono.subscribeOn(Schedulers.parallel())
                .contextWrite(ReactorBaggage.append("tenant", s + ":baggage")); // Appends baggage to existing one (tenant2:baggage2)
        })
            .contextWrite(Context.of(ObservationThreadLocalAccessor.KEY, observation, // Puts observation to Reactor Context
                    ObservationAwareBaggageThreadLocalAccessor.KEY, new BaggageToPropagate("tenant2", "baggage2") // Puts baggage to Reactor Context
                ))
            .block();
        // end::docs[]

        assertThat(hello).hasSize(2);
        assertThat(hello.get(0)).isEqualTo("hello:baggage");
        assertThat(hello.get(1)).isEqualTo("baggage2");
    }
    // @formatter:on

    @ParameterizedTest(name = "{index} Baggage gets propagated through reactor with thread hop enabled [{0}]")
    @ValueSource(booleans = { true, false })
    void onlyReactorPropagatesBaggage(boolean threadHopAfterBaggageSet) {
        Hooks.enableAutomaticContextPropagation();
        Observation observation = Observation.start("parent", observationRegistry);

        List<String> hello = Mono.just("hello").subscribeOn(Schedulers.single()).flatMap(s -> {
            Mono<List<String>> mono = Mono.defer(() -> {
                log.info("In mono defer, current span [" + getTracer().currentSpan() + "], all baggage ["
                        + getTracer().getAllBaggage() + "]");
                return Mono.just(
                        Arrays.asList(getTracer().getBaggage("tenant").get(), getTracer().getBaggage("tenant2").get()));
            });
            return (threadHopAfterBaggageSet ? mono.subscribeOn(Schedulers.parallel()) : mono)
                .contextWrite(ReactorBaggage.append("tenant", s + ":baggage"));
        })
            .contextWrite(Context.of(ObservationThreadLocalAccessor.KEY, observation,
                    ObservationAwareBaggageThreadLocalAccessor.KEY, new BaggageToPropagate("tenant2", "baggage2")))
            .block();

        assertThat(hello).hasSize(2);
        assertThat(hello.get(0)).isEqualTo("hello:baggage");
        assertThat(hello.get(1)).isEqualTo("baggage2");
    }

    @Test
    @Disabled("Fix me")
    void onlyReactorPropagatesBaggageWithContextCapture() {
        Span span = getTracer().nextSpan().name("test").start();

        Mono<String> mono = Mono.defer(() -> Mono.just("asd"));
        try (SpanInScope ws = getTracer().withSpan(span)) {
            try (BaggageInScope baggageInScope = getTracer().createBaggageInScope("tenant", "tenantValue")) {
                // mono = mono.contextCapture();
                ContextSnapshot contextSnapshot = ContextSnapshotFactory.builder()
                    .clearMissing(true)
                    .build()
                    .captureAll();
                mono = mono.contextWrite(contextSnapshot::updateContext);
                // mono.contextCaptureNow(); !
            }
        }
        span.end();

        String tenant = mono.map(s -> getTracer().getBaggage("tenant").get()).block();
        assertThat(tenant).isEqualTo("tenantValue");
    }

    @Test
    @Disabled("Fix me")
    void onlyReactorPropagatesBaggageWithContextCaptureAndObservation() {
        Observation observation = Observation.start("asd", observationRegistry);

        Mono<String> mono = Mono.defer(() -> Mono.just("asd"));
        try (BaggageInScope baggageInScope = getTracer().createBaggageInScope("tenant", "tenantValue")) {
            // mono = mono.contextCapture();
            ContextSnapshot contextSnapshot = ContextSnapshotFactory.builder().clearMissing(true).build().captureAll();
            mono = mono.contextWrite(contextSnapshot::updateContext);
            // mono.contextCaptureNow(); !
        }
        String tenant = mono.map(s -> getTracer().getBaggage("tenant").get())
            .contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation))
            .block();

        assertThat(tenant).isEqualTo("tenantValue");
    }

    @Test
    void observationAwareBaggageThreadLocalAccessorSetsAndClosesBaggageToPropagate() throws ReflectiveOperationException {
        then(getTracer().currentTraceContext().context()).isNull();

        Observation.createNotStarted("First span", observationRegistry).observeChecked(
            () -> {
                then(getTracer().currentTraceContext().context()).isNotNull();

                BaggageToPropagate baggageToPropagate = new BaggageToPropagate("tenant", "tenantValue", "tenant2", "tenant2Value");
                observationAwareBaggageThreadLocalAccessor.setValue(baggageToPropagate);
                Method closeCurrentScope = ObservationAwareBaggageThreadLocalAccessor.class.getDeclaredMethod("closeCurrentScope");
                closeCurrentScope.setAccessible(true);
                return closeCurrentScope.invoke(observationAwareBaggageThreadLocalAccessor);
            }
        );

        then(getTracer().currentTraceContext().context()).isNull();
    }

    private String asyncCall() {
        logWithSpan("TASK EXECUTOR");
        if (getTracer().currentSpan() == null) {
            throw new AssertionError("Current span must not be null. Context propagation failed");
        }
        return getTracer().currentSpan().context().spanId();
    }

    private String asyncBaggageCall() {
        logWithSpan("TASK EXECUTOR BAGGAGE");
        if (getTracer().getBaggage("tenant").get() == null) {
            throw new AssertionError("Baggage for <tenant> key is empty. Context propagation failed");
        }
        return getTracer().getBaggage("tenant").get();
    }

    private void logWithSpan(String text) {
        log.info(text + ". Current span ["
                + (getTracer().currentSpan() != null ? getTracer().currentSpan().context().toString() : null) + "]");
    }

}
