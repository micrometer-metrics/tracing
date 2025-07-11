/**
 * Copyright 2023 the original author or authors.
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
package io.micrometer.tracing.test.reporter.inmemory;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.*;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;

/**
 * Provides Zipkin setup with OTel.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public final class InMemoryOtelSetup implements AutoCloseable {

    private final Consumer<Builder.OtelBuildingBlocks> closingFunction;

    private final Builder.OtelBuildingBlocks otelBuildingBlocks;

    InMemoryOtelSetup(Consumer<Builder.OtelBuildingBlocks> closingFunction,
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
     * @return builder for the {@link InMemoryOtelSetup}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for OTel with Zipkin.
     */
    public static class Builder {

        private String applicationName = "observability-test";

        private @Nullable Function<InMemorySpanExporter, SdkTracerProvider> sdkTracerProvider;

        private @Nullable Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk;

        private @Nullable Function<OpenTelemetrySdk, Tracer> tracer;

        private @Nullable Function<Tracer, OtelTracer> otelTracer;

        private @Nullable BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

        private @Nullable Function<OtelBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers;

        private @Nullable Consumer<OtelBuildingBlocks> closingFunction;

        /**
         * All OTel building blocks required to communicate with Zipkin.
         */
        public static class OtelBuildingBlocks implements BuildingBlocks {

            private final SdkTracerProvider sdkTracerProvider;

            private final OtelTracer otelTracer;

            private final OtelPropagator propagator;

            private final BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers;

            private final InMemorySpanExporter arrayListSpanProcessor;

            /**
             * Creates a new instance of {@link OtelBuildingBlocks}.
             * @param sdkTracerProvider sdk tracer provider
             * @param otelTracer otel tracer
             * @param propagator propagator
             * @param customizers observation customizers
             * @param arrayListSpanProcessor array list span processor
             */
            public OtelBuildingBlocks(SdkTracerProvider sdkTracerProvider, OtelTracer otelTracer,
                    OtelPropagator propagator,
                    BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers,
                    InMemorySpanExporter arrayListSpanProcessor) {
                this.sdkTracerProvider = sdkTracerProvider;
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

            @Override
            public OtelPropagator getPropagator() {
                return this.propagator;
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
         * Overrides the sdk tracer provider.
         * @param sdkTracerProvider sdk tracer provider function
         * @return this for chaining
         */
        public Builder sdkTracerProvider(Function<InMemorySpanExporter, SdkTracerProvider> sdkTracerProvider) {
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
                Function<OtelBuildingBlocks, ObservationHandler<? extends Observation.Context>> handlers) {
            this.handlers = handlers;
            return this;
        }

        /**
         * Overrides the closing function.
         * @param closingFunction closing function provider
         * @return this for chaining
         */
        public Builder closingFunction(Consumer<OtelBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * Registers setup.
         * @param registry observation registry to which the {@link ObservationHandler}
         * should be attached
         * @return setup with all OTel building blocks
         */
        public InMemoryOtelSetup register(ObservationRegistry registry) {
            InMemorySpanExporter arrayListSpanProcessor = InMemorySpanExporter.create();
            SdkTracerProvider sdkTracerProvider = this.sdkTracerProvider != null
                    ? this.sdkTracerProvider.apply(arrayListSpanProcessor)
                    : sdkTracerProvider(arrayListSpanProcessor, this.applicationName);
            OpenTelemetrySdk openTelemetrySdk = this.openTelemetrySdk != null
                    ? this.openTelemetrySdk.apply(sdkTracerProvider) : openTelemetrySdk(sdkTracerProvider);
            Tracer tracer = this.tracer != null ? this.tracer.apply(openTelemetrySdk) : tracer(openTelemetrySdk);
            OtelTracer otelTracer = this.otelTracer != null ? this.otelTracer.apply(tracer) : otelTracer(tracer);
            BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizers = this.customizers != null
                    ? this.customizers : (t, h) -> {
                    };
            OtelBuildingBlocks otelBuildingBlocks = new OtelBuildingBlocks(sdkTracerProvider, otelTracer,
                    new OtelPropagator(propagators(Collections.singletonList(B3Propagator.injectingMultiHeaders())),
                            tracer),
                    customizers, arrayListSpanProcessor);
            ObservationHandler<? extends Observation.Context> tracingHandlers = this.handlers != null
                    ? this.handlers.apply(otelBuildingBlocks) : tracingHandlers(otelBuildingBlocks);
            registry.observationConfig().observationHandler(tracingHandlers);
            Consumer<OtelBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction
                    : closingFunction();
            return new InMemoryOtelSetup(closingFunction, otelBuildingBlocks);
        }

        private static SdkTracerProvider sdkTracerProvider(InMemorySpanExporter arrayListSpanProcessor,
                String applicationName) {
            return SdkTracerProvider.builder()
                .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(arrayListSpanProcessor))
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.of(SERVICE_NAME, applicationName))))
                .build();
        }

        private static OpenTelemetrySdk openTelemetrySdk(SdkTracerProvider sdkTracerProvider) {
            return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
                .build();
        }

        private static Tracer tracer(OpenTelemetrySdk openTelemetrySdk) {
            return openTelemetrySdk.getTracerProvider().get("io.micrometer.micrometer-tracing");
        }

        private static OtelTracer otelTracer(Tracer tracer) {
            OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
            return new OtelTracer(tracer, otelCurrentTraceContext, event -> {
            }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
        }

        private static ContextPropagators propagators(List<TextMapPropagator> propagators) {
            if (propagators.isEmpty()) {
                return ContextPropagators.noop();
            }
            return ContextPropagators.create(TextMapPropagator.composite(propagators));
        }

        private static Consumer<OtelBuildingBlocks> closingFunction() {
            return deps -> deps.sdkTracerProvider.close();
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
     * @param observationRegistry observation registry to register the handlers against
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(ObservationRegistry observationRegistry, Consumer<Builder.OtelBuildingBlocks> consumer) {
        run(InMemoryOtelSetup.builder().register(observationRegistry), consumer);
    }

    /**
     * @param localZipkinBrave Brave setup with Zipkin
     * @param consumer runnable to run
     */
    public static void run(InMemoryOtelSetup localZipkinBrave, Consumer<Builder.OtelBuildingBlocks> consumer) {
        try {
            consumer.accept(localZipkinBrave.getBuildingBlocks());
        }
        finally {
            localZipkinBrave.close();
        }
    }

}
