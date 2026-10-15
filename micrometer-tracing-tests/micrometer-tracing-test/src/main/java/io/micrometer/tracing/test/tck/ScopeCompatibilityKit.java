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
package io.micrometer.tracing.test.tck;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Test Compatibility Kit for testing tracer scope implementations and context
 * propagation.
 *
 * @author Marcin Grzejszczak
 * @author Tommy Ludwig
 * @since 1.8.0
 */
public abstract class ScopeCompatibilityKit {

    /**
     * Observation registry configured for testing.
     */
    protected ObservationRegistry observationRegistry = ObservationRegistry.create();

    /**
     * Subclasses provide the tracer under test.
     * @return tracer implementation under test
     */
    protected abstract Tracer getTracer();

    /**
     * Creates an {@link ObservationRegistry} for the test.
     * @return observation registry instance
     */
    protected ObservationRegistry createObservationRegistry() {
        return ObservationRegistry.create();
    }

    @BeforeEach
    void setupScopeCompatibilityKit() {
        this.observationRegistry = createObservationRegistry();
        this.observationRegistry.observationConfig()
            .observationHandler(new DefaultTracingObservationHandler(getTracer()));
        Hooks.enableAutomaticContextPropagation();
        ObservationThreadLocalAccessor.getInstance().setObservationRegistry(this.observationRegistry);
    }

    @AfterEach
    void cleanupScopeCompatibilityKit() {
        try {
            then(getTracer().currentSpan()).as("Current span must be null after test").isNull();
            then(this.observationRegistry.getCurrentObservationScope())
                .as("Current observation scope must be null after test")
                .isNull();
        }
        finally {
            Hooks.disableAutomaticContextPropagation();
        }
    }

    @Test
    void should_nest_and_restore_spans_with_tracer() {
        Span parent = getTracer().nextSpan().name("parent").start();
        try (Tracer.SpanInScope parentScope = getTracer().withSpan(parent)) {
            then(getTracer().currentSpan()).isEqualTo(parent);
            Span child = getTracer().nextSpan().name("child").start();
            try (Tracer.SpanInScope childScope = getTracer().withSpan(child)) {
                then(getTracer().currentSpan()).isEqualTo(child);
            }
            then(getTracer().currentSpan()).isEqualTo(parent);
            child.end();
        }
        parent.end();
        then(getTracer().currentSpan()).isNull();
    }

    @Test
    void should_nest_and_restore_spans_with_current_trace_context() {
        CurrentTraceContext currentTraceContext = getTracer().currentTraceContext();
        Span parent = getTracer().nextSpan().name("parent").start();
        try (CurrentTraceContext.Scope parentScope = currentTraceContext.newScope(parent.context())) {
            then(getTracer().currentSpan()).isEqualTo(parent);
            then(currentTraceContext.context()).isEqualTo(parent.context());

            Span child = getTracer().nextSpan().name("child").start();
            try (CurrentTraceContext.Scope childScope = currentTraceContext.newScope(child.context())) {
                then(getTracer().currentSpan()).isEqualTo(child);
                then(currentTraceContext.context()).isEqualTo(child.context());
            }

            then(getTracer().currentSpan()).isEqualTo(parent);
            then(currentTraceContext.context()).isEqualTo(parent.context());
            child.end();
        }
        parent.end();
        then(getTracer().currentSpan()).isNull();
        then(currentTraceContext.context()).isNull();
    }

    @Test
    void should_support_maybe_scope() {
        CurrentTraceContext currentTraceContext = getTracer().currentTraceContext();
        Span span = getTracer().nextSpan().name("span").start();
        try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(span.context())) {
            then(getTracer().currentSpan()).isEqualTo(span);

            try (CurrentTraceContext.Scope nullScope = currentTraceContext.maybeScope(null)) {
                then(getTracer().currentSpan()).isNull();
                then(currentTraceContext.context()).isNull();
            }

            then(getTracer().currentSpan()).isEqualTo(span);
            then(currentTraceContext.context()).isEqualTo(span.context());
        }
        span.end();
        then(getTracer().currentSpan()).isNull();
    }

    @Test
    void should_isolate_baggage_per_span_and_unmerge_from_thread_on_scope_close() {
        Span span1 = getTracer().nextSpan().name("span1").start();
        try (Tracer.SpanInScope ws1 = getTracer().withSpan(span1)) {
            try (BaggageInScope b1 = getTracer().createBaggageInScope(span1.context(), "foo", "value1")) {
                then(getTracer().getBaggage("foo").get()).isEqualTo("value1");
                Baggage span1Baggage = getTracer().getBaggage(span1.context(), "foo");
                assertThat(span1Baggage).isNotNull();
                then(span1Baggage.get()).isEqualTo("value1");

                Span span2 = getTracer().nextSpan().name("span2").start();
                try (Tracer.SpanInScope ws2 = getTracer().withSpan(span2)) {
                    try (BaggageInScope b2 = getTracer().createBaggageInScope(span2.context(), "foo", "value2")) {
                        then(getTracer().getBaggage("foo").get()).isEqualTo("value2");
                        Baggage span2Baggage = getTracer().getBaggage(span2.context(), "foo");
                        assertThat(span2Baggage).isNotNull();
                        then(span2Baggage.get()).isEqualTo("value2");
                    }
                    // Closing b2 restores previous value on thread
                    then(getTracer().getBaggage("foo").get()).isEqualTo("value1");
                }
                span2.end();

                // Outside span2 scope: thread retains span1's baggage
                then(getTracer().getBaggage("foo").get()).isEqualTo("value1");
                Baggage span1BaggageAfter = getTracer().getBaggage(span1.context(), "foo");
                assertThat(span1BaggageAfter).isNotNull();
                then(span1BaggageAfter.get()).isEqualTo("value1");
            }
            then(getTracer().getBaggage("foo").get()).isNull();
        }
        span1.end();
        then(getTracer().getBaggage("foo").get()).isNull();
    }

    @Test
    void childSpanContextShouldNotMutateToParentOnScopeClose() {
        Span parent = getTracer().nextSpan().name("parent").start();
        try (Tracer.SpanInScope parentScope = getTracer().withSpan(parent)) {
            Span child = getTracer().nextSpan().name("child").start();
            TraceContext childContext = child.context();
            String childSpanId = childContext.spanId();
            String childTraceId = childContext.traceId();

            try (Tracer.SpanInScope childScope = getTracer().withSpan(child)) {
                then(getTracer().currentSpan()).isEqualTo(child);
                then(getTracer().currentTraceContext().context()).isEqualTo(childContext);
            }

            // Scope of child is closed
            then(getTracer().currentSpan()).isEqualTo(parent);
            then(getTracer().currentTraceContext().context()).isEqualTo(parent.context());

            // Child context itself must still identify child, not parent
            then(child.context().spanId()).as("Child span ID must remain child span ID").isEqualTo(childSpanId);
            then(child.context().traceId()).isEqualTo(childTraceId);
            then(child.context()).isNotEqualTo(parent.context());

            child.end();
        }
        parent.end();
        then(getTracer().currentSpan()).isNull();
    }

    @Test
    void reopeningChildSpanScopeShouldNotReviveParentWhenParentScopeIsClosed() {
        Span parent = getTracer().nextSpan().name("parent").start();
        Span child;
        try (Tracer.SpanInScope parentScope = getTracer().withSpan(parent)) {
            child = getTracer().nextSpan().name("child").start();
            try (Tracer.SpanInScope childScope = getTracer().withSpan(child)) {
                then(getTracer().currentSpan()).isEqualTo(child);
            }
            then(getTracer().currentSpan()).isEqualTo(parent);
        }
        // Parent scope is closed! Thread has no current span
        then(getTracer().currentSpan()).isNull();

        // Reopen scope on child
        try (Tracer.SpanInScope childScope2 = getTracer().withSpan(child)) {
            then(getTracer().currentSpan()).isEqualTo(child);
        }
        // When child scope closes, thread must return to null, not parent
        then(getTracer().currentSpan()).isNull();

        child.end();
        parent.end();
    }

    @Test
    void should_support_explicit_parent_when_parent_is_not_in_scope() {
        Span parent = getTracer().nextSpan().name("parent").start();
        Span child = Objects.requireNonNull(getTracer().nextSpan(parent)).name("child").start();

        then(child.context().traceId()).isEqualTo(parent.context().traceId());
        then(child.context().parentId()).isEqualTo(parent.context().spanId());
        then(getTracer().currentSpan()).isNull();

        try (Tracer.SpanInScope childScope = getTracer().withSpan(child)) {
            then(getTracer().currentSpan()).isEqualTo(child);
            then(getTracer().currentTraceContext().context()).isEqualTo(child.context());
        }

        then(getTracer().currentSpan()).isNull();
        child.end();
        parent.end();
    }

    @Test
    void should_support_explicit_parent_context_via_builder_when_parent_is_not_in_scope() {
        Span parent = getTracer().nextSpan().name("parent").start();
        Span child = getTracer().spanBuilder().setParent(parent.context()).name("child").start();

        then(child.context().traceId()).isEqualTo(parent.context().traceId());
        then(child.context().parentId()).isEqualTo(parent.context().spanId());
        then(getTracer().currentSpan()).isNull();

        try (Tracer.SpanInScope childScope = getTracer().withSpan(child)) {
            then(getTracer().currentSpan()).isEqualTo(child);
            then(getTracer().currentTraceContext().context()).isEqualTo(child.context());
        }

        then(getTracer().currentSpan()).isNull();
        child.end();
        parent.end();
    }

    @Test
    void should_handle_redundant_scopes_for_same_span() {
        Span span = getTracer().nextSpan().name("span").start();
        try (Tracer.SpanInScope scope1 = getTracer().withSpan(span)) {
            then(getTracer().currentSpan()).isEqualTo(span);
            try (Tracer.SpanInScope scope2 = getTracer().withSpan(span)) {
                then(getTracer().currentSpan()).isEqualTo(span);
            }
            then(getTracer().currentSpan()).isEqualTo(span);
        }
        then(getTracer().currentSpan()).isNull();
        span.end();
    }

    @Test
    void should_handle_reopening_scope_for_same_span_sequentially() {
        Span span = getTracer().nextSpan().name("span").start();
        try (Tracer.SpanInScope scope1 = getTracer().withSpan(span)) {
            then(getTracer().currentSpan()).isEqualTo(span);
        }
        then(getTracer().currentSpan()).isNull();

        try (Tracer.SpanInScope scope2 = getTracer().withSpan(span)) {
            then(getTracer().currentSpan()).isEqualTo(span);
        }
        then(getTracer().currentSpan()).isNull();
        span.end();
    }

    @Test
    void should_reopen_scope_for_span_after_baggage_scope_was_closed_in_previous_scope() {
        Span span = getTracer().nextSpan().name("span").start();
        try (Tracer.SpanInScope scope1 = getTracer().withSpan(span)) {
            try (BaggageInScope baggage = getTracer().createBaggageInScope("foo", "bar")) {
                then(getTracer().currentSpan()).isEqualTo(span);
            }
        }
        then(getTracer().currentSpan()).isNull();

        try (Tracer.SpanInScope scope2 = getTracer().withSpan(span)) {
            then(getTracer().currentSpan()).isEqualTo(span);
        }
        then(getTracer().currentSpan()).isNull();
        span.end();
    }

    @Test
    void should_keep_baggage_of_span_after_baggage_scope_was_closed_in_previous_scope() {
        Span parent = getTracer().nextSpan().name("parent").start();
        Span child;
        try (Tracer.SpanInScope parentScope = getTracer().withSpan(parent);
                BaggageInScope parentBaggage = getTracer().createBaggageInScope(parent.context(), "foo", "parent")) {
            child = getTracer().nextSpan().name("child").start();
        }
        try (Tracer.SpanInScope scope1 = getTracer().withSpan(child)) {
            then(getTracer().getBaggage("foo").get()).isEqualTo("parent");
            try (BaggageInScope baggage = getTracer().createBaggageInScope("foo", "child")) {
                then(getTracer().getBaggage("foo").get()).isEqualTo("child");
            }
            then(getTracer().getBaggage("foo").get()).isEqualTo("parent");
        }
        then(getTracer().getBaggage("foo").get()).isNull();

        try (Tracer.SpanInScope scope2 = getTracer().withSpan(child)) {
            then(getTracer().currentSpan()).isEqualTo(child);
            then(getTracer().getBaggage("foo").get()).isEqualTo("parent");
        }
        child.end();
        parent.end();
    }

    @Test
    void should_open_scope_for_span_inside_baggage_scope_for_that_span() {
        Span span = getTracer().nextSpan().name("span").start();
        try (BaggageInScope baggage = getTracer().createBaggageInScope(span.context(), "foo", "bar")) {
            try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                then(getTracer().currentSpan()).isEqualTo(span);
                then(getTracer().getBaggage("foo").get()).isEqualTo("bar");
            }
        }
        span.end();
    }

    @Test
    void should_open_scope_for_span_inside_baggage_scope_for_that_span_while_other_span_is_in_scope() {
        Span other = getTracer().nextSpan().name("other").start();
        Span span = getTracer().nextSpan().name("span").start();
        try (Tracer.SpanInScope otherScope = getTracer().withSpan(other)) {
            try (BaggageInScope baggage = getTracer().createBaggageInScope(span.context(), "foo", "bar")) {
                try (Tracer.SpanInScope scope = getTracer().withSpan(span)) {
                    then(getTracer().currentSpan()).isEqualTo(span);
                }
                then(getTracer().currentSpan()).isEqualTo(other);
            }
        }
        span.end();
        other.end();
    }

    @Test
    void should_reopen_observation_scope_after_baggage_scope_was_closed_in_previous_scope() {
        Observation observation = Observation.start("observation", this.observationRegistry);
        Span span;
        try (Observation.Scope scope1 = observation.openScope()) {
            span = getTracer().currentSpan();
            assertThat(span).isNotNull();
            try (BaggageInScope baggage = getTracer().createBaggageInScope("foo", "bar")) {
                then(getTracer().currentSpan()).isEqualTo(span);
            }
        }
        thenCurrentSpanEqualTo(null);

        // context propagation re-opens the observation scope e.g. after a thread hop
        try (Observation.Scope scope2 = observation.openScope()) {
            thenCurrentSpanEqualTo(span);
        }
        thenCurrentSpanEqualTo(null);
        observation.stop();
    }

    @Test
    void nestedScopesShouldBeMaintained() {
        Observation obs1 = Observation.start("parent", this.observationRegistry);
        Observation obs2 = Observation.start("foo", this.observationRegistry);

        thenCurrentObservationIsNull();

        try (Observation.Scope obs1Scope1 = obs1.openScope()) {
            Span spanObs1Scope1 = getTracer().currentSpan();
            CurrentTraceContext.Scope inScopeObs1Scope1 = getCurrentScope();
            try (Observation.Scope obs1Scope2 = obs1.openScope()) {
                Span spanObs1Scope2 = getTracer().currentSpan();
                CurrentTraceContext.Scope inScopeObs1Scope2 = getCurrentScope();
                try (Observation.Scope obs2Scope1 = obs2.openScope()) {
                    Span spanObs2Scope1 = getTracer().currentSpan();
                    CurrentTraceContext.Scope inScopeObs2Scope1 = getCurrentScope();
                    try (Observation.Scope obs2Scope2 = obs2.openScope()) {
                        Span spanObs2Scope2 = getTracer().currentSpan();
                        CurrentTraceContext.Scope inScopeObs2Scope2 = getCurrentScope();
                        try (Observation.Scope obs1Scope3 = obs1.openScope()) {
                            Span spanObs1Scope3 = getTracer().currentSpan();
                            CurrentTraceContext.Scope inScopeObs1Scope3 = getCurrentScope();
                            try (Observation.Scope obs1Scope4 = obs1.openScope()) {
                                // empty
                            }
                            thenCurrentSpanEqualTo(spanObs1Scope3);
                            thenCurrentScopeSameAs(inScopeObs1Scope3);
                        }
                        thenCurrentSpanEqualTo(spanObs2Scope2);
                        thenCurrentScopeSameAs(inScopeObs2Scope2);
                    }
                    thenCurrentSpanEqualTo(spanObs2Scope1);
                    thenCurrentScopeSameAs(inScopeObs2Scope1);
                }
                thenCurrentSpanEqualTo(spanObs1Scope2);
                thenCurrentScopeSameAs(inScopeObs1Scope2);
            }
            thenCurrentSpanEqualTo(spanObs1Scope1);
            thenCurrentScopeSameAs(inScopeObs1Scope1);
        }

        thenCurrentSpanEqualTo(null);
        thenCurrentObservationIsNull();

        obs2.stop();
        obs1.stop();
    }

    @Test
    @SuppressWarnings("deprecation")
    void nestedScopesShouldBeMaintainedWithContextPropagationApi() {
        Observation obs1 = Observation.start("obs1", this.observationRegistry);
        ContextSnapshot snapshot1;
        try (Observation.Scope scope = obs1.openScope()) {
            snapshot1 = ContextSnapshot.captureAll();
        }

        Observation obs2 = Observation.start("obs2", this.observationRegistry);
        ContextSnapshot snapshot2;
        try (Observation.Scope scope = obs2.openScope()) {
            snapshot2 = ContextSnapshot.captureAll();
        }

        thenCurrentObservationIsNull();

        try (ContextSnapshot.Scope obs1Scope1 = snapshot1.setThreadLocals()) {
            Span spanObs1Scope1 = getTracer().currentSpan();
            try (ContextSnapshot.Scope obs1Scope2 = snapshot1.setThreadLocals()) {
                Span spanObs1Scope2 = getTracer().currentSpan();
                try (ContextSnapshot.Scope obs2Scope1 = snapshot2.setThreadLocals()) {
                    Span spanObs2Scope1 = getTracer().currentSpan();
                    try (ContextSnapshot.Scope obs2Scope2 = snapshot2.setThreadLocals()) {
                        Span spanObs2Scope2 = getTracer().currentSpan();
                        try (ContextSnapshot.Scope obs1Scope3 = snapshot1.setThreadLocals()) {
                            Span spanObs1Scope3 = getTracer().currentSpan();
                            try (ContextSnapshot.Scope obs1Scope4 = snapshot1.setThreadLocals()) {
                                // empty
                            }
                            thenCurrentSpanEqualTo(spanObs1Scope3);
                        }
                        thenCurrentSpanEqualTo(spanObs2Scope2);
                    }
                    thenCurrentSpanEqualTo(spanObs2Scope1);
                }
                thenCurrentSpanEqualTo(spanObs1Scope2);
            }
            thenCurrentSpanEqualTo(spanObs1Scope1);
        }

        thenCurrentSpanEqualTo(null);
        thenCurrentObservationIsNull();

        obs2.stop();
        obs1.stop();
    }

    @Test
    void should_open_and_close_scopes_with_reactor() {
        Observation obs1 = Observation.start("1", this.observationRegistry);

        Observation.Scope scope = obs1.openScope();
        Span span1 = getTracer().currentSpan();

        Observation obs2 = Observation.start("2", this.observationRegistry);
        Observation.Scope scope2 = obs2.openScope();
        Span span2 = getTracer().currentSpan();

        AtomicReference<AssertionError> errorInFlatMap = new AtomicReference<>();
        AtomicReference<AssertionError> errorInOnNext = new AtomicReference<>();

        Mono.just(1).flatMap(integer -> Mono.just(2).doOnNext(integer1 -> {
            Span spanWEmpty = getTracer().currentSpan();
            assertInReactor(errorInFlatMap, spanWEmpty, null);
        }).contextWrite(context -> Context.empty())).doOnNext(integer -> {
            Span spanWOnNext = getTracer().currentSpan();
            assertInReactor(errorInOnNext, spanWOnNext, span2);
        }).contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, obs2)).block();

        then(errorInFlatMap).hasValue(null);
        then(errorInOnNext).hasValue(null);

        then(getTracer().currentSpan()).isEqualTo(span2);

        scope2.close();
        obs2.stop();
        then(getTracer().currentSpan())
            .as("Scopes should be restored to previous so current span should be Span 1 which is <%s>. Span 2 is <%s>",
                    span1, span2)
            .isEqualTo(span1);

        scope.close();
        obs1.stop();
        then(getTracer().currentSpan()).isNull();
    }

    @Test
    void should_open_and_close_scopes_with_reactor_multithreaded() throws InterruptedException {
        ExecutorService executorService = Executors.newScheduledThreadPool(2);
        Scheduler scheduler = Schedulers.fromExecutorService(executorService);

        Observation obs1 = Observation.start("1", this.observationRegistry);

        Observation.Scope scope = obs1.openScope();
        Span span1 = getTracer().currentSpan();

        Observation obs2 = Observation.start("2", this.observationRegistry);
        Observation.Scope scope2 = obs2.openScope();
        Span span2 = getTracer().currentSpan();

        List<AssertionError> errorsInFlatMap = new CopyOnWriteArrayList<>();
        List<AssertionError> errorsInInnerOnNext = new CopyOnWriteArrayList<>();
        List<AssertionError> errorsInOuterOnNext = new CopyOnWriteArrayList<>();

        CountDownLatch latch = new CountDownLatch(1);

        Flux.range(0, 2).flatMap(integer -> {
            Observation obs3 = Observation.start("3", this.observationRegistry);
            Span span3;
            try (Observation.Scope scope3 = obs3.openScope()) {
                span3 = getTracer().currentSpan();
            }

            return Mono.just(integer).subscribeOn(scheduler).doOnNext(v -> {
                Span spanWEmpty = getTracer().currentSpan();
                assertInReactor(errorsInFlatMap, spanWEmpty, null);
                if (integer == 1) {
                    latch.countDown();
                }
                else {
                    try {
                        if (!latch.await(1, TimeUnit.SECONDS)) {
                            throw new RuntimeException("Waiting for the latch timed out!");
                        }
                    }
                    catch (InterruptedException e) {
                        // ignore
                    }
                }
            }).contextWrite(context -> Context.empty()).doOnNext(v -> {
                Span spanWOnNext = getTracer().currentSpan();
                assertInReactor(errorsInInnerOnNext, spanWOnNext, span3);
            }).contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, obs3)).doOnTerminate(obs3::stop);
        }, 2, 1).doOnNext(integer -> {
            Span spanWOnNext = getTracer().currentSpan();
            assertInReactor(errorsInOuterOnNext, spanWOnNext, span2);
        }).blockLast(Duration.ofSeconds(1));

        then(errorsInFlatMap).isEmpty();
        then(errorsInOuterOnNext).isEmpty();
        then(errorsInInnerOnNext).isEmpty();

        then(getTracer().currentSpan()).isEqualTo(span2);

        scope2.close();
        obs2.stop();

        then(getTracer().currentSpan())
            .as("Scopes should be restored to previous so current span should be Span 1 which is <%s>. Span 2 is <%s>",
                    span1, span2)
            .isEqualTo(span1);

        scope.close();
        obs1.stop();
        then(getTracer().currentSpan()).isNull();

        CountDownLatch cleanupLatch = new CountDownLatch(1);

        Future<?> unused1 = executorService.submit(() -> {
            Observation obs4 = Observation.start("4", this.observationRegistry);
            try (Observation.Scope scope4 = obs4.openScope()) {
                if (!cleanupLatch.await(1, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Waiting for the latch timed out!");
                }
            }
            catch (InterruptedException e) {
                // ignore
            }
            obs4.stop();
        });

        Future<?> unused2 = executorService.submit(() -> {
            Observation obs4 = Observation.start("4", this.observationRegistry);
            try (Observation.Scope scope4 = obs4.openScope()) {
                cleanupLatch.countDown();
            }
            obs4.stop();
        });

        executorService.shutdown();
        then(executorService.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_open_and_close_scopes_with_reactor_with_baggage() {
        Observation obs1 = Observation.start("1", this.observationRegistry);

        Observation.Scope scope = obs1.openScope();
        Span span1 = getTracer().currentSpan();
        assertThat(span1).isNotNull();
        BaggageInScope baggageInScope1 = getTracer().createBaggageInScope("foo", "span1");
        then(getTracer().getAllBaggage().get("foo")).isEqualTo("span1");
        then(getTracer().getAllBaggage(span1.context()).get("foo")).isEqualTo("span1");

        Observation obs2 = Observation.start("2", this.observationRegistry);
        Observation.Scope scope2 = obs2.openScope();
        Span span2 = getTracer().currentSpan();
        assertThat(span2).isNotNull();
        BaggageInScope baggageInScope2 = getTracer().createBaggageInScope(span2.context(), "foo", "span2");

        then(getTracer().getAllBaggage().get("foo")).isEqualTo("span2");
        then(getTracer().getAllBaggage(span1.context()).get("foo")).isEqualTo("span1");
        then(getTracer().getAllBaggage(span2.context()).get("foo")).isEqualTo("span2");

        AtomicReference<AssertionError> errorInFlatMap = new AtomicReference<>();
        AtomicReference<AssertionError> errorInOnNext = new AtomicReference<>();

        Mono.just(1).flatMap(integer -> Mono.just(2).doOnNext(integer1 -> {
            Map<String, String> baggageInEmpty = getTracer().getAllBaggage();
            assertBaggageInReactor(errorInFlatMap, baggageInEmpty, null);
        }).contextWrite(context -> Context.empty())).doOnNext(integer -> {
            Map<String, String> baggageInOnNext = getTracer().getAllBaggage();
            assertBaggageInReactor(errorInOnNext, baggageInOnNext, "span2");
        }).contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, obs2)).block();

        then(errorInFlatMap).hasValue(null);
        then(errorInOnNext).hasValue(null);

        Baggage span2Baggage = getTracer().getBaggage(span2.context(), "foo");
        assertThat(span2Baggage).isNotNull();
        then(span2Baggage.get()).isEqualTo("span2");

        Baggage span1Baggage = getTracer().getBaggage(span1.context(), "foo");
        assertThat(span1Baggage).isNotNull();
        then(span1Baggage.get()).isEqualTo("span1");

        baggageInScope2.close();
        scope2.close();
        obs2.stop();
        TracingContext tracingContext2 = obs2.getContext().get(TracingContext.class);
        assertThat(tracingContext2).isNotNull();
        then(tracingContext2.getBaggage()).isNullOrEmpty();

        then(getTracer().getBaggage("foo").get()).isEqualTo("span1");
        Baggage span1BaggageAfterClose2 = getTracer().getBaggage(span1.context(), "foo");
        assertThat(span1BaggageAfterClose2).isNotNull();
        then(span1BaggageAfterClose2.get()).isEqualTo("span1");

        Baggage span2BaggageAfterClose2 = getTracer().getBaggage(span2.context(), "foo");
        assertThat(span2BaggageAfterClose2).isNotNull();
        then(span2BaggageAfterClose2.get()).isEqualTo("span1");

        baggageInScope1.close();
        scope.close();
        obs1.stop();
        TracingContext tracingContext1 = obs1.getContext().get(TracingContext.class);
        assertThat(tracingContext1).isNotNull();
        then(tracingContext1.getBaggage()).isNullOrEmpty();

        then(getTracer().getAllBaggage()).isEmpty();
    }

    private void thenCurrentScopeSameAs(CurrentTraceContext.@Nullable Scope scope) {
        then(getCurrentScope()).isSameAs(scope);
    }

    private void thenCurrentSpanEqualTo(@Nullable Span span) {
        then(getTracer().currentSpan()).isEqualTo(span);
    }

    private CurrentTraceContext.@Nullable Scope getCurrentScope() {
        Observation observation = this.observationRegistry.getCurrentObservation();
        if (observation == null) {
            return null;
        }
        TracingObservationHandler.TracingContext tracingContext = observation.getContextView()
            .get(TracingObservationHandler.TracingContext.class);
        return tracingContext != null ? tracingContext.getScope() : null;
    }

    private void thenCurrentObservationIsNull() {
        then(this.observationRegistry.getCurrentObservation()).isNull();
    }

    private static void assertInReactor(List<AssertionError> errors, @Nullable Span spanWOnNext,
            @Nullable Span expectedSpan) {
        try {
            then(spanWOnNext).isEqualTo(expectedSpan);
        }
        catch (AssertionError er) {
            errors.add(er);
        }
    }

    private static void assertInReactor(AtomicReference<AssertionError> errors, @Nullable Span spanWOnNext,
            @Nullable Span expectedSpan) {
        try {
            then(spanWOnNext).isEqualTo(expectedSpan);
        }
        catch (AssertionError er) {
            errors.set(er);
        }
    }

    private static void assertBaggageInReactor(AtomicReference<AssertionError> errors, Map<String, String> baggageMap,
            @Nullable String expectedValue) {
        try {
            then(baggageMap.get("foo")).isEqualTo(expectedValue);
        }
        catch (AssertionError er) {
            errors.set(er);
        }
    }

}
