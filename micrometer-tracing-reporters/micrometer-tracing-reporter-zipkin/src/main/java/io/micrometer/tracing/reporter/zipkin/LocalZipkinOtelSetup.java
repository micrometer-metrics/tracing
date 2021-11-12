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
import java.util.Collections;
import java.util.List;
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
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

public final class LocalZipkinOtelSetup {

    public static void main(MeterRegistry meterRegistry, Runnable runnable) {
        ZipkinSpanExporter spanExporter = ZipkinSpanExporter.builder()
                .setSender(URLConnectionSender.newBuilder().endpoint("http://localhost:9411/api/v2/spans").build())
                .build();
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();
        Tracer otelTracer = sdk.getTracerProvider().get("io.micrometer.micrometer-tracing");
        OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
        OtelTracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
        }, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
        HttpServerHandler httpServerHandler = new OtelHttpServerHandler(sdk, null, null, Pattern.compile(""), new DefaultHttpServerAttributesExtractor());
        HttpClientHandler httpClientHandler = new OtelHttpClientHandler(sdk, null, null, SamplerFunction.alwaysSample(), new DefaultHttpClientAttributesExtractor());
        @SuppressWarnings("rawtypes")
        List<TimerRecordingHandler> tracingHandlers = Arrays.asList(new HttpServerTracingRecordingHandler(tracer, httpServerHandler), new HttpClientTracingRecordingHandler(tracer, httpClientHandler), new DefaultTracingRecordingHandler(tracer));
        meterRegistry.config().timerRecordingListener(new TimerRecordingHandler.FirstMatchingCompositeTimerRecordingHandler(tracingHandlers));
        try {
            runnable.run();
        }
        finally {
            spanExporter.flush();
            spanExporter.shutdown();
        }
    }
}
