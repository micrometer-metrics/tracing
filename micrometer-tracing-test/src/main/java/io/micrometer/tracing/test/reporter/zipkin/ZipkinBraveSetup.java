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

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.*;
import io.micrometer.tracing.handler.DefaultTracingRecordingHandler;
import io.micrometer.tracing.handler.HttpClientTracingRecordingHandler;
import io.micrometer.tracing.handler.HttpServerTracingRecordingHandler;
import io.micrometer.tracing.handler.TracingRecordingHandlerSpanCustomizer;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Work in progress. Requires HTTP instrumentation dependency to be on the classpath.
 *
 * Provides Zipkin setup with Brave.
 */
public final class ZipkinBraveSetup implements AutoCloseable {

    private final Consumer<Builder.BraveBuildingBlocks> closingFunction;

    private final Builder.BraveBuildingBlocks braveBuildingBlocks;

    ZipkinBraveSetup(Consumer<Builder.BraveBuildingBlocks> closingFunction, Builder.BraveBuildingBlocks braveBuildingBlocks) {
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

        private Supplier<Sender> sender;

        private Function<Sender, AsyncReporter<Span>> reporter;

        private Function<Reporter, Tracing> tracing;

        private Function<Tracing, Tracer> tracer;

        private Function<Tracing, HttpTracing> httpTracing;

        private Function<HttpTracing, HttpServerHandler> httpServerHandler;

        private Function<HttpTracing, HttpClientHandler> httpClientHandler;

        private Function<BraveBuildingBlocks, TimerRecordingHandler> handlers;

        private List<TracingRecordingHandlerSpanCustomizer> customizers;

        private Consumer<BraveBuildingBlocks> closingFunction;

        /**
         * All Brave building blocks required to communicate with Zipkin.
         */
        public static class BraveBuildingBlocks {
            public final Sender sender;

            public final AsyncReporter<Span> reporter;

            public final Tracing tracing;

            public final Tracer tracer;

            public final HttpTracing httpTracing;

            public final HttpServerHandler httpServerHandler;

            public final HttpClientHandler httpClientHandler;

            public final List<TracingRecordingHandlerSpanCustomizer> customizers;

            public BraveBuildingBlocks(Sender sender, AsyncReporter<Span> reporter, Tracing tracing, Tracer tracer, HttpTracing httpTracing, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler, List<TracingRecordingHandlerSpanCustomizer> customizers) {
                this.sender = sender;
                this.reporter = reporter;
                this.tracing = tracing;
                this.tracer = tracer;
                this.httpTracing = httpTracing;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
                this.customizers = customizers;
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

        public Builder sender(Supplier<Sender> sender) {
            this.sender = sender;
            return this;
        }

        public Builder reporter(Function<Sender, AsyncReporter<Span>> reporter) {
            this.reporter = reporter;
            return this;
        }

        public Builder tracing(Function<Reporter, Tracing> tracing) {
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

        public Builder tracingRecordingHandlerSpanCustomizers(List<TracingRecordingHandlerSpanCustomizer> customizers) {
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

        public Builder handlers(Function<BraveBuildingBlocks, TimerRecordingHandler> tracingHandlers) {
            this.handlers = tracingHandlers;
            return this;
        }

        public Builder closingFunction(Consumer<BraveBuildingBlocks> closingFunction) {
            this.closingFunction = closingFunction;
            return this;
        }

        /**
         * @param meterRegistry meter registry to which the {@link TimerRecordingHandler} should be attached
         * @return setup with all Brave building blocks
         */
        public ZipkinBraveSetup register(MeterRegistry meterRegistry) {
            Sender sender = this.sender != null ? this.sender.get() : sender(this.zipkinUrl);
            AsyncReporter<Span> reporter = this.reporter != null ? this.reporter.apply(sender) : reporter(sender);
            Tracing tracing = this.tracing != null ? this.tracing.apply(reporter) : tracing(reporter, this.applicationName);
            Tracer tracer = this.tracer != null ? this.tracer.apply(tracing) : tracer(tracing);
            HttpTracing httpTracing = this.httpTracing != null ? this.httpTracing.apply(tracing) : httpTracing(tracing);
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(httpTracing) : httpServerHandler(httpTracing);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(httpTracing) : httpClientHandler(httpTracing);
            List<TracingRecordingHandlerSpanCustomizer> customizers = this.customizers != null ? this.customizers : new ArrayList<>();
            BraveBuildingBlocks braveBuildingBlocks = new BraveBuildingBlocks(sender, reporter, tracing, tracer, httpTracing, httpServerHandler, httpClientHandler, customizers);
            TimerRecordingHandler tracingHandlers = this.handlers != null ? this.handlers.apply(braveBuildingBlocks) : tracingHandlers(braveBuildingBlocks);
            meterRegistry.config().timerRecordingHandler(tracingHandlers);
            Consumer<BraveBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction : closingFunction();
            return new ZipkinBraveSetup(closingFunction, braveBuildingBlocks);
        }

        private static Sender sender(String zipkinUrl) {
            return URLConnectionSender.newBuilder()
                    .connectTimeout(1000)
                    .readTimeout(1000)
                    .endpoint((zipkinUrl.endsWith("/") ? zipkinUrl.substring(0, zipkinUrl.length() - 1) : zipkinUrl) + "/api/v2/spans").build();
        }

        private static AsyncReporter<Span> reporter(Sender sender) {
            return AsyncReporter
                    .builder(sender)
                    .build();
        }

        private static Tracer tracer(Tracing tracing) {
            return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());
        }

        private static Tracing tracing(AsyncReporter<Span> reporter, String applicationName) {
            return Tracing.newBuilder()
                    .localServiceName(applicationName)
                    .addSpanHandler(ZipkinSpanHandler.newBuilder(reporter).build())
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
                AsyncReporter reporter = deps.reporter;
                reporter.flush();
                reporter.close();
            };
        }

        @SuppressWarnings("rawtypes")
        private static TimerRecordingHandler tracingHandlers(BraveBuildingBlocks braveBuildingBlocks) {
            Tracer tracer = braveBuildingBlocks.tracer;
            HttpServerHandler httpServerHandler = braveBuildingBlocks.httpServerHandler;
            HttpClientHandler httpClientHandler = braveBuildingBlocks.httpClientHandler;
            return new TimerRecordingHandler.FirstMatchingCompositeTimerRecordingHandler(Arrays.asList(new HttpServerTracingRecordingHandler(tracer, braveBuildingBlocks.customizers, httpServerHandler), new HttpClientTracingRecordingHandler(tracer, braveBuildingBlocks.customizers, httpClientHandler), new DefaultTracingRecordingHandler(tracer, braveBuildingBlocks.customizers)));
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     * @param meterRegistry meter registry to register the handlers against
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(MeterRegistry meterRegistry, Consumer<Builder.BraveBuildingBlocks> consumer) {
        run(ZipkinBraveSetup.builder().register(meterRegistry), consumer);
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
