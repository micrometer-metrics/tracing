/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.tracing.test.reporter.inmemory;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.brave.bridge.BraveHttpClientHandler;
import io.micrometer.tracing.brave.bridge.BraveHttpServerHandler;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.HttpClientTracingObservationHandler;
import io.micrometer.tracing.handler.HttpServerTracingObservationHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;

/**
 * Provides Zipkin setup with Brave.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public final class InMemoryBraveSetup implements AutoCloseable {

    private final Consumer<Builder.BraveBuildingBlocks> closingFunction;

    private final Builder.BraveBuildingBlocks braveBuildingBlocks;

    InMemoryBraveSetup(Consumer<Builder.BraveBuildingBlocks> closingFunction, Builder.BraveBuildingBlocks braveBuildingBlocks) {
        this.closingFunction = closingFunction;
        this.braveBuildingBlocks = braveBuildingBlocks;
    }

    @Override
    public void close() {
        this.closingFunction.accept(this.braveBuildingBlocks);
    }

    /**
     * @return all the Brave building blocks required to communicate with Zipkin
     */
    public Builder.BraveBuildingBlocks getBuildingBlocks() {
        return this.braveBuildingBlocks;
    }

    /**
     * @return builder for the {@link InMemoryBraveSetup}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Brave with Zipkin.
     */
    public static class Builder {

        private String applicationName = "observability-test";

        private Supplier<Sender> sender;

        private Function<Sender, AsyncReporter<Span>> reporter;

        private Function<TestSpanHandler, Tracing> tracing;

        private Function<Tracing, Tracer> tracer;

        private Function<Tracing, HttpTracing> httpTracing;

        private Function<HttpTracing, HttpServerHandler> httpServerHandler;

        private Function<HttpTracing, HttpClientHandler> httpClientHandler;

        private Function<BraveBuildingBlocks, ObservationHandler> handlers;

        private BiConsumer<BuildingBlocks, Deque<ObservationHandler<Observation.Context>>> customizers;

        private Consumer<BraveBuildingBlocks> closingFunction;

        /**
         * All Brave building blocks.
         */
        public static class BraveBuildingBlocks implements BuildingBlocks {

            public final Tracing tracing;

            public final Tracer tracer;

            public final BravePropagator propagator;

            public final HttpTracing httpTracing;

            public final HttpServerHandler httpServerHandler;

            public final HttpClientHandler httpClientHandler;

            public final BiConsumer<BuildingBlocks, Deque<ObservationHandler<Observation.Context>>> customizers;

            private final TestSpanHandler testSpanHandler;

            public BraveBuildingBlocks(Tracing tracing, Tracer tracer, BravePropagator propagator, HttpTracing httpTracing, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler, BiConsumer<BuildingBlocks, Deque<ObservationHandler<Observation.Context>>> customizers, TestSpanHandler testSpanHandler) {
                this.tracing = tracing;
                this.tracer = tracer;
                this.propagator = propagator;
                this.httpTracing = httpTracing;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
                this.customizers = customizers;
                this.testSpanHandler = testSpanHandler;
            }

            @Override
            public Tracer getTracer() {
                return this.tracer;
            }

            @Override
            public Propagator getPropagator() {
                return this.propagator;
            }

            @Override
            public HttpServerHandler getHttpServerHandler() {
                return this.httpServerHandler;
            }

            @Override
            public HttpClientHandler getHttpClientHandler() {
                return this.httpClientHandler;
            }

            @Override
            public BiConsumer<BuildingBlocks, Deque<ObservationHandler<Observation.Context>>> getCustomizers() {
                return this.customizers;
            }

            @Override
            public List<FinishedSpan> getFinishedSpans() {
                return this.testSpanHandler.spans().stream().map(BraveFinishedSpan::fromBrave).collect(Collectors.toList());
            }
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder sender(Supplier<Sender> sender) {
            this.sender = sender;
            return this;
        }

        public Builder reporter(Function<Sender, AsyncReporter<Span>> reporter) {
            this.reporter = reporter;
            return this;
        }

        public Builder tracing(Function<TestSpanHandler, Tracing> tracing) {
            this.tracing = tracing;
            return this;
        }

        public Builder tracer(Function<Tracing, Tracer> tracer) {
            this.tracer = tracer;
            return this;
        }

        public Builder httpTracing(Function<Tracing, HttpTracing> httpTracing) {
            this.httpTracing = httpTracing;
            return this;
        }

        public Builder observationHandlerCustomizer(BiConsumer<BuildingBlocks, Deque<ObservationHandler<Observation.Context>>> customizers) {
            this.customizers = customizers;
            return this;
        }

        public Builder httpServerHandler(Function<HttpTracing, HttpServerHandler> httpServerHandler) {
            this.httpServerHandler = httpServerHandler;
            return this;
        }

        public Builder httpClientHandler(Function<HttpTracing, HttpClientHandler> httpClientHandler) {
            this.httpClientHandler = httpClientHandler;
            return this;
        }

        public Builder handlers(Function<BraveBuildingBlocks, ObservationHandler> tracingHandlers) {
            this.handlers = tracingHandlers;
            return this;
        }

        public Builder closingFunction(Consumer<BraveBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * Registers setup.
         *
         * @param registry registry to which the {@link ObservationHandler} should be attached
         * @return setup with all Brave building blocks
         */
        public InMemoryBraveSetup register(ObservationRegistry registry) {
            TestSpanHandler testSpanHandler = new TestSpanHandler();
            Tracing tracing = this.tracing != null ? this.tracing.apply(testSpanHandler) : tracing(testSpanHandler, this.applicationName);
            Tracer tracer = this.tracer != null ? this.tracer.apply(tracing) : tracer(tracing);
            HttpTracing httpTracing = this.httpTracing != null ? this.httpTracing.apply(tracing) : httpTracing(tracing);
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(httpTracing) : httpServerHandler(httpTracing);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(httpTracing) : httpClientHandler(httpTracing);
            BiConsumer<BuildingBlocks, Deque<ObservationHandler<Observation.Context>>> customizers = this.customizers != null ? this.customizers : (t, h) -> {
            };
            BraveBuildingBlocks braveBuildingBlocks = new BraveBuildingBlocks(tracing, tracer, new BravePropagator(tracing), httpTracing, httpServerHandler, httpClientHandler, customizers, testSpanHandler);
            ObservationHandler tracingHandlers = this.handlers != null ? this.handlers.apply(braveBuildingBlocks) : tracingHandlers(braveBuildingBlocks);
            registry.observationConfig().observationHandler(tracingHandlers);
            Consumer<BraveBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction : closingFunction();
            return new InMemoryBraveSetup(closingFunction, braveBuildingBlocks);
        }

        private static Tracer tracer(Tracing tracing) {
            return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());
        }

        private static Tracing tracing(TestSpanHandler testSpanHandler, String applicationName) {
            return Tracing.newBuilder()
                    .localServiceName(applicationName)
                    .addSpanHandler(testSpanHandler)
                    .sampler(Sampler.ALWAYS_SAMPLE)
                    .build();
        }

        private static HttpTracing httpTracing(Tracing tracing) {
            return HttpTracing.newBuilder(tracing).build();
        }

        private static HttpServerHandler httpServerHandler(HttpTracing httpTracing) {
            return new BraveHttpServerHandler(brave.http.HttpServerHandler.create(httpTracing));
        }

        private static HttpClientHandler httpClientHandler(HttpTracing httpTracing) {
            return new BraveHttpClientHandler(brave.http.HttpClientHandler.create(httpTracing));
        }

        private static Consumer<BraveBuildingBlocks> closingFunction() {
            return deps -> {
                deps.httpTracing.close();
                deps.tracing.close();
            };
        }

        @SuppressWarnings("rawtypes")
        private static ObservationHandler tracingHandlers(BraveBuildingBlocks braveBuildingBlocks) {
            Tracer tracer = braveBuildingBlocks.tracer;
            HttpServerHandler httpServerHandler = braveBuildingBlocks.httpServerHandler;
            HttpClientHandler httpClientHandler = braveBuildingBlocks.httpClientHandler;
            LinkedList<ObservationHandler<Observation.Context>> handlers = new LinkedList(Arrays.asList(new HttpServerTracingObservationHandler(tracer, httpServerHandler), new HttpClientTracingObservationHandler(tracer, httpClientHandler), new DefaultTracingObservationHandler(tracer)));
            braveBuildingBlocks.customizers.accept(braveBuildingBlocks, handlers);
            return new ObservationHandler.FirstMatchingCompositeObservationHandler(handlers);
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     *
     * @param registry registry to register the handlers against
     * @param consumer      lambda to be executed with the building blocks
     */
    public static void run(ObservationRegistry registry, Consumer<Builder.BraveBuildingBlocks> consumer) {
        run(InMemoryBraveSetup.builder().register(registry), consumer);
    }

    /**
     * @param localZipkinBrave Brave setup with Zipkin
     * @param consumer         runnable to run
     */
    public static void run(InMemoryBraveSetup localZipkinBrave, Consumer<Builder.BraveBuildingBlocks> consumer) {
        try {
            consumer.accept(localZipkinBrave.getBuildingBlocks());
        }
        finally {
            localZipkinBrave.close();
        }
    }
}
