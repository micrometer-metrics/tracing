/*
 * Copyright 2023 VMware, Inc.
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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.then;

class ObservationAwareSpanThreadLocalAccessorTests {

    static final Logger log = LoggerFactory.getLogger(ObservationAwareSpanThreadLocalAccessorTests.class);

    SimpleTracer tracer = new SimpleTracer();

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    ContextRegistry contextRegistry = new ContextRegistry();

    ExecutorService executorService = ContextExecutorService.wrap(Executors.newSingleThreadExecutor(),
            () -> ContextSnapshot.captureAll(contextRegistry));

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
        contextRegistry.loadThreadLocalAccessors()
            .registerThreadLocalAccessor(new ObservationAwareSpanThreadLocalAccessor(tracer));
    }

    @AfterEach
    void close() {
        executorService.shutdown();
        then(tracer.currentSpan()).isNull();
        then(observationRegistry.getCurrentObservationScope()).isNull();
    }

    @Test
    void asyncTracingTestWithObservationAndManualSpans()
            throws ExecutionException, InterruptedException, TimeoutException {
        Observation firstSpan = Observation.createNotStarted("First span", observationRegistry)
            .highCardinalityKeyValue("test", "test");
        try (Observation.Scope scope = firstSpan.start().openScope()) {
            logWithSpan("Async in test with observation - before call");

            SimpleSpan secondSpan = tracer.nextSpan().name("Second span").tag("test", "test");
            try (Tracer.SpanInScope scope2 = this.tracer.withSpan(secondSpan.start())) {
                logWithSpan("Async in test with span - before call");
                Future<String> future = executorService.submit(this::asyncCall);
                String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
                logWithSpan("Async in test with span - after call");
                then(spanIdFromFuture).isEqualTo(secondSpan.context().spanId());
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
            return tracer.currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();

    }

    @Test
    void asyncTracingTestWithManualSpansAndObservation()
            throws ExecutionException, InterruptedException, TimeoutException {
        SimpleSpan firstSpan = tracer.nextSpan().name("First span").tag("test", "test");
        try (Tracer.SpanInScope scope2 = this.tracer.withSpan(firstSpan.start())) {
            logWithSpan("Async in test with span - before call");
            Observation observation = Observation.createNotStarted("Second span", observationRegistry)
                .highCardinalityKeyValue("test", "test");
            try (Observation.Scope scope = observation.start().openScope()) {
                SimpleSpan secondSpan = tracer.currentSpan();
                logWithSpan("Async in test with observation - before call");
                Future<String> future = executorService.submit(this::asyncCall);
                String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
                then(spanIdFromFuture).isEqualTo(secondSpan.context().spanId());
                logWithSpan("Async in test with observation - after call");
            }
            logWithSpan("Async in test with span - after call");
        }
        finally {
            firstSpan.end();
        }

        Future<Boolean> submit = executorService.submit(() -> {
            logWithSpan("There should be no span here");
            return tracer.currentSpan() == null;
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
            String currentSpanId = tracer.currentSpan().context().spanId();
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
            return tracer.currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();

    }

    @Test
    void asyncTracingTestWithJustSpans() throws ExecutionException, InterruptedException, TimeoutException {
        Span secondSpan = tracer.nextSpan().name("Second span").tag("test", "test");
        try (Tracer.SpanInScope scope2 = this.tracer.withSpan(secondSpan.start())) {
            logWithSpan("Async in test with span - before call");
            Future<String> future = executorService.submit(this::asyncCall);
            String spanIdFromFuture = future.get(1, TimeUnit.SECONDS);
            logWithSpan("Async in test with span - after call");
            then(spanIdFromFuture).isEqualTo(secondSpan.context().spanId());
        }
        finally {
            secondSpan.end();
        }

        Future<Boolean> submit = executorService.submit(() -> {
            logWithSpan("There should be no span here");
            return tracer.currentSpan() == null;
        });
        boolean noCurrentSpan = submit.get(1, TimeUnit.SECONDS);

        Assertions.assertThat(noCurrentSpan).isTrue();

    }

    private String asyncCall() {
        logWithSpan("TASK EXECUTOR");
        if (tracer.currentSpan() == null) {
            throw new AssertionError("Current span must not be null. Context propagation failed");
        }
        return tracer.currentSpan().context().spanId();
    }

    private void logWithSpan(String text) {
        log.info(text + ". Current span ["
                + (tracer.currentSpan() != null ? tracer.currentSpan().context().toString() : null) + "]");
    }

}
