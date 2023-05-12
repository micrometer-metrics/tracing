/**
 * Copyright 2022 the original author or authors.
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
package io.micrometer.tracing.brave.contextpropagation;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.CorrelationFlushScopeArrayReader;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.BDDAssertions.then;

class ScopesTests {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ScopesTests.class);

    CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.newBuilder()
            .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageField.create("foo"))
                .flushOnUpdate()
                .build())
            .build())
        .build();

    Tracing tracing = Tracing.newBuilder().currentTraceContext(currentTraceContext).build();

    brave.Tracer braveTracer = tracing.tracer();

    // [Micrometer Tracing component] A Micrometer Tracing wrapper for Brave's Tracer
    Tracer tracer = new BraveTracer(this.braveTracer, new BraveCurrentTraceContext(this.currentTraceContext),
            new BraveBaggageManager());

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(this.tracer));

        Hooks.enableAutomaticContextPropagation();
    }

    @Disabled("TODO: Bug")
    @Test
    void should_open_and_close_scopes_with_reactor() {
        Observation obs1 = Observation.start("1", observationRegistry);

        logger.info("SIZE BEFORE [" + CorrelationFlushScopeArrayReader.size() + "]");
        Observation.Scope scope = obs1.openScope();
        Span span1 = tracer.currentSpan();
        logger.info("SPAN 1 [" + tracer.currentSpan() + "]");

        Observation obs2 = Observation.start("2", observationRegistry);
        Observation.Scope scope2 = obs2.openScope();
        Span span2 = tracer.currentSpan();
        logger.info("SPAN 2 [" + tracer.currentSpan() + "]");

        AtomicReference<AssertionError> errorInFlatMap = new AtomicReference<>();
        AtomicReference<AssertionError> errorInOnNext = new AtomicReference<>();

        Mono.just(1).flatMap(integer -> {
            return Mono.just(2).doOnNext(integer1 -> {
                Span spanWEmpty = tracer.currentSpan();
                logger.info("\n\n[2] SPAN IN EMPTY [" + spanWEmpty + "]");
                assertInReactor(errorInFlatMap, spanWEmpty, null);
            }).contextWrite(context -> Context.empty());
        }).doOnNext(integer -> {
            Span spanWOnNext = tracer.currentSpan();
            logger.info("\n\n[1] SPAN IN ON NEXT [" + spanWOnNext + "]");
            assertInReactor(errorInOnNext, spanWOnNext, span2);
        }).contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, obs2)).block();

        logger.info("Checking if there were no errors in reactor");
        then(errorInFlatMap).hasValue(null);
        then(errorInOnNext).hasValue(null);

        logger.info("\n\nSPAN OUTSIDE REACTOR [" + tracer.currentSpan() + "]");
        logger.info("SIZE AFTER [" + CorrelationFlushScopeArrayReader.size() + "]");
        then(tracer.currentSpan()).isEqualTo(span2);

        scope2.close();
        obs2.stop();
        logger.info("SIZE OUTSIDE CLOSE 2 [" + CorrelationFlushScopeArrayReader.size() + "]");
        logger.info("SPAN AFTER CLOSE 2 [" + tracer.currentSpan() + "]");
        then(tracer.currentSpan())
            .as("Scopes should be restored to previous so current span should be Span 1 which is <%s>. Span 2 is <%s>",
                    span1, span2)
            .isEqualTo(span1);

        scope.close();
        obs1.stop();
        logger.info("SIZE AFTER CLOSE 1 [" + CorrelationFlushScopeArrayReader.size() + "]");
        then(CorrelationFlushScopeArrayReader.size()).isZero();
        then(tracer.currentSpan()).isNull();
        logger.info("SPAN AFTER CLOSE 1 [" + tracer.currentSpan() + "]");
    }

    private static void assertInReactor(AtomicReference<AssertionError> errors, Span spanWOnNext, Span expectedSpan) {
        try {
            then(spanWOnNext).isEqualTo(expectedSpan);
        }
        catch (AssertionError er) {
            errors.set(er);
        }
    }

}
