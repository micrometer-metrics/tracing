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

package io.micrometer.tracing.test.reporter.zipkin;

import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.ObservationHandler;
import io.micrometer.tracing.SamplerFunction;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.HttpClientTracingObservationHandler;
import io.micrometer.tracing.handler.HttpServerTracingObservationHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.DefaultHttpClientAttributesGetter;
import io.micrometer.tracing.otel.bridge.DefaultHttpServerAttributesExtractor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.micrometer.tracing.otel.bridge.OtelHttpClientHandler;
import io.micrometer.tracing.otel.bridge.OtelHttpServerHandler;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.micrometer.tracing.test.reporter.zipkin.ZipkinOtelSetup.Builder.OtelBuildingBlocks;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Provides Zipkin setup with OTel.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public final class ZipkinOtelSetup implements AutoCloseable {

    private final Consumer<OtelBuildingBlocks> closingFunction;

    private final OtelBuildingBlocks otelBuildingBlocks;

    ZipkinOtelSetup(Consumer<OtelBuildingBlocks> closingFunction, OtelBuildingBlocks otelBuildingBlocks) {
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
    public OtelBuildingBlocks getBuildingBlocks() {
        return this.otelBuildingBlocks;
    }

    /**
     * @return builder for the {@link ZipkinOtelSetup}
     */
    public static ZipkinOtelSetup.Builder builder() {
        return new ZipkinOtelSetup.Builder();
    }

    /**
     * Builder for OTel with Zipkin.
     */
    public static class Builder {

        private String applicationName = "observability-test";

        private String zipkinUrl = "http://localhost:9411";

        private Supplier<Sender> sender;

        private Function<Sender, ZipkinSpanExporter> zipkinSpanExporter;

        private Function<ZipkinSpanExporter, SdkTracerProvider> sdkTracerProvider;

        private Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk;

        private Function<OpenTelemetrySdk, io.opentelemetry.api.trace.Tracer> tracer;

        private Function<io.opentelemetry.api.trace.Tracer, OtelTracer> otelTracer;

        private BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers;

        private Function<OpenTelemetrySdk, HttpServerHandler> httpServerHandler;

        private Function<OpenTelemetrySdk, HttpClientHandler> httpClientHandler;

        private Function<OtelBuildingBlocks, ObservationHandler> handlers;

        private Consumer<OtelBuildingBlocks> closingFunction;

        /**
         * All OTel building blocks required to communicate with Zipkin.
         */
        public static class OtelBuildingBlocks implements BuildingBlocks {

            public final Sender sender;

            public final ZipkinSpanExporter zipkinSpanExporter;

            public final SdkTracerProvider sdkTracerProvider;

            public final OpenTelemetrySdk openTelemetrySdk;

            public final io.opentelemetry.api.trace.Tracer tracer;

            public final OtelTracer otelTracer;

            public final OtelPropagator propagator;

            public final HttpServerHandler httpServerHandler;

            public final HttpClientHandler httpClientHandler;

            public final BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers;

            private final ArrayListSpanProcessor arrayListSpanProcessor;

            public OtelBuildingBlocks(Sender sender, ZipkinSpanExporter zipkinSpanExporter, SdkTracerProvider sdkTracerProvider, OpenTelemetrySdk openTelemetrySdk, Tracer tracer, OtelTracer otelTracer, OtelPropagator propagator, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler, BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers, ArrayListSpanProcessor arrayListSpanProcessor) {
                this.sender = sender;
                this.zipkinSpanExporter = zipkinSpanExporter;
                this.sdkTracerProvider = sdkTracerProvider;
                this.openTelemetrySdk = openTelemetrySdk;
                this.tracer = tracer;
                this.otelTracer = otelTracer;
                this.propagator = propagator;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
                this.customizers = customizers;
                this.arrayListSpanProcessor = arrayListSpanProcessor;
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

            @Override
            public List<FinishedSpan> getFinishedSpans() {
                return this.arrayListSpanProcessor.spans().stream().map(OtelFinishedSpan::fromOtel).collect(Collectors.toList());
            }

            @Override
            public OtelPropagator getPropagator() {
                return this.propagator;
            }
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder zipkinUrl(String zipkinUrl) {
            this.zipkinUrl = zipkinUrl;
            return this;
        }

        public Builder zipkinSender(Supplier<Sender> sender) {
            this.sender = sender;
            return this;
        }

        public Builder zipkinSpanExporter(Function<Sender, ZipkinSpanExporter> zipkinSpanExporter) {
            this.zipkinSpanExporter = zipkinSpanExporter;
            return this;
        }

        public Builder sdkTracerProvider(Function<ZipkinSpanExporter, SdkTracerProvider> sdkTracerProvider) {
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

        public Builder httpClientHandler(Function<OpenTelemetrySdk, HttpClientHandler> httpClientHandler) {
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
        public ZipkinOtelSetup register(MeterRegistry meterRegistry) {
            Sender sender = this.sender != null ? this.sender.get() : sender(this.zipkinUrl);
            ZipkinSpanExporter zipkinSpanExporter = this.zipkinSpanExporter != null ? this.zipkinSpanExporter.apply(sender) : zipkinSpanExporter(sender);
            ArrayListSpanProcessor arrayListSpanProcessor = new ArrayListSpanProcessor();
            SdkTracerProvider sdkTracerProvider = this.sdkTracerProvider != null ? this.sdkTracerProvider.apply(zipkinSpanExporter) : sdkTracerProvider(zipkinSpanExporter, arrayListSpanProcessor, this.applicationName);
            OpenTelemetrySdk openTelemetrySdk = this.openTelemetrySdk != null ? this.openTelemetrySdk.apply(sdkTracerProvider) : openTelemetrySdk(sdkTracerProvider);
            io.opentelemetry.api.trace.Tracer tracer = this.tracer != null ? this.tracer.apply(openTelemetrySdk) : tracer(openTelemetrySdk);
            OtelTracer otelTracer = this.otelTracer != null ? this.otelTracer.apply(tracer) : otelTracer(tracer);
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(openTelemetrySdk) : httpServerHandler(openTelemetrySdk);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(openTelemetrySdk) : httpClientHandler(openTelemetrySdk);
            BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizers = this.customizers != null ? this.customizers : (t, h) -> {
            };
            OtelBuildingBlocks otelBuildingBlocks = new OtelBuildingBlocks(sender, zipkinSpanExporter, sdkTracerProvider, openTelemetrySdk, tracer, otelTracer, new OtelPropagator(propagators(Collections.singletonList(B3Propagator.injectingMultiHeaders())), tracer), httpServerHandler, httpClientHandler, customizers, arrayListSpanProcessor);
            ObservationHandler tracingHandlers = this.handlers != null ? this.handlers.apply(otelBuildingBlocks) : tracingHandlers(otelBuildingBlocks);
            meterRegistry.observationConfig().observationHandler(tracingHandlers);
            Consumer<OtelBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction : closingFunction();
            return new ZipkinOtelSetup(closingFunction, otelBuildingBlocks);
        }

        private static Sender sender(String zipkinUrl) {
            return URLConnectionSender.newBuilder()
                    .connectTimeout(1000)
                    .readTimeout(1000)
                    .endpoint((zipkinUrl.endsWith("/") ? zipkinUrl.substring(0, zipkinUrl.length() - 1) : zipkinUrl) + "/api/v2/spans").build();
        }

        private static ZipkinSpanExporter zipkinSpanExporter(Sender sender) {
            return ZipkinSpanExporter.builder()
                    .setSender(sender)
                    .build();
        }

        private static SdkTracerProvider sdkTracerProvider(ZipkinSpanExporter zipkinSpanExporter, ArrayListSpanProcessor arrayListSpanProcessor, String applicationName) {
            return SdkTracerProvider.builder().setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
                    .addSpanProcessor(arrayListSpanProcessor)
                    .addSpanProcessor(BatchSpanProcessor.builder(zipkinSpanExporter)
                            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                            .setExporterTimeout(300, TimeUnit.MILLISECONDS)
                            .build())
                    .setResource(Resource.getDefault()
                            .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, applicationName))))
                    .build();
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

        private static ContextPropagators propagators(List<TextMapPropagator> propagators) {
            if (propagators.isEmpty()) {
                return ContextPropagators.noop();
            }
            return ContextPropagators.create(TextMapPropagator.composite(propagators));
        }

        private static HttpServerHandler httpServerHandler(OpenTelemetrySdk openTelemetrySdk) {
            return new OtelHttpServerHandler(openTelemetrySdk, null, null, Pattern.compile(""), new DefaultHttpServerAttributesExtractor());
        }

        private static HttpClientHandler httpClientHandler(OpenTelemetrySdk openTelemetrySdk) {
            return new OtelHttpClientHandler(openTelemetrySdk, null, null, SamplerFunction.alwaysSample(), new DefaultHttpClientAttributesGetter());
        }

        private static Consumer<OtelBuildingBlocks> closingFunction() {
            return deps -> deps.sdkTracerProvider.close();
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
     * @param meterRegistry meter registry to register the handlers against
     * @param consumer      lambda to be executed with the building blocks
     */
    public static void run(MeterRegistry meterRegistry, Consumer<OtelBuildingBlocks> consumer) {
        run(ZipkinOtelSetup.builder().register(meterRegistry), consumer);
    }

    /**
     * @param localZipkinBrave Brave setup with Zipkin
     * @param consumer         runnable to run
     */
    public static void run(ZipkinOtelSetup localZipkinBrave, Consumer<OtelBuildingBlocks> consumer) {
        try {
            consumer.accept(localZipkinBrave.getBuildingBlocks());
        }
        finally {
            localZipkinBrave.close();
        }
    }
}
