/*
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
package io.micrometer.tracing.otel.bridge;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.BDDAssertions.then;

class OtelSpanTests {

    InMemorySpanExporter arrayListSpanProcessor = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(arrayListSpanProcessor))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    @Test
    void should_set_status_to_error_when_recording_exception() {
        OtelSpan otelSpan = new OtelSpan(otelTracer.spanBuilder("foo").startSpan());

        otelSpan.error(new RuntimeException("boom!")).end();

        SpanData poll = arrayListSpanProcessor.getFinishedSpanItems().get(0);
        then(poll.getStatus()).isEqualTo(StatusData.create(StatusCode.ERROR, "boom!"));
        then(poll.getEvents()).hasSize(1);
        then(poll.getEvents().get(0).getAttributes().asMap().values()).containsAnyOf("boom!");
    }

    // gh-1320
    @Test
    void should_have_empty_description_when_throwable_message_is_null() {
        OtelSpan otelSpan = new OtelSpan(otelTracer.spanBuilder("foo").startSpan());

        otelSpan.error(new RuntimeException((String) null)).end();

        SpanData poll = arrayListSpanProcessor.getFinishedSpanItems().get(0);
        then(poll.getStatus()).isEqualTo(StatusData.create(StatusCode.ERROR, ""));
        then(poll.getEvents()).hasSize(1);
        then(poll.getEvents().get(0).getAttributes().asMap().values()).contains("java.lang.RuntimeException");
    }

    @Test
    void should_be_equal_when_two_span_delegates_are_equal() {
        Span span = otelTracer.spanBuilder("foo").startSpan();
        OtelSpan otelSpan1 = new OtelSpan(span);
        OtelSpan otelSpan2 = new OtelSpan(span);

        then(otelSpan1).isEqualTo(otelSpan2);
        then(otelSpan2).isEqualTo(otelSpan1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void should_expose_sdk_span_and_preserve_span_identity_for_parented_spans(boolean extracted) {
        OtelTracer tracer = bridgeTracer();
        io.micrometer.tracing.Span parent = tracer.nextSpan().name("parent").start();
        io.micrometer.tracing.Span child = parentedSpan(tracer, parent, extracted);
        try {
            Span nativeSpan = OtelSpan.toOtel(child);
            then(nativeSpan).isInstanceOf(ReadableSpan.class);
            then(child.context().parentId()).isEqualTo(parent.context().spanId());
            try (io.micrometer.tracing.Tracer.SpanInScope scope = tracer.withSpan(child)) {
                io.micrometer.tracing.Span current = Objects.requireNonNull(tracer.currentSpan());
                then(Span.current()).isSameAs(nativeSpan);
                then(current).isEqualTo(child).hasSameHashCodeAs(child);
                then(child).isEqualTo(current);
                then(child).isEqualTo(OtelSpan.fromOtel(nativeSpan));
            }
        }
        finally {
            child.end();
            parent.end();
        }
        then(exportedSpan("child").getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void should_preserve_error_status_for_parented_spans(boolean extracted) {
        OtelTracer tracer = bridgeTracer();
        io.micrometer.tracing.Span parent = tracer.nextSpan().name("parent").start();
        io.micrometer.tracing.Span child = parentedSpan(tracer, parent, extracted);
        child.error(new IllegalStateException("report generation failed")).end();
        parent.end();

        then(exportedSpan("child").getStatus())
            .isEqualTo(StatusData.create(StatusCode.ERROR, "report generation failed"));
    }

    private OtelTracer bridgeTracer() {
        OtelCurrentTraceContext context = new OtelCurrentTraceContext();
        return new OtelTracer(this.otelTracer, context, event -> {
        }, new OtelBaggageManager(context, Collections.emptyList(), Collections.emptyList()));
    }

    private io.micrometer.tracing.Span parentedSpan(OtelTracer tracer, io.micrometer.tracing.Span parent,
            boolean extracted) {
        if (extracted) {
            OtelPropagator propagator = new OtelPropagator(this.openTelemetrySdk.getPropagators(), this.otelTracer);
            Map<String, String> carrier = Collections.singletonMap("b3",
                    parent.context().traceId() + "-" + parent.context().spanId() + "-1");
            return propagator.extract(carrier, Map::get).name("child").start();
        }
        return tracer.spanBuilder().setParent(parent.context()).name("child").start();
    }

    private SpanData exportedSpan(String name) {
        return this.arrayListSpanProcessor.getFinishedSpanItems()
            .stream()
            .filter(span -> span.getName().equals(name))
            .findFirst()
            .orElseThrow(AssertionError::new);
    }

    @Test
    void should_set_multi_value_tags() {
        OtelSpan otelSpan = new OtelSpan(otelTracer.spanBuilder("foo").startSpan());

        otelSpan.tagOfStrings("strings", Arrays.asList("s1", "s2", "s3"))
            .tagOfDoubles("doubles", Arrays.asList(1.0, 2.5, 3.7))
            .tagOfLongs("longs", Arrays.asList(2L, 3L, 4L))
            .tagOfBooleans("booleans", Arrays.asList(true, false, false))
            .end();

        SpanData poll = arrayListSpanProcessor.getFinishedSpanItems().get(0);
        Attributes attributes = poll.getAttributes();
        then(attributes.get(AttributeKey.stringArrayKey("strings"))).isEqualTo(Arrays.asList("s1", "s2", "s3"));
        then(attributes.get(AttributeKey.doubleArrayKey("doubles"))).isEqualTo(Arrays.asList(1.0, 2.5, 3.7));
        then(attributes.get(AttributeKey.longArrayKey("longs"))).isEqualTo(Arrays.asList(2L, 3L, 4L));
        then(attributes.get(AttributeKey.booleanArrayKey("booleans"))).isEqualTo(Arrays.asList(true, false, false));
    }

}
