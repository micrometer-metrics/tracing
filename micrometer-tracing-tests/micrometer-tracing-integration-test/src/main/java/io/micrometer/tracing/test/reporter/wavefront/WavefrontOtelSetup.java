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

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.clients.WavefrontClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.*;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.reporter.wavefront.WavefrontOtelSpanExporter;
import io.micrometer.tracing.reporter.wavefront.WavefrontSpanHandler;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides Wavefront setup with OTel.
 *
 * @deprecated since 1.6.0 because Wavefront's End of Life Announcement
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Deprecated
public final class WavefrontOtelSetup implements AutoCloseable {

    // To be used in tests ONLY
    static @Nullable WavefrontSpanHandler mockHandler;

    private final Consumer<Builder.OtelBuildingBlocks> closingFunction;

    private final Builder.OtelBuildingBlocks otelBuildingBlocks;

    WavefrontOtelSetup(Consumer<Builder.OtelBuildingBlocks> closingFunction,
            Builder.OtelBuildingBlocks otelBuildingBlocks) {
        this.closingFunction = closingFunction;
        this.otelBuildingBlocks = otelBuildingBlocks;
    }

    @Override
    public void close() {
        this.closingFunction.accept(this.otelBuildingBlocks);
    }

    /**
     * @return all the OTel building blocks required to communicate with Zipkin
     */
    public Builder.OtelBuildingBlocks getBuildingBlocks() {
        return this.otelBuildingBlocks;
    }

    /**
     * @param server Wavefront server URL
     * @param token Wavefront token
     * @return builder for the {@link WavefrontOtelSetup}
     */
    public static WavefrontOtelSetup.Builder builder(String server, String token) {
        return new WavefrontOtelSetup.Builder(server, token);
    }

    /**
     * Builder for OTel with Wavefront.
     */
    public static class Builder {

        private final String server;

        private final String token;

        private @Nullable String source;

        private @Nullable String applicationName;

        private @Nullable String serviceName;

        private @Nullable Function<MeterRegistry, WavefrontSpanHandler> wavefrontSpanHandler;

        private @Nullable Function<WavefrontOtelSpanExporter, SdkTracerProvider> sdkTracerProvider;

        private @Nullable Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk;

        private @Nullable Function<OpenTelemetrySdk, io.opentelemetry.api.trace.Tracer> tracer;

        private @Nullable Function<io.opentelemetry.api.trace.Tracer, OtelTracer> otelTracer;

        private @Nullable BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

        private @Nullable Function<OtelBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers;

        private @Nullable Consumer<OtelBuildingBlocks> closingFunction;

        /**
         * Creates a new instance of {@link Builder}.
         * @param server server URL
         * @param token token
         */
        public Builder(String server, String token) {
            this.server = server;
            this.token = token;
        }

        /**
         * All OTel building blocks required to communicate with Zipkin.
         */
        @SuppressWarnings("rawtypes")
        public static class OtelBuildingBlocks implements BuildingBlocks {

            private final WavefrontOtelSpanExporter wavefrontOTelSpanExporter;

            private final OtelTracer otelTracer;

            private final OtelPropagator propagator;

            private final BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

            private final InMemorySpanExporter arrayListSpanProcessor;

            /**
             * Creates a new instance of {@link OtelBuildingBlocks}.
             * @param wavefrontOTelSpanExporter span handler
             * @param otelTracer otel tracer
             * @param propagator otel propagator
             * @param customizers observation customizers
             * @param arrayListSpanProcessor array list span processor
             */
            public OtelBuildingBlocks(WavefrontOtelSpanExporter wavefrontOTelSpanExporter, OtelTracer otelTracer,
                    OtelPropagator propagator,
                    BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers,
                    InMemorySpanExporter arrayListSpanProcessor) {
                this.wavefrontOTelSpanExporter = wavefrontOTelSpanExporter;
                this.otelTracer = otelTracer;
                this.propagator = propagator;
                this.customizers = customizers;
                this.arrayListSpanProcessor = arrayListSpanProcessor;
            }

            @Override
            public io.micrometer.tracing.Tracer getTracer() {
                return this.otelTracer;
            }

            @Override
            public Propagator getPropagator() {
                return this.propagator;
            }

            @Override
            public BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> getCustomizers() {
                return this.customizers;
            }

            @Override
            public List<FinishedSpan> getFinishedSpans() {
                return this.arrayListSpanProcessor.getFinishedSpanItems()
                    .stream()
                    .map(OtelFinishedSpan::fromOtel)
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
         * Overrides the sdk tracer provider.
         * @param sdkTracerProvider sdk tracer provider function
         * @return this for chaining
         */
        public Builder sdkTracerProvider(Function<WavefrontOtelSpanExporter, SdkTracerProvider> sdkTracerProvider) {
            this.sdkTracerProvider = sdkTracerProvider;
            return this;
        }

        /**
         * Overrides the opentelemetry sdk provider.
         * @param openTelemetrySdk opentelemetry sdk provider
         * @return this for chaining
         */
        public Builder openTelemetrySdk(Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk) {
            this.openTelemetrySdk = openTelemetrySdk;
            return this;
        }

        /**
         * Overrides Tracer.
         * @param tracer tracer provider
         * @return this for chaining
         */
        public Builder tracer(Function<OpenTelemetrySdk, Tracer> tracer) {
            this.tracer = tracer;
            return this;
        }

        /**
         * Overrides OTel Tracer.
         * @param otelTracer OTel tracer provider
         * @return this for chaining
         */
        public Builder otelTracer(Function<Tracer, OtelTracer> otelTracer) {
            this.otelTracer = otelTracer;
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
                Function<Builder.OtelBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers) {
            this.handlers = handlers;
            return this;
        }

        /**
         * Overrides the closing function.
         * @param closingFunction closing function provider
         * @return this for chaining
         */
        public Builder closingFunction(Consumer<Builder.OtelBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * Registers setup.
         * @param observationRegistry registry to register the handlers against
         * @param meterRegistry meter registry
         * @return setup with all OTel building blocks
         */
        public WavefrontOtelSetup register(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
            WavefrontSpanHandler wavefrontSpanHandler = wavefrontSpanHandlerOrMock(meterRegistry);
            WavefrontOtelSpanExporter wavefrontOTelSpanExporter = wavefrontOtelSpanHandler(wavefrontSpanHandler);
            InMemorySpanExporter arrayListSpanProcessor = InMemorySpanExporter.create();
            SdkTracerProvider sdkTracerProvider = this.sdkTracerProvider != null
                    ? this.sdkTracerProvider.apply(wavefrontOTelSpanExporter)
                    : sdkTracerProvider(wavefrontOTelSpanExporter, arrayListSpanProcessor);
            OpenTelemetrySdk openTelemetrySdk = this.openTelemetrySdk != null
                    ? this.openTelemetrySdk.apply(sdkTracerProvider) : openTelemetrySdk(sdkTracerProvider);
            io.opentelemetry.api.trace.Tracer tracer = this.tracer != null ? this.tracer.apply(openTelemetrySdk)
                    : tracer(openTelemetrySdk);
            OtelTracer otelTracer = this.otelTracer != null ? this.otelTracer.apply(tracer) : otelTracer(tracer);
            BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers = this.customizers != null
                    ? this.customizers : (b, t) -> {
                    };
            OtelBuildingBlocks otelBuildingBlocks = new OtelBuildingBlocks(wavefrontOTelSpanExporter, otelTracer,
                    new OtelPropagator(propagators(Collections.singletonList(B3Propagator.injectingMultiHeaders())),
                            tracer),
                    customizers, arrayListSpanProcessor);
            ObservationHandler<? extends Observation.Context> tracingHandlers = this.handlers != null
                    ? this.handlers.apply(otelBuildingBlocks) : tracingHandlers(otelBuildingBlocks);
            observationRegistry.observationConfig().observationHandler(tracingHandlers);
            Consumer<OtelBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction
                    : closingFunction();
            return new WavefrontOtelSetup(closingFunction, otelBuildingBlocks);
        }

        private static ContextPropagators propagators(List<TextMapPropagator> propagators) {
            if (propagators.isEmpty()) {
                return ContextPropagators.noop();
            }
            return ContextPropagators.create(TextMapPropagator.composite(propagators));
        }

        private WavefrontSpanHandler wavefrontSpanHandlerOrMock(MeterRegistry meterRegistry) {
            if (mockHandler == null) {
                return this.wavefrontSpanHandler != null ? this.wavefrontSpanHandler.apply(meterRegistry)
                        : wavefrontSpanHandler(meterRegistry);
            }
            return mockHandler;
        }

        private WavefrontSpanHandler wavefrontSpanHandler(MeterRegistry meterRegistry) {
            return new WavefrontSpanHandler(50000, new WavefrontClient.Builder(this.server, this.token).build(),
                    new MeterRegistrySpanMetrics(meterRegistry), Objects.requireNonNull(this.source),
                    new ApplicationTags.Builder(this.applicationName, this.serviceName).build(), new HashSet<>());
        }

        private static WavefrontOtelSpanExporter wavefrontOtelSpanHandler(WavefrontSpanHandler handler) {
            return new WavefrontOtelSpanExporter(handler);
        }

        private static SdkTracerProvider sdkTracerProvider(WavefrontOtelSpanExporter spanHandler,
                InMemorySpanExporter arrayListSpanProcessor) {
            return SdkTracerProvider.builder()
                .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(arrayListSpanProcessor))
                .addSpanProcessor(SimpleSpanProcessor.create(spanHandler))
                .build();
        }

        private static OpenTelemetrySdk openTelemetrySdk(SdkTracerProvider sdkTracerProvider) {
            return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
                .build();
        }

        private static io.opentelemetry.api.trace.Tracer tracer(OpenTelemetrySdk openTelemetrySdk) {
            return openTelemetrySdk.getTracerProvider().get("io.micrometer.micrometer-tracing");
        }

        private static OtelTracer otelTracer(io.opentelemetry.api.trace.Tracer tracer) {
            OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
            return new OtelTracer(tracer, otelCurrentTraceContext, event -> {
            }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
        }

        private static Consumer<OtelBuildingBlocks> closingFunction() {
            return deps -> {
                WavefrontOtelSpanExporter reporter = deps.wavefrontOTelSpanExporter;
                reporter.flush();
                reporter.close();
            };
        }

        @SuppressWarnings("rawtypes")
        private static ObservationHandler<Observation.Context> tracingHandlers(OtelBuildingBlocks otelBuildingBlocks) {
            OtelTracer tracer = otelBuildingBlocks.otelTracer;

            LinkedList<ObservationHandler<? extends Observation.Context>> handlers = new LinkedList<>();
            handlers.add(new PropagatingSenderTracingObservationHandler<>(tracer, otelBuildingBlocks.propagator));
            handlers.add(new PropagatingReceiverTracingObservationHandler<>(tracer, otelBuildingBlocks.propagator));
            handlers.add(new DefaultTracingObservationHandler(tracer));
            otelBuildingBlocks.customizers.accept(otelBuildingBlocks, handlers);

            return new ObservationHandler.FirstMatchingCompositeObservationHandler(handlers);
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     * @param server Wavefront's server URL
     * @param token Wavefront's token
     * @param observationRegistry registry to register the handlers against
     * @param meterRegistry meter registry
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(String server, String token, ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry, Consumer<Builder.OtelBuildingBlocks> consumer) {
        run(WavefrontOtelSetup.builder(server, token).register(observationRegistry, meterRegistry), consumer);
    }

    /**
     * @param setup OTel setup with Wavefront
     * @param consumer runnable to run
     */
    public static void run(WavefrontOtelSetup setup, Consumer<Builder.OtelBuildingBlocks> consumer) {
        try {
            consumer.accept(setup.getBuildingBlocks());
        }
        finally {
            setup.close();
        }
    }

}
