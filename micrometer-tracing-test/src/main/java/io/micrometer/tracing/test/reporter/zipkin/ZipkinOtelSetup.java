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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.tracing.SamplerFunction;
import io.micrometer.tracing.handler.DefaultTracingRecordingHandler;
import io.micrometer.tracing.handler.HttpClientTracingRecordingHandler;
import io.micrometer.tracing.handler.HttpServerTracingRecordingHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import io.micrometer.tracing.otel.bridge.DefaultHttpClientAttributesExtractor;
import io.micrometer.tracing.otel.bridge.DefaultHttpServerAttributesExtractor;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelHttpClientHandler;
import io.micrometer.tracing.otel.bridge.OtelHttpServerHandler;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.test.reporter.zipkin.ZipkinOtelSetup.Builder.OtelBuildingBlocks;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Work in progress. Requires HTTP instrumentation dependendency to be on the classpath.
 *
 * Provides Zipkin setup with OTel.
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

        private String zipkinUrl = "http://localhost:9411";

        private Supplier<Sender> sender;

        private Function<Sender, ZipkinSpanExporter> zipkinSpanExporter;

        private Function<ZipkinSpanExporter, SdkTracerProvider> sdkTracerProvider;

        private Function<SdkTracerProvider, OpenTelemetrySdk> openTelemetrySdk;

        private Function<OpenTelemetrySdk, io.opentelemetry.api.trace.Tracer> tracer;

        private Function<io.opentelemetry.api.trace.Tracer, OtelTracer> otelTracer;

        private Function<OpenTelemetrySdk, HttpServerHandler> httpServerHandler;

        private Function<OpenTelemetrySdk, HttpClientHandler> httpClientHandler;

        private Function<OtelBuildingBlocks, TimerRecordingHandler> handlers;

        private Consumer<OtelBuildingBlocks> closingFunction;

        /**
         * All OTel building blocks required to communicate with Zipkin.
         */
        public static class OtelBuildingBlocks {

            public final Sender sender;

            public final ZipkinSpanExporter zipkinSpanExporter;

            public final SdkTracerProvider sdkTracerProvider;

            public final OpenTelemetrySdk openTelemetrySdk;

            public final io.opentelemetry.api.trace.Tracer tracer;

            public final OtelTracer otelTracer;

            public final HttpServerHandler httpServerHandler;

            public final HttpClientHandler httpClientHandler;

            public OtelBuildingBlocks(Sender sender, ZipkinSpanExporter zipkinSpanExporter, SdkTracerProvider sdkTracerProvider, OpenTelemetrySdk openTelemetrySdk, Tracer tracer, OtelTracer otelTracer, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler) {
                this.sender = sender;
                this.zipkinSpanExporter = zipkinSpanExporter;
                this.sdkTracerProvider = sdkTracerProvider;
                this.openTelemetrySdk = openTelemetrySdk;
                this.tracer = tracer;
                this.otelTracer = otelTracer;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
            }
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

        public Builder httpServerHandler(Function<OpenTelemetrySdk, HttpServerHandler> httpServerHandler) {
            this.httpServerHandler = httpServerHandler;
            return this;
        }

        public Builder httpClientHandler(Function<OpenTelemetrySdk, HttpClientHandler> httpClientHandler) {
            this.httpClientHandler = httpClientHandler;
            return this;
        }

        public Builder handlers(Function<OtelBuildingBlocks, TimerRecordingHandler> tracingHandlers) {
            this.handlers = tracingHandlers;
            return this;
        }

        public Builder closingFunction(Consumer<OtelBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * @param meterRegistry meter registry to which the {@link TimerRecordingHandler} should be attached
         * @return setup with all OTel building blocks
         */
        public ZipkinOtelSetup register(MeterRegistry meterRegistry) {
            Sender sender = this.sender != null ? this.sender.get() : sender(this.zipkinUrl);
            ZipkinSpanExporter zipkinSpanExporter = this.zipkinSpanExporter != null ? this.zipkinSpanExporter.apply(sender) : zipkinSpanExporter(sender);
            SdkTracerProvider sdkTracerProvider = this.sdkTracerProvider != null ? this.sdkTracerProvider.apply(zipkinSpanExporter) : sdkTracerProvider(zipkinSpanExporter);
            OpenTelemetrySdk openTelemetrySdk = this.openTelemetrySdk != null ? this.openTelemetrySdk.apply(sdkTracerProvider) : openTelemetrySdk(sdkTracerProvider);
            io.opentelemetry.api.trace.Tracer tracer = this.tracer != null ? this.tracer.apply(openTelemetrySdk) : tracer(openTelemetrySdk);
            OtelTracer otelTracer = this.otelTracer != null ? this.otelTracer.apply(tracer) : otelTracer(tracer);
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(openTelemetrySdk) : httpServerHandler(openTelemetrySdk);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(openTelemetrySdk) : httpClientHandler(openTelemetrySdk);
            OtelBuildingBlocks otelBuildingBlocks = new OtelBuildingBlocks(sender, zipkinSpanExporter, sdkTracerProvider, openTelemetrySdk, tracer, otelTracer, httpServerHandler, httpClientHandler);
            TimerRecordingHandler tracingHandlers = this.handlers != null ? this.handlers.apply(otelBuildingBlocks) : tracingHandlers(otelBuildingBlocks);
            meterRegistry.config().timerRecordingListener(tracingHandlers);
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

        private static SdkTracerProvider sdkTracerProvider(ZipkinSpanExporter zipkinSpanExporter) {
            return SdkTracerProvider.builder().setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
                    .addSpanProcessor(SimpleSpanProcessor.create(zipkinSpanExporter)).build();
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
                ZipkinSpanExporter reporter = deps.zipkinSpanExporter;
                reporter.flush();
                reporter.close();
            };
        }

        @SuppressWarnings("rawtypes")
        private static TimerRecordingHandler tracingHandlers(OtelBuildingBlocks otelBuildingBlocks) {
            OtelTracer tracer = otelBuildingBlocks.otelTracer;
            HttpServerHandler httpServerHandler = otelBuildingBlocks.httpServerHandler;
            HttpClientHandler httpClientHandler = otelBuildingBlocks.httpClientHandler;
            return new TimerRecordingHandler.FirstMatchingCompositeTimerRecordingHandler(Arrays.asList(new HttpServerTracingRecordingHandler(tracer, httpServerHandler), new HttpClientTracingRecordingHandler(tracer, httpClientHandler), new DefaultTracingRecordingHandler(tracer)));
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     * @param meterRegistry meter registry to register the handlers against
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(MeterRegistry meterRegistry, Consumer<OtelBuildingBlocks> consumer) {
        run(ZipkinOtelSetup.builder().register(meterRegistry), consumer);
    }

    /**
     * @param localZipkinBrave Brave setup with Zipkin
     * @param consumer runnable to run
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
