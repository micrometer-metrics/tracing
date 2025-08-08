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
package io.micrometer.tracing.test.reporter.wavefront;

import brave.Tracing;
import brave.handler.SpanHandler;
import brave.http.HttpTracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.clients.WavefrontClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.*;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.reporter.wavefront.WavefrontBraveSpanHandler;
import io.micrometer.tracing.reporter.wavefront.WavefrontSpanHandler;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides Wavefront setup with Brave.
 *
 * @deprecated since 1.6.0 because Wavefront's End of Life Announcement
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Deprecated
public final class WavefrontBraveSetup implements AutoCloseable {

    // To be used in tests ONLY
    static @Nullable WavefrontSpanHandler mockHandler;

    private final Consumer<Builder.BraveBuildingBlocks> closingFunction;

    private final Builder.BraveBuildingBlocks braveBuildingBlocks;

    WavefrontBraveSetup(Consumer<Builder.BraveBuildingBlocks> closingFunction,
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
     * @param server Wavefront server URL
     * @param token Wavefront token
     * @return builder for the {@link WavefrontBraveSetup}
     */
    public static WavefrontBraveSetup.Builder builder(String server, String token) {
        return new WavefrontBraveSetup.Builder(server, token);
    }

    /**
     * Builder for Brave with Wavefront.
     */
    public static class Builder {

        private final String server;

        private final String token;

        private @Nullable String source;

        private @Nullable String applicationName;

        private @Nullable String serviceName;

        private @Nullable Function<MeterRegistry, WavefrontSpanHandler> wavefrontSpanHandler;

        private @Nullable Function<WavefrontBraveSpanHandler, Tracing> tracing;

        private @Nullable Function<Tracing, Tracer> tracer;

        private @Nullable Function<Tracing, HttpTracing> httpTracing;

        private @Nullable Function<BraveBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers;

        private @Nullable BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

        private @Nullable Consumer<BraveBuildingBlocks> closingFunction;

        /**
         * Creates a new instance of {@link Builder}.
         * @param server server URL
         * @param token authentication token
         */
        public Builder(String server, String token) {
            this.server = server;
            this.token = token;
        }

        /**
         * All Brave building blocks required to communicate with Zipkin.
         */
        @SuppressWarnings("rawtypes")
        public static class BraveBuildingBlocks implements BuildingBlocks {

            private final WavefrontSpanHandler wavefrontSpanHandler;

            private final Tracing tracing;

            private final Tracer tracer;

            private final BravePropagator propagator;

            private final HttpTracing httpTracing;

            private final BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

            private final TestSpanHandler testSpanHandler;

            /**
             * Creates a new instance of {@link BraveBuildingBlocks}.
             * @param wavefrontSpanHandler Wavefront span handler
             * @param tracing tracing
             * @param tracer tracer
             * @param propagator propagator
             * @param httpTracing http tracing
             * @param customizers observation customizers
             * @param testSpanHandler test span handler
             */
            public BraveBuildingBlocks(WavefrontSpanHandler wavefrontSpanHandler, Tracing tracing, Tracer tracer,
                    BravePropagator propagator, HttpTracing httpTracing,
                    BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers,
                    TestSpanHandler testSpanHandler) {
                this.wavefrontSpanHandler = wavefrontSpanHandler;
                this.tracing = tracing;
                this.tracer = tracer;
                this.propagator = propagator;
                this.httpTracing = httpTracing;
                this.customizers = customizers;
                this.testSpanHandler = testSpanHandler;
            }

            @Override
            public Tracer getTracer() {
                return this.tracer;
            }

            @Override
            public BravePropagator getPropagator() {
                return this.propagator;
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
         * Overrides the source.
         * @param source name of the source
         * @return this for chaining
         */
        public Builder source(String source) {
            this.source = source;
            return this;
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
         * Overrides the service name.
         * @param serviceName name of the service
         * @return this for chaining
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Overrides the wavefront span handler.
         * @param wavefrontSpanHandler wavefront span handler provider
         * @return this for chaining
         */
        public Builder wavefrontSpanHandler(Function<MeterRegistry, WavefrontSpanHandler> wavefrontSpanHandler) {
            this.wavefrontSpanHandler = wavefrontSpanHandler;
            return this;
        }

        /**
         * Overrides Tracing.
         * @param tracing tracing provider
         * @return this for chaining
         */
        public Builder tracing(Function<WavefrontBraveSpanHandler, Tracing> tracing) {
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
         * @param meterRegistry meter registry to set up Wavefront Sender
         * @param registry observation registry to which the {@link ObservationHandler}
         * should be attached
         * @return setup with all Brave building blocks
         */
        public WavefrontBraveSetup register(MeterRegistry meterRegistry, ObservationRegistry registry) {
            WavefrontSpanHandler wavefrontSpanHandler = wavefrontSpanHandlerOrMock(meterRegistry);
            WavefrontBraveSpanHandler wavefrontBraveSpanHandler = wavefrontBraveSpanHandler(wavefrontSpanHandler);
            TestSpanHandler testSpanHandler = new TestSpanHandler();
            Tracing tracing = this.tracing != null ? this.tracing.apply(wavefrontBraveSpanHandler)
                    : tracing(wavefrontBraveSpanHandler, testSpanHandler);
            Tracer tracer = this.tracer != null ? this.tracer.apply(tracing) : tracer(tracing);
            HttpTracing httpTracing = this.httpTracing != null ? this.httpTracing.apply(tracing) : httpTracing(tracing);
            BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers = this.customizers != null
                    ? this.customizers : (t, h) -> {
                    };
            BraveBuildingBlocks braveBuildingBlocks = new BraveBuildingBlocks(wavefrontSpanHandler, tracing, tracer,
                    new BravePropagator(tracing), httpTracing, customizers, testSpanHandler);
            ObservationHandler<? extends Observation.Context> tracingHandlers = this.handlers != null
                    ? this.handlers.apply(braveBuildingBlocks) : tracingHandlers(braveBuildingBlocks);
            registry.observationConfig().observationHandler(tracingHandlers);
            Consumer<BraveBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction
                    : closingFunction();
            return new WavefrontBraveSetup(closingFunction, braveBuildingBlocks);
        }

        private WavefrontSpanHandler wavefrontSpanHandlerOrMock(MeterRegistry registry) {
            if (mockHandler == null) {
                return this.wavefrontSpanHandler != null ? this.wavefrontSpanHandler.apply(registry)
                        : wavefrontSpanHandler(registry);
            }
            return mockHandler;
        }

        private WavefrontSpanHandler wavefrontSpanHandler(MeterRegistry meterRegistry) {
            return new WavefrontSpanHandler(50000, new WavefrontClient.Builder(this.server, this.token).build(),
                    new MeterRegistrySpanMetrics(meterRegistry), Objects.requireNonNull(this.source),
                    new ApplicationTags.Builder(this.applicationName, this.serviceName).build(), new HashSet<>());
        }

        private static WavefrontBraveSpanHandler wavefrontBraveSpanHandler(WavefrontSpanHandler handler) {
            return new WavefrontBraveSpanHandler(handler);
        }

        private static Tracer tracer(Tracing tracing) {
            return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
                    new BraveBaggageManager());
        }

        private static Tracing tracing(SpanHandler spanHandler, TestSpanHandler testSpanHandler) {
            return Tracing.newBuilder()
                .traceId128Bit(true)
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
                WavefrontSpanHandler reporter = deps.wavefrontSpanHandler;
                reporter.close();
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
     * @param server Wavefront's server URL
     * @param token Wavefront's token
     * @param observationRegistry observation registry to register the handlers against
     * @param meterRegistry meter registry
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(String server, String token, ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry, Consumer<Builder.BraveBuildingBlocks> consumer) {
        run(WavefrontBraveSetup.builder(server, token).register(meterRegistry, observationRegistry), consumer);
    }

    /**
     * @param setup Brave setup with Wavefront
     * @param consumer runnable to run
     */
    public static void run(WavefrontBraveSetup setup, Consumer<Builder.BraveBuildingBlocks> consumer) {
        try {
            consumer.accept(setup.getBuildingBlocks());
        }
        finally {
            setup.close();
        }
    }

}
