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

package io.micrometer.tracing.reporter.zipkin;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveHttpClientHandler;
import io.micrometer.tracing.brave.bridge.BraveHttpServerHandler;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingRecordingHandler;
import io.micrometer.tracing.handler.HttpClientTracingRecordingHandler;
import io.micrometer.tracing.handler.HttpServerTracingRecordingHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import io.micrometer.tracing.reporter.zipkin.ZipkinBraveSetup.Builder.BraveBuildingBlocks;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Work in progress. Requires HTTP instrumentation dependency to be on the classpath.
 *
 * Provides Zipkin setup with Brave.
 */
public final class ZipkinBraveSetup implements AutoCloseable {

    private final Consumer<BraveBuildingBlocks> closingFunction;

    private final BraveBuildingBlocks braveBuildingBlocks;

    ZipkinBraveSetup(Consumer<BraveBuildingBlocks> closingFunction, BraveBuildingBlocks braveBuildingBlocks) {
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
    public BraveBuildingBlocks getBuildingBlocks() {
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

        private Supplier<AsyncReporter<Span>> reporter;

        private Function<Reporter, Tracing> tracing;

        private Function<Tracing, Tracer> tracer;

        private Function<Tracing, HttpTracing> httpTracing;

        private Function<HttpTracing, HttpServerHandler> httpServerHandler;

        private Function<HttpTracing, HttpClientHandler> httpClientHandler;

        private Function<BraveBuildingBlocks, TimerRecordingHandler> handlers;

        private Consumer<BraveBuildingBlocks> closingFunction;

        /**
         * All Brave building blocks required to communicate with Zipkin.
         */
        public static class BraveBuildingBlocks {
            public final AsyncReporter<Span> reporter;
            public final Tracing tracing;
            public final Tracer tracer;
            public final HttpTracing httpTracing;
            public final HttpServerHandler httpServerHandler;
            public final HttpClientHandler httpClientHandler;

            public BraveBuildingBlocks(AsyncReporter<Span> reporter, Tracing tracing, Tracer tracer, HttpTracing httpTracing, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler) {
                this.reporter = reporter;
                this.tracing = tracing;
                this.tracer = tracer;
                this.httpTracing = httpTracing;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
            }
        }

        public Builder reporter(Supplier<AsyncReporter<Span>> reporter) {
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
            AsyncReporter<Span> reporter = this.reporter != null ? this.reporter.get() : reporter();
            Tracing tracing = this.tracing != null ? this.tracing.apply(reporter) : tracing(reporter);
            Tracer tracer = this.tracer != null ? this.tracer.apply(tracing) : tracer(tracing);
            HttpTracing httpTracing = this.httpTracing != null ? this.httpTracing.apply(tracing) : httpTracing(tracing);
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(httpTracing) : httpServerHandler(httpTracing);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(httpTracing) : httpClientHandler(httpTracing);
            BraveBuildingBlocks braveBuildingBlocks = new BraveBuildingBlocks(reporter, tracing, tracer, httpTracing, httpServerHandler, httpClientHandler);
            TimerRecordingHandler tracingHandlers = this.handlers != null ? this.handlers.apply(braveBuildingBlocks) : tracingHandlers(braveBuildingBlocks);
            meterRegistry.config().timerRecordingListener(tracingHandlers);
            Consumer<BraveBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction : closingFunction(braveBuildingBlocks);
            return new ZipkinBraveSetup(closingFunction, braveBuildingBlocks);
        }

        private static AsyncReporter<Span> reporter() {
            return AsyncReporter
                    .builder(URLConnectionSender.newBuilder()
                            .connectTimeout(1000)
                            .readTimeout(1000)
                            .endpoint("http://localhost:9411/api/v2/spans").build())
                    .build();
        }

        private static Tracer tracer(Tracing tracing) {
            return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());
        }

        private static Tracing tracing(AsyncReporter<Span> reporter) {
            return Tracing.newBuilder()
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

        private static Consumer<BraveBuildingBlocks> closingFunction(BraveBuildingBlocks braveBuildingBlocks) {
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
            return new TimerRecordingHandler.FirstMatchingCompositeTimerRecordingHandler(Arrays.asList(new HttpServerTracingRecordingHandler(tracer, httpServerHandler), new HttpClientTracingRecordingHandler(tracer, httpClientHandler), new DefaultTracingRecordingHandler(tracer)));
        }

    }

    /**
     * Runs the given lambda with Zipkin setup.
     * @param meterRegistry meter registry to register the handlers against
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(MeterRegistry meterRegistry, Consumer<BraveBuildingBlocks> consumer) {
        run(ZipkinBraveSetup.builder().register(meterRegistry), consumer);
    }

    /**
     * @param localZipkinBrave Brave setup with Zipkin
     * @param consumer runnable to run
     */
    public static void run(ZipkinBraveSetup localZipkinBrave, Consumer<BraveBuildingBlocks> consumer) {
        try {
            consumer.accept(localZipkinBrave.getBuildingBlocks());
        }
        finally {
            localZipkinBrave.close();
        }
    }
}
