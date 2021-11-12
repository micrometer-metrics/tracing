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
import java.util.List;

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
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

public final class LocalZipkinBraveSetup {

    public static void main(MeterRegistry meterRegistry, Runnable runnable) {
        AsyncReporter<Span> reporter = reporter();
        Tracing tracing = tracing(reporter);
        Tracer tracer = tracer(tracing);
        HttpTracing httpTracing = HttpTracing.newBuilder(tracing).build();
        HttpServerHandler httpServerHandler = new BraveHttpServerHandler(brave.http.HttpServerHandler.create(httpTracing));
        HttpClientHandler httpClientHandler = new BraveHttpClientHandler(brave.http.HttpClientHandler.create(httpTracing));
        @SuppressWarnings("rawtypes")
        List<TimerRecordingHandler> tracingHandlers = Arrays.asList(new HttpServerTracingRecordingHandler(tracer, httpServerHandler), new HttpClientTracingRecordingHandler(tracer, httpClientHandler), new DefaultTracingRecordingHandler(tracer));
        meterRegistry.config().timerRecordingListener(new TimerRecordingHandler.FirstMatchingCompositeTimerRecordingHandler(tracingHandlers));
        try {
            runnable.run();
        }
        finally {
            reporter.flush();
            reporter.close();
        }
    }

    private static AsyncReporter<Span> reporter() {
        return AsyncReporter
                .builder(URLConnectionSender.newBuilder().endpoint("http://localhost:9411/api/v2/spans").build())
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
}
