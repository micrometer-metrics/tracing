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
package io.micrometer.tracing.test.reporter.zipkin;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.*;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Provides Zipkin setup with Brave.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public final class ZipkinBraveSetup implements AutoCloseable {

    private final Consumer<Builder.BraveBuildingBlocks> closingFunction;

    private final Builder.BraveBuildingBlocks braveBuildingBlocks;

    ZipkinBraveSetup(Consumer<Builder.BraveBuildingBlocks> closingFunction,
            Builder.BraveBuildingBlocks braveBuildingBlocks) {
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
     * @return builder for the {@link ZipkinBraveSetup}
     */
    public static ZipkinBraveSetup.Builder builder() {
        return new ZipkinBraveSetup.Builder();
    }

    /**
     * Builder for Brave with Zipkin.
     */
    public static class Builder {

        private String applicationName = "observability-test";

        private String zipkinUrl = "http://localhost:9411";

        private Supplier<BytesMessageSender> sender;

        private Function<BytesMessageSender, AsyncZipkinSpanHandler> spanHandler;

        private Function<AsyncZipkinSpanHandler, Tracing> tracing;

        private Function<Tracing, Tracer> tracer;

        private Function<Tracing, HttpTracing> httpTracing;

        @Deprecated
        private Function<HttpTracing, HttpServerHandler> httpServerHandler;

        @Deprecated
        private Function<HttpTracing, HttpClientHandler> httpClientHandler;

        private Function<BraveBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers;

        private BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

        private Consumer<BraveBuildingBlocks> closingFunction;

        /**
         * All Brave building blocks required to communicate with Zipkin.
         */
        public static class BraveBuildingBlocks implements BuildingBlocks {

            private final BytesMessageSender sender;

            private final AsyncZipkinSpanHandler spanHandler;

            private final Tracing tracing;

            private final Tracer tracer;

            private final BravePropagator propagator;

            private final HttpTracing httpTracing;

            @Deprecated
            private final HttpServerHandler httpServerHandler;

            @Deprecated
            private final HttpClientHandler httpClientHandler;

            private final BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

            private final TestSpanHandler testSpanHandler;

            /**
             * Creates a new instance of {@link BraveBuildingBlocks}.
             * @param sender sender
             * @param spanHandler spanHandler
             * @param tracing tracing
             * @param tracer tracer
             * @param propagator propagator
             * @param httpTracing http tracing
             * @param httpServerHandler http server handler
             * @param httpClientHandler http client handler
             * @param customizers observation handler customizers
             * @param testSpanHandler test span handler
             */
            public BraveBuildingBlocks(BytesMessageSender sender, AsyncZipkinSpanHandler spanHandler, Tracing tracing,
                    Tracer tracer, BravePropagator propagator, HttpTracing httpTracing,
                    HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler,
                    BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers,
                    TestSpanHandler testSpanHandler) {
                this.sender = sender;
                this.spanHandler = spanHandler;
                this.tracing = tracing;
                this.tracer = tracer;
                this.propagator = propagator;
                this.httpTracing = httpTracing;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
                this.customizers = customizers;
                this.testSpanHandler = testSpanHandler;
            }

            /**
             * Returns the sender.
             * @return sender
             */
            public BytesMessageSender getSender() {
                return sender;
            }

            @Override
            public Tracer getTracer() {
                return this.tracer;
            }

            @Override
            public Propagator getPropagator() {
                return this.propagator;
            }

            /**
             * @deprecated scheduled for removal in 1.4.0, returns {@code null} starting
             * from 1.3.0 unless explicitly set by the builder
             * @return http server handler
             */
            @Deprecated
            @Override
            public HttpServerHandler getHttpServerHandler() {
                return this.httpServerHandler;
            }

            /**
             * @deprecated scheduled for removal in 1.4.0, returns {@code null} starting
             * from 1.3.0 unless explicitly set by the builder
             * @return http client handler
             */
            @Deprecated
            @Override
            public HttpClientHandler getHttpClientHandler() {
                return this.httpClientHandler;
            }

            @Override
            public BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> getCustomizers() {
                return this.customizers;
            }

            @Override
            public List<FinishedSpan> getFinishedSpans() {
                return this.testSpanHandler.spans()
                    .stream()
                    .map(BraveFinishedSpan::fromBrave)
                    .collect(Collectors.toList());
            }

        }

        /**
         * Overrides the application name.
         * @param applicationName name of the application
         * @return this for chaining
         */
        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        /**
         * Overrides the Zipkin URL.
         * @param zipkinUrl zipkin URL
         * @return this for chaining
         */
        public Builder zipkinUrl(String zipkinUrl) {
            this.zipkinUrl = zipkinUrl;
            return this;
        }

        /**
         * Overrides sender.
         * @param sender sender provider
         * @return this for chaining
         */
        public Builder sender(Supplier<BytesMessageSender> sender) {
            this.sender = sender;
            return this;
        }

        /**
         * Overrides spanHandler.
         * @param spanHandler spanHandler provider
         * @return this for chaining
         */
        public Builder spanHandler(Function<BytesMessageSender, AsyncZipkinSpanHandler> spanHandler) {
            this.spanHandler = spanHandler;
            return this;
        }

        /**
         * Overrides Tracing.
         * @param tracing tracing provider
         * @return this for chaining
         */
        public Builder tracing(Function<AsyncZipkinSpanHandler, Tracing> tracing) {
            this.tracing = tracing;
            return this;
        }

        /**
         * Overrides Tracer.
         * @param tracer tracer provider
         * @return this for chaining
         */
        public Builder tracer(Function<Tracing, Tracer> tracer) {
            this.tracer = tracer;
            return this;
        }

        /**
         * Overrides Http Tracing.
         * @param httpTracing http tracing provider
         * @return this for chaining
         */
        public Builder httpTracing(Function<Tracing, HttpTracing> httpTracing) {
            this.httpTracing = httpTracing;
            return this;
        }

        /**
         * Allows customization of Observation Handlers.
         * @param customizers customization provider
         * @return this for chaining
         */
        public Builder observationHandlerCustomizer(
                BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers) {
            this.customizers = customizers;
            return this;
        }

        /**
         * Overrides Http Server Handler.
         * @param httpServerHandler http server handler provider
         * @return this for chaining
         * @deprecated scheduled for removal in 1.4.0
         */
        @Deprecated
        public Builder httpServerHandler(Function<HttpTracing, HttpServerHandler> httpServerHandler) {
            this.httpServerHandler = httpServerHandler;
            return this;
        }

        /**
         * Overrides Http Client Handler.
         * @param httpClientHandler http client handler provider
         * @return this for chaining
         * @deprecated scheduled for removal in 1.4.0
         */
        @Deprecated
        public Builder httpClientHandler(Function<HttpTracing, HttpClientHandler> httpClientHandler) {
            this.httpClientHandler = httpClientHandler;
            return this;
        }

        /**
         * Overrides Observation Handlers
         * @param handlers handlers provider
         * @return this for chaining
         */
        public Builder handlers(
                Function<Builder.BraveBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers) {
            this.handlers = handlers;
            return this;
        }

        /**
         * Overrides the closing function.
         * @param closingFunction closing function provider
         * @return this for chaining
         */
        public Builder closingFunction(Consumer<Builder.BraveBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * Registers setup.
         * @param registry observation registry to which the {@link ObservationHandler}
         * should be attached
         * @return setup with all Brave building blocks
         */
        public ZipkinBraveSetup register(ObservationRegistry registry) {
            BytesMessageSender sender = this.sender != null ? this.sender.get() : sender(this.zipkinUrl);
            AsyncZipkinSpanHandler spanHandler = this.spanHandler != null ? this.spanHandler.apply(sender)
                    : spanHandler(sender);
            TestSpanHandler testSpanHandler = new TestSpanHandler();
            Tracing tracing = this.tracing != null ? this.tracing.apply(spanHandler)
                    : tracing(spanHandler, testSpanHandler, this.applicationName);
            Tracer tracer = this.tracer != null ? this.tracer.apply(tracing) : tracer(tracing);
            HttpTracing httpTracing = this.httpTracing != null ? this.httpTracing.apply(tracing) : httpTracing(tracing);
            @Deprecated
            HttpServerHandler httpServerHandler = this.httpServerHandler != null
                    ? this.httpServerHandler.apply(httpTracing) : null;
            @Deprecated
            HttpClientHandler httpClientHandler = this.httpClientHandler != null
                    ? this.httpClientHandler.apply(httpTracing) : null;
            BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers = this.customizers != null
                    ? this.customizers : (t, h) -> {
                    };
            BraveBuildingBlocks braveBuildingBlocks = new BraveBuildingBlocks(sender, spanHandler, tracing, tracer,
                    new BravePropagator(tracing), httpTracing, httpServerHandler, httpClientHandler, customizers,
                    testSpanHandler);
            ObservationHandler<? extends Observation.Context> tracingHandlers = this.handlers != null
                    ? this.handlers.apply(braveBuildingBlocks) : tracingHandlers(braveBuildingBlocks);
            registry.observationConfig().observationHandler(tracingHandlers);
            Consumer<BraveBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction
                    : closingFunction();

            return new ZipkinBraveSetup(closingFunction, braveBuildingBlocks);
        }

        private static BytesMessageSender sender(String zipkinUrl) {
            return URLConnectionSender.newBuilder()
                .connectTimeout(1000)
                .readTimeout(1000)
                .endpoint((zipkinUrl.endsWith("/") ? zipkinUrl.substring(0, zipkinUrl.length() - 1) : zipkinUrl)
                        + "/api/v2/spans")
                .build();
        }

        private static AsyncZipkinSpanHandler spanHandler(BytesMessageSender sender) {
            return AsyncZipkinSpanHandler.create(sender);
        }

        private static Tracer tracer(Tracing tracing) {
            return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
                    new BraveBaggageManager());
        }

        private static Tracing tracing(AsyncZipkinSpanHandler spanHandler, TestSpanHandler testSpanHandler,
                String applicationName) {
            return Tracing.newBuilder()
                .localServiceName(applicationName)
                .addSpanHandler(spanHandler)
                .addSpanHandler(testSpanHandler)
                .currentTraceContext(ThreadLocalCurrentTraceContext.create())
                .sampler(Sampler.ALWAYS_SAMPLE)
                .build();
        }

        private static HttpTracing httpTracing(Tracing tracing) {
            return HttpTracing.newBuilder(tracing).build();
        }

        private static Consumer<BraveBuildingBlocks> closingFunction() {
            return deps -> {
                deps.httpTracing.close();
                deps.tracing.close();
                AsyncZipkinSpanHandler spanHandler = deps.spanHandler;
                spanHandler.flush();
                spanHandler.close();
            };
        }

        @SuppressWarnings("rawtypes")
        private static ObservationHandler<Observation.Context> tracingHandlers(
                BraveBuildingBlocks braveBuildingBlocks) {
            Tracer tracer = braveBuildingBlocks.tracer;

            LinkedList<ObservationHandler<? extends Observation.Context>> handlers = new LinkedList<>();
            handlers.add(new PropagatingSenderTracingObservationHandler<>(tracer, braveBuildingBlocks.propagator));
            handlers.add(new PropagatingReceiverTracingObservationHandler<>(tracer, braveBuildingBlocks.propagator));
            handlers.add(new DefaultTracingObservationHandler(tracer));
            braveBuildingBlocks.customizers.accept(braveBuildingBlocks, handlers);

            return new ObservationHandler.FirstMatchingCompositeObservationHandler(handlers);
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     * @param registry observation registry to register the handlers against
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(ObservationRegistry registry, Consumer<Builder.BraveBuildingBlocks> consumer) {
        run(ZipkinBraveSetup.builder().register(registry), consumer);
    }

    /**
     * @param localZipkinBrave Brave setup with Zipkin
     * @param consumer runnable to run
     */
    public static void run(ZipkinBraveSetup localZipkinBrave, Consumer<Builder.BraveBuildingBlocks> consumer) {
        try {
            consumer.accept(localZipkinBrave.getBuildingBlocks());
        }
        finally {
            localZipkinBrave.close();
        }
    }

}
