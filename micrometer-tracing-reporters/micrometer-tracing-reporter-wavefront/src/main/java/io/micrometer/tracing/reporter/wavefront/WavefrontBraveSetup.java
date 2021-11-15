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

package io.micrometer.tracing.reporter.wavefront;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;

import brave.Tracing;
import brave.handler.SpanHandler;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.clients.WavefrontClient;
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

/**
 * Work in progress. Requires HTTP instrumentation dependency to be on the classpath.
 *
 * Provides Wavefront setup with Brave.
 */
public final class WavefrontBraveSetup implements AutoCloseable {

    private final Consumer<Builder.BraveBuildingBlocks> closingFunction;

    private final Builder.BraveBuildingBlocks braveBuildingBlocks;

    WavefrontBraveSetup(Consumer<Builder.BraveBuildingBlocks> closingFunction, Builder.BraveBuildingBlocks braveBuildingBlocks) {
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

        private String source;

        private String applicationName;

        private String serviceName;

        private Function<MeterRegistry, WavefrontSpanHandler> wavefrontSpanHandler;

        private Function<WavefrontBraveSpanHandler, Tracing> tracing;

        private Function<Tracing, Tracer> tracer;

        private Function<Tracing, HttpTracing> httpTracing;

        private Function<HttpTracing, HttpServerHandler> httpServerHandler;

        private Function<HttpTracing, HttpClientHandler> httpClientHandler;

        private Function<BraveBuildingBlocks, TimerRecordingHandler> handlers;

        private Consumer<BraveBuildingBlocks> closingFunction;

        public Builder(String server, String token) {
            this.server = server;
            this.token = token;
        }

        /**
         * All Brave building blocks required to communicate with Zipkin.
         */
        public static class BraveBuildingBlocks {
            public final WavefrontSpanHandler wavefrontSpanHandler;
            public final Tracing tracing;
            public final Tracer tracer;
            public final HttpTracing httpTracing;
            public final HttpServerHandler httpServerHandler;
            public final HttpClientHandler httpClientHandler;

            public BraveBuildingBlocks(WavefrontSpanHandler wavefrontSpanHandler, Tracing tracing, Tracer tracer, HttpTracing httpTracing, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler) {
                this.wavefrontSpanHandler = wavefrontSpanHandler;
                this.tracing = tracing;
                this.tracer = tracer;
                this.httpTracing = httpTracing;
                this.httpServerHandler = httpServerHandler;
                this.httpClientHandler = httpClientHandler;
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

        public Builder tracing(Function<WavefrontBraveSpanHandler, Tracing> tracing) {
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
        public WavefrontBraveSetup register(MeterRegistry meterRegistry) {
            WavefrontSpanHandler wavefrontSpanHandler = this.wavefrontSpanHandler != null ? this.wavefrontSpanHandler.apply(meterRegistry) : wavefrontSpanHandler(meterRegistry);
            WavefrontBraveSpanHandler wavefrontBraveSpanHandler = wavefrontBraveSpanHandler(wavefrontSpanHandler);
            Tracing tracing = this.tracing != null ? this.tracing.apply(wavefrontBraveSpanHandler) : tracing(wavefrontBraveSpanHandler);
            Tracer tracer = this.tracer != null ? this.tracer.apply(tracing) : tracer(tracing);
            HttpTracing httpTracing = this.httpTracing != null ? this.httpTracing.apply(tracing) : httpTracing(tracing);
            HttpServerHandler httpServerHandler = this.httpServerHandler != null ? this.httpServerHandler.apply(httpTracing) : httpServerHandler(httpTracing);
            HttpClientHandler httpClientHandler = this.httpClientHandler != null ? this.httpClientHandler.apply(httpTracing) : httpClientHandler(httpTracing);
            BraveBuildingBlocks braveBuildingBlocks = new BraveBuildingBlocks(wavefrontSpanHandler, tracing, tracer, httpTracing, httpServerHandler, httpClientHandler);
            TimerRecordingHandler tracingHandlers = this.handlers != null ? this.handlers.apply(braveBuildingBlocks) : tracingHandlers(braveBuildingBlocks);
            meterRegistry.config().timerRecordingListener(tracingHandlers);
            Consumer<BraveBuildingBlocks> closingFunction = this.closingFunction != null ? this.closingFunction : closingFunction();
            return new WavefrontBraveSetup(closingFunction, braveBuildingBlocks);
        }

        private WavefrontSpanHandler wavefrontSpanHandler(MeterRegistry meterRegistry) {
            return new WavefrontSpanHandler(50000, new WavefrontClient.Builder(this.server, this.token).build(), meterRegistry, this.source, new ApplicationTags.Builder(this.applicationName, this.serviceName).build(), new HashSet<>());
        }

        private static WavefrontBraveSpanHandler wavefrontBraveSpanHandler(WavefrontSpanHandler handler) {
            return new WavefrontBraveSpanHandler(handler);
        }

        private static Tracer tracer(Tracing tracing) {
            return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());
        }

        private static Tracing tracing(SpanHandler spanHandler) {
            return Tracing.newBuilder()
                    .addSpanHandler(spanHandler)
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
                WavefrontSpanHandler reporter = deps.wavefrontSpanHandler;
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
     * @param server Wavefront's server URL
     * @param token Wavefront's token
     * @param meterRegistry meter registry to register the handlers against
     * @param consumer lambda to be executed with the building blocks
     */
    public static void run(String server, String token, MeterRegistry meterRegistry, Consumer<Builder.BraveBuildingBlocks> consumer) {
        run(WavefrontBraveSetup.builder(server, token).register(meterRegistry), consumer);
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
