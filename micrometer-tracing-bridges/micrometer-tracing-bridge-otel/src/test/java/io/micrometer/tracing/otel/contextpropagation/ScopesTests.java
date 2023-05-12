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
package io.micrometer.tracing.otel.contextpropagation;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.BDDAssertions.then;

class ScopesTests {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ScopesTests.class);

    ArrayListSpanProcessor testSpanProcessor = new ArrayListSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(testSpanProcessor))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {
    }, new OtelBaggageManager(new OtelCurrentTraceContext(), Collections.emptyList(), Collections.emptyList()));

    DefaultTracingObservationHandler handler = new DefaultTracingObservationHandler(tracer);

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(handler);

        Hooks.enableAutomaticContextPropagation();
    }

    @Disabled("TODO: Bug")
    @Test
    void should_open_and_close_scopes_with_reactor() {
        Observation obs1 = Observation.start("1", observationRegistry);

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
        then(tracer.currentSpan()).isEqualTo(span2);

        scope2.close();
        obs2.stop();
        logger.info("SPAN AFTER CLOSE 2 [" + tracer.currentSpan() + "]");
        then(tracer.currentSpan()).isEqualTo(span1);

        scope.close();
        obs1.stop();
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
