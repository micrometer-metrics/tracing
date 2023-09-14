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
package io.micrometer.docs.tracing;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.test.SampleTestRunner;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.micrometer.tracing.test.simple.SimpleTracer;
import io.micrometer.tracing.test.simple.SpansAssert;
import io.micrometer.tracing.test.simple.TracerAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.function.BiConsumer;

/**
 * Sources for tracing-testing.adoc
 */
class TracingTestingTests {

    @Nested
    // @formatter:off
    // tag::handler_test[]
    class SomeComponentThatIsUsingMyTracingObservationHandlerTests {

        ObservationRegistry registry = ObservationRegistry.create();

        SomeComponent someComponent = new SomeComponent(registry);

        SimpleTracer simpleTracer = new SimpleTracer();

        MyTracingObservationHandler handler = new MyTracingObservationHandler(simpleTracer);

        @BeforeEach
        void setup() {
            registry.observationConfig().observationHandler(handler);
        }

        @Test
        void should_store_a_span() {
            // this code will call actual Observation API
            someComponent.doSthThatShouldCreateSpans();

            TracerAssert.assertThat(simpleTracer)
                    .onlySpan()
                    .hasNameEqualTo("insert user")
                    .hasKindEqualTo(Span.Kind.CLIENT)
                    .hasRemoteServiceNameEqualTo("mongodb-database")
                    .hasTag("mongodb.command", "insert")
                    .hasTag("mongodb.collection", "user")
                    .hasTagWithKey("mongodb.cluster_id")
                    .assertThatThrowable()
                    .isInstanceOf(IllegalStateException.class)
                    .backToSpan()
                    .hasIpThatIsBlank()
                    .hasPortThatIsNotSet();
        }

    }
    // end::handler_test[]
    // @formatter:on

    // @formatter:off
    // tag::observability_smoke_test[]
    class ObservabilitySmokeTest extends SampleTestRunner {

        ObservabilitySmokeTest() {
            super(SampleRunnerConfig.builder().wavefrontApplicationName("my-app").wavefrontServiceName("my-service")
                    .wavefrontToken("...")
                    .wavefrontUrl("...")
                    .zipkinUrl("...") // defaults to localhost:9411
                    .build());
        }

        @Override
        public BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizeObservationHandlers() {
            return (bb, handlers) -> {
                ObservationHandler defaultHandler = handlers.removeLast();
                handlers.addLast(new MyTracingObservationHandler(bb.getTracer()));
                handlers.addLast(defaultHandler);
            };
        }

        @Override
        public SampleTestRunnerConsumer yourCode() {
            return (bb, meterRegistry) -> {
                // here you would be running your code
                yourCode();

                SpansAssert.assertThat(bb.getFinishedSpans())
                        .haveSameTraceId()
                        .hasNumberOfSpansEqualTo(8)
                        .hasNumberOfSpansWithNameEqualTo("handle", 4)
                        .forAllSpansWithNameEqualTo("handle", span -> span.hasTagWithKey("rsocket.request-type"))
                        .hasASpanWithNameIgnoreCase("request_stream")
                        .thenASpanWithNameEqualToIgnoreCase("request_stream")
                        .hasTag("rsocket.request-type", "REQUEST_STREAM")
                        .backToSpans()
                        .hasASpanWithNameIgnoreCase("request_channel")
                        .thenASpanWithNameEqualToIgnoreCase("request_channel")
                        .hasTag("rsocket.request-type", "REQUEST_CHANNEL")
                        .backToSpans()
                        .hasASpanWithNameIgnoreCase("request_fnf")
                        .thenASpanWithNameEqualToIgnoreCase("request_fnf")
                        .hasTag("rsocket.request-type", "REQUEST_FNF")
                        .backToSpans()
                        .hasASpanWithNameIgnoreCase("request_response")
                        .thenASpanWithNameEqualToIgnoreCase("request_response")
                        .hasTag("rsocket.request-type", "REQUEST_RESPONSE");

                MeterRegistryAssert.assertThat(meterRegistry)
                        .hasTimerWithNameAndTags("rsocket.response", Tags.of(Tag.of("error", "none"), Tag.of("rsocket.request-type", "REQUEST_RESPONSE")))
                        .hasTimerWithNameAndTags("rsocket.fnf", Tags.of(Tag.of("error", "none"), Tag.of("rsocket.request-type", "REQUEST_FNF")))
                        .hasTimerWithNameAndTags("rsocket.request", Tags.of(Tag.of("error", "none"), Tag.of("rsocket.request-type", "REQUEST_RESPONSE")))
                        .hasTimerWithNameAndTags("rsocket.channel", Tags.of(Tag.of("error", "none"), Tag.of("rsocket.request-type", "REQUEST_CHANNEL")))
                        .hasTimerWithNameAndTags("rsocket.stream", Tags.of(Tag.of("error", "none"), Tag.of("rsocket.request-type", "REQUEST_STREAM")));
            };
        }

    }
    // end::observability_smoke_test[]
    // @formatter:on

    class SomeComponent {

        private final ObservationRegistry registry;

        public SomeComponent(ObservationRegistry registry) {
            this.registry = registry;
        }

        void doSthThatShouldCreateSpans() {
            try {
                Observation.createNotStarted("insert user", () -> new CustomContext("mongodb-database"), this.registry)
                    .highCardinalityKeyValue("mongodb.command", "insert")
                    .highCardinalityKeyValue("mongodb.collection", "user")
                    .highCardinalityKeyValue("mongodb.cluster_id", "some_id")
                    .observe(() -> {
                        System.out.println("hello");
                        throw new IllegalStateException("Boom!");
                    });
            }
            catch (Exception ex) {

            }
        }

    }

    // tag::observation_handler[]
    static class MyTracingObservationHandler implements TracingObservationHandler<CustomContext> {

        private final Tracer tracer;

        MyTracingObservationHandler(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void onStart(CustomContext context) {
            String databaseName = context.getDatabaseName();
            Span.Builder builder = this.tracer.spanBuilder().kind(Span.Kind.CLIENT).remoteServiceName(databaseName);
            getTracingContext(context).setSpan(builder.start());
        }

        @Override
        public void onError(CustomContext context) {
            getTracingContext(context).getSpan().error(context.getError());
        }

        @Override
        public void onStop(CustomContext context) {
            Span span = getRequiredSpan(context);
            span.name(context.getContextualName() != null ? context.getContextualName() : context.getName());
            tagSpan(context, span);
            span.end();
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof CustomContext;
        }

        @Override
        public Tracer getTracer() {
            return this.tracer;
        }

    }
    // end::observation_handler[]

    static class CustomContext extends Observation.Context {

        private final String databaseName;

        CustomContext(String databaseName) {
            this.databaseName = databaseName;
        }

        String getDatabaseName() {
            return databaseName;
        }

    }

}
