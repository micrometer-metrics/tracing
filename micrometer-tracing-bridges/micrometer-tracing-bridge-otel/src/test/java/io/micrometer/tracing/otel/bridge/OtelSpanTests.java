/**
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
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.BDDAssertions.then;

class OtelSpanTests {

    ArrayListSpanProcessor arrayListSpanProcessor = new ArrayListSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(arrayListSpanProcessor)
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

        SpanData poll = arrayListSpanProcessor.spans().poll();
        then(poll.getStatus()).isEqualTo(StatusData.create(StatusCode.ERROR, "boom!"));
        then(poll.getEvents()).hasSize(1);
        then(poll.getEvents().get(0).getAttributes().asMap().values()).containsAnyOf("boom!");
    }

    @Test
    void should_be_equal_when_two_span_delegates_are_equal() {
        Span span = otelTracer.spanBuilder("foo").startSpan();
        OtelSpan otelSpan = new OtelSpan(span);
        OtelSpan otelSpanFromSpanContext = new OtelSpan(
                new SpanFromSpanContext(span, null, new OtelTraceContext(span)));

        then(otelSpan).isEqualTo(otelSpanFromSpanContext);
        then(otelSpanFromSpanContext).isEqualTo(otelSpan);
    }

    @Test
    void should_set_multi_value_tags() {
        OtelSpan otelSpan = new OtelSpan(otelTracer.spanBuilder("foo").startSpan());

        otelSpan.tagOfStrings("strings", Arrays.asList("s1", "s2", "s3"))
            .tagOfDoubles("doubles", Arrays.asList(1.0, 2.5, 3.7))
            .tagOfLongs("longs", Arrays.asList(2L, 3L, 4L))
            .tagOfBooleans("booleans", Arrays.asList(true, false, false))
            .end();

        SpanData poll = arrayListSpanProcessor.spans().poll();
        Attributes attributes = poll.getAttributes();
        then(attributes.get(AttributeKey.stringArrayKey("strings"))).isEqualTo(Arrays.asList("s1", "s2", "s3"));
        then(attributes.get(AttributeKey.doubleArrayKey("doubles"))).isEqualTo(Arrays.asList(1.0, 2.5, 3.7));
        then(attributes.get(AttributeKey.longArrayKey("longs"))).isEqualTo(Arrays.asList(2L, 3L, 4L));
        then(attributes.get(AttributeKey.booleanArrayKey("booleans"))).isEqualTo(Arrays.asList(true, false, false));
    }

}
