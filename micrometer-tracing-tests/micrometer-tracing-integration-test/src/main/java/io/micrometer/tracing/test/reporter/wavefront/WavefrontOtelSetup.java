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

package io.micrometer.tracing.test.reporter.wavefront;

import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.clients.WavefrontClient;
import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.observation.ObservationHandler;
import io.micrometer.tracing.SamplerFunction;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.HttpClientTracingObservationHandler;
import io.micrometer.tracing.handler.HttpServerTracingObservationHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import io.micrometer.tracing.otel.bridge.DefaultHttpClientAttributesExtractor;
import io.micrometer.tracing.otel.bridge.DefaultHttpServerAttributesExtractor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelHttpClientHandler;
import io.micrometer.tracing.otel.bridge.OtelHttpServerHandler;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.reporter.wavefront.WavefrontOtelSpanHandler;
import io.micrometer.tracing.reporter.wavefront.WavefrontSpanHandler;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * Provides Wavefront setup with OTel.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public final class WavefrontOtelSetup implements AutoCloseable {

    // To be used in tests ONLY
    static WavefrontSpanHandler mockHandler;

    private final Consumer<Builder.OtelBuildingBlocks> closingFunction;

    private final Builder.OtelBuildingBlocks otelBuildingBlocks;

    WavefrontOtelSetup(Consumer<Builder.OtelBuildingBlocks> closingFunction, Builder.OtelBuildingBlocks otelBuildingBlocks) {
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
     * @param token  Wavefront token
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

        private String source;

        private String applicationName;

        private String serviceName;

        private Function<MeterRegistry, WavefrontSpanHandler> wavefrontSpanHandler;

        private Function<WavefrontOtelSpanHandler, SdkTracerProvider> sdkTracerProvider;

        private Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk;

        private Function<OpenTelemetrySdk, io.opentelemetry.api.trace.Tracer> tracer;

        private Function<io.opentelemetry.api.trace.Tracer, OtelTracer> otelTracer;

        private BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers;

        private Function<OpenTelemetrySdk, HttpServerHandler> httpServerHandler;

        private Function<OpenTelemetrySdk, HttpClientHandler> httpClientHandler;

        private Function<OtelBuildingBlocks, ObservationHandler> handlers;

        private Consumer<OtelBuildingBlocks> closingFunction;

        public Builder(String server, String token) {
            this.server = server;
            this.token = token;
        }

        /**
         * All OTel building blocks required to communicate with Zipkin.
         */
        @SuppressWarnings("rawtypes")
        public static class OtelBuildingBlocks implements BuildingBlocks {
            public final WavefrontOtelSpanHandler wavefrontOTelSpanHandler;

            public final SdkTracerProvider sdkTracerProvider;

            public final OpenTelemetrySdk openTelemetrySdk;

            public final io.opentelemetry.api.trace.Tracer tracer;

            public final OtelTracer otelTracer;

            public final HttpServerHandler httpServerHandler;

            public final HttpClientHandler httpClientHandler;

            public final BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers;

            public OtelBuildingBlocks(WavefrontOtelSpanHandler wavefrontOTelSpanHandler, SdkTracerProvider sdkTracerProvider, OpenTelemetrySdk openTelemetrySdk, Tracer tracer, OtelTracer otelTracer, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler, BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers) {
                this.wavefrontOTelSpanHandler = wavefrontOTelSpanHandler;
                this.sdkTracerProvider = sdkTracerProvider;
                this.openTelemetrySdk = openTelemetrySdk;
                this.tracer = tracer;
                this.otelTracer = otelTracer;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
                this.customizers = customizers;
            }

            @Override
            public io.micrometer.tracing.Tracer getTracer() {
                return this.otelTracer;
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
            public BiConsumer<BuildingBlocks, Deque<ObservationHandler>> getCustomizers() {
                return this.customizers;
            }
        }


        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder wavefrontSpanHandler(Function<MeterRegistry, WavefrontSpanHandler> wavefrontSpanHandler) {
            this.wavefrontSpanHandler = wavefrontSpanHandler;
            return this;
        }

        public Builder sdkTracerProvider(Function<WavefrontOtelSpanHandler, SdkTracerProvider> sdkTracerProvider) {
            this.sdkTracerProvider = sdkTracerProvider;
            return this;
        }

        public Builder openTelemetrySdk(Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk) {
            this.openTelemetrySdk = openTelemetrySdk;
            return this;
        }

        public Builder tracer(Function<OpenTelemetrySdk, io.opentelemetry.api.trace.Tracer> tracer) {
            this.tracer = tracer;
            return this;
        }

        public Builder otelTracer(Function<io.opentelemetry.api.trace.Tracer, OtelTracer> otelTracer) {
            this.otelTracer = otelTracer;
            return this;
        }

        public Builder observationHandlerCustomizer(BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers) {
            this.customizers = customizers;
            return this;
        }

        public Builder httpServerHandler(Function<OpenTelemetrySdk, HttpServerHandler> httpServerHandler) {
            this.httpServerHandler = httpServerHandler;
            return this;
        }

        public Builder httpClientHandler(Function<OpenTelemetrySdk,
                HttpClientHandler> httpClientHandler) {
            this.httpClientHandler = httpClientHandler;
            return this;
        }

        public Builder handlers(Function<OtelBuildingBlocks, ObservationHandler> tracingHandlers) {
            this.handlers = tracingHandlers;
            return this;
        }

        public Builder closingFunction(Consumer<OtelBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * Registers setup.
         *
         * @param meterRegistry meter registry to which the {@link ObservationHandler} should be attached
         * @return setup with all OTel building blocks
         */
        public WavefrontOtelSetup register(MeterRegistry meterRegistry) {
            WavefrontSpanHandler wavefrontSpanHandler = wavefrontSpanHandlerOrMock(meterRegistry);
            WavefrontOtelSpanHandler wavefrontOTelSpanHandler = wavefrontOtelSpanHandler(wavefrontSpanHandler);
            SdkTracerProvider sdkTracerProvider = this.sdkTracerProvider != null ? this.sdkTracerProvider.apply(wavefrontOTelSpanHandler) : sdkTracerProvider(wavefrontOTelSpanHandler);
            OpenTelemetrySdk openTelemetrySdk = this.openTelemetrySdk != null ? this.openTelemetrySdk.apply(sdkTracerProvider) : openTelemetrySdk(sdkTracerProvider);
            io.opentelemetry.api.trace.Tracer tracer = this.tracer != null ? this.tracer.apply(openTelemetrySdk) : tracer(openTelemetrySdk);
            OtelTracer otelTracer = this.otelTracer != null ? this.otelTracer.apply(tracer) : otelTracer(tracer);
            BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers = this.customizers != null ? this.customizers : (b, t) -> {
            };
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(openTelemetrySdk) : httpServerHandler(openTelemetrySdk);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(openTelemetrySdk) : httpClientHandler(openTelemetrySdk);
            OtelBuildingBlocks otelBuildingBlocks = new OtelBuildingBlocks(wavefrontOTelSpanHandler, sdkTracerProvider, openTelemetrySdk, tracer, otelTracer, httpServerHandler, httpClientHandler, customizers);
            ObservationHandler tracingHandlers = this.handlers != null ? this.handlers.apply(otelBuildingBlocks) : tracingHandlers(otelBuildingBlocks);
            meterRegistry.observationConfig().observationHandler(tracingHandlers);
            Consumer<OtelBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction : closingFunction();
            return new WavefrontOtelSetup(closingFunction, otelBuildingBlocks);
        }

        private WavefrontSpanHandler wavefrontSpanHandlerOrMock(MeterRegistry meterRegistry) {
            if (mockHandler == null) {
                return this.wavefrontSpanHandler != null ? this.wavefrontSpanHandler.apply(meterRegistry) : wavefrontSpanHandler(meterRegistry);
            }
            return mockHandler;
        }

        private WavefrontSpanHandler wavefrontSpanHandler(MeterRegistry meterRegistry) {
            return new WavefrontSpanHandler(50000, new WavefrontClient.Builder(this.server, this.token).build(), meterRegistry, this.source, new ApplicationTags.Builder(this.applicationName, this.serviceName).build(), new HashSet<>());
        }

        private static WavefrontOtelSpanHandler wavefrontOtelSpanHandler(WavefrontSpanHandler handler) {
            return new WavefrontOtelSpanHandler(handler);
        }

        private static SdkTracerProvider sdkTracerProvider(WavefrontOtelSpanHandler spanHandler) {
            return SdkTracerProvider.builder().setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
                    .addSpanProcessor(SimpleSpanProcessor.create(spanHandler)).build();
        }

        private static OpenTelemetrySdk openTelemetrySdk(SdkTracerProvider sdkTracerProvider) {
            return OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();
        }

        private static io.opentelemetry.api.trace.Tracer tracer(OpenTelemetrySdk openTelemetrySdk) {
            return openTelemetrySdk.getTracerProvider().get("io.micrometer.micrometer-tracing");
        }

        private static OtelTracer otelTracer(io.opentelemetry.api.trace.Tracer tracer) {
            OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
            return new OtelTracer(tracer, otelCurrentTraceContext, event -> {
            }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
        }

        private static HttpServerHandler httpServerHandler(OpenTelemetrySdk openTelemetrySdk) {
            return new OtelHttpServerHandler(openTelemetrySdk, null, null, Pattern.compile(""), new DefaultHttpServerAttributesExtractor());
        }

        private static HttpClientHandler httpClientHandler(OpenTelemetrySdk openTelemetrySdk) {
            return new OtelHttpClientHandler(openTelemetrySdk, null, null, SamplerFunction.alwaysSample(), new DefaultHttpClientAttributesExtractor());
        }

        private static Consumer<OtelBuildingBlocks> closingFunction() {
            return deps -> {
                WavefrontOtelSpanHandler reporter = deps.wavefrontOTelSpanHandler;
                reporter.flush();
                reporter.close();
            };
        }

        @SuppressWarnings("rawtypes")
        private static ObservationHandler tracingHandlers(OtelBuildingBlocks otelBuildingBlocks) {
            OtelTracer tracer = otelBuildingBlocks.otelTracer;
            HttpServerHandler httpServerHandler = otelBuildingBlocks.httpServerHandler;
            HttpClientHandler httpClientHandler = otelBuildingBlocks.httpClientHandler;
            LinkedList<ObservationHandler> handlers = new LinkedList<>(Arrays.asList(new HttpServerTracingObservationHandler(tracer, httpServerHandler), new HttpClientTracingObservationHandler(tracer, httpClientHandler), new DefaultTracingObservationHandler(tracer)));
            otelBuildingBlocks.customizers.accept(otelBuildingBlocks, handlers);
            return new ObservationHandler.FirstMatchingCompositeObservationHandler(handlers);
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     *
     * @param server        Wavefront's server URL
     * @param token         Wavefront's token
     * @param meterRegistry meter registry to register the handlers against
     * @param consumer      lambda to be executed with the building blocks
     */
    public static void run(String server, String token, MeterRegistry meterRegistry, Consumer<Builder.OtelBuildingBlocks> consumer) {
        run(WavefrontOtelSetup.builder(server, token).register(meterRegistry), consumer);
    }

    /**
     * @param setup    OTel setup with Wavefront
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
