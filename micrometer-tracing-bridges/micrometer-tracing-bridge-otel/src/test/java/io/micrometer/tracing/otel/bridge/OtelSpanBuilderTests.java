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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class OtelSpanBuilderTests {

    ArrayListSpanProcessor processor = new ArrayListSpanProcessor();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(processor)
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    @Test
    void should_set_child_span_when_using_builders() {

        Span.Builder builder = new OtelSpanBuilder(otelTracer.spanBuilder("foo"));
        Span parentSpan = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());

        Span child = builder.setParent(parentSpan.context()).start();

        then(child.context().traceId()).isEqualTo(parentSpan.context().traceId());
        then(child.context().parentId()).isEqualTo(parentSpan.context().spanId());
    }

    @Test
    void should_set_links() {
        Span.Builder builder = new OtelSpanBuilder(otelTracer.spanBuilder("foo"));
        Span span1 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span2 = OtelSpan.fromOtel(otelTracer.spanBuilder("baz").startSpan());

        builder.addLink(new Link(span1.context())).addLink(new Link(span2, tags())).start().end();

        SpanData finishedSpan = processor.spans().poll();
        then(finishedSpan.getLinks().get(0).getSpanContext())
            .isEqualTo(OtelTraceContext.toOtelSpanContext(span1.context()));
        then(finishedSpan.getLinks().get(1).getSpanContext())
            .isEqualTo(OtelTraceContext.toOtelSpanContext(span2.context()));
        then(finishedSpan.getLinks()
            .get(1)
            .getAttributes()
            .asMap()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().getKey(), e -> e.getValue().toString()))).isEqualTo(tags());
    }

    @Test
    void should_set_non_string_tags() {
        new OtelSpanBuilder(otelTracer.spanBuilder("foo")).tag("string", "string")
            .tag("double", 2.5)
            .tag("long", 2)
            .tag("boolean", true)
            .start()
            .end();

        SpanData poll = processor.spans().poll();
        then(poll.getAttributes().get(AttributeKey.stringKey("string"))).isEqualTo("string");
        then(poll.getAttributes().get(AttributeKey.doubleKey("double"))).isEqualTo(2.5);
        then(poll.getAttributes().get(AttributeKey.longKey("long"))).isEqualTo(2L);
        then(poll.getAttributes().get(AttributeKey.booleanKey("boolean"))).isTrue();
    }

    private Map<String, Object> tags() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

}
