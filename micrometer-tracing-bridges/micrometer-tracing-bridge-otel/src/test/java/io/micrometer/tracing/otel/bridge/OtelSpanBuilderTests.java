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

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

class OtelSpanBuilderTests {

    InMemorySpanExporter processor = InMemorySpanExporter.create();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
        .addSpanProcessor(SimpleSpanProcessor.create(processor))
        .build();

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

    OtelTracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
    });

    @Test
    void should_set_child_span_when_using_builders() {

        Span.Builder builder = new OtelSpanBuilder(otelTracer).name("foo");
        Span parentSpan = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());

        Span child = builder.setParent(parentSpan.context()).start();

        then(child.context().traceId()).isEqualTo(parentSpan.context().traceId());
        then(child.context().parentId()).isEqualTo(parentSpan.context().spanId());
    }

    @Test
    void should_set_links() {
        Span.Builder builder = new OtelSpanBuilder(otelTracer).name("foo");
        Span span1 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span2 = OtelSpan.fromOtel(otelTracer.spanBuilder("baz").startSpan());

        builder.addLink(new Link(span1.context())).addLink(new Link(span2, tags())).start().end();

        SpanData finishedSpan = processor.getFinishedSpanItems().get(0);
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
        new OtelSpanBuilder(otelTracer).name("foo")
            .tag("string", "string")
            .tag("double", 2.5)
            .tag("long", 2)
            .tag("boolean", true)
            .start()
            .end();

        SpanData poll = processor.getFinishedSpanItems().get(0);
        then(poll.getAttributes().get(AttributeKey.stringKey("string"))).isEqualTo("string");
        then(poll.getAttributes().get(AttributeKey.doubleKey("double"))).isEqualTo(2.5);
        then(poll.getAttributes().get(AttributeKey.longKey("long"))).isEqualTo(2L);
        then(poll.getAttributes().get(AttributeKey.booleanKey("boolean"))).isTrue();
    }

    @Test
    void should_set_multi_value_tags() {
        new OtelSpanBuilder(otelTracer).name("foo")
            .tagOfStrings("strings", Arrays.asList("s1", "s2", "s3"))
            .tagOfDoubles("doubles", Arrays.asList(1.0, 2.5, 3.7))
            .tagOfLongs("longs", Arrays.asList(2L, 3L, 4L))
            .tagOfBooleans("booleans", Arrays.asList(true, false, false))
            .start()
            .end();

        SpanData poll = processor.getFinishedSpanItems().get(0);
        Attributes attributes = poll.getAttributes();
        then(attributes.get(AttributeKey.stringArrayKey("strings"))).isEqualTo(Arrays.asList("s1", "s2", "s3"));
        then(attributes.get(AttributeKey.doubleArrayKey("doubles"))).isEqualTo(Arrays.asList(1.0, 2.5, 3.7));
        then(attributes.get(AttributeKey.longArrayKey("longs"))).isEqualTo(Arrays.asList(2L, 3L, 4L));
        then(attributes.get(AttributeKey.booleanArrayKey("booleans"))).isEqualTo(Arrays.asList(true, false, false));
    }

    @Test
    void should_honor_parent_context_using_tracecontextbuilder() {
        Span foo = tracer.spanBuilder().name("foo").start();

        TraceContext parentCtx = tracer.traceContextBuilder()
            .traceId(foo.context().traceId())
            .spanId(foo.context().spanId())
            .sampled(foo.context().sampled())
            .build();

        Span span = tracer.spanBuilder().setParent(parentCtx).name("test-span").start();

        then(span.context().traceId()).isEqualTo(foo.context().traceId());
    }

    @Test
    void should_add_event_with_exception_and_set_error_status() {
        new OtelSpanBuilder(otelTracer).name("foo").error(new RuntimeException("something went wrong")).start().end();

        SpanData spanData = processor.getFinishedSpanItems().get(0);
        assertThat(spanData).isNotNull();
        List<EventData> events = spanData.getEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getName()).isEqualTo("exception");
        assertThat(events.get(0).getAttributes().asMap()).containsEntry(AttributeKey.stringKey("exception.message"),
                "something went wrong");
        assertThat(spanData.getStatus()).isEqualTo(StatusData.create(StatusCode.ERROR, "something went wrong"));
    }

    private Map<String, Object> tags() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

    @Test
    void should_start_span_with_baggage_stored_in_parent_trace_context() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
            .addSpanProcessor(new BaggageTaggingSpanProcessor(Arrays.asList("foo", "current")))
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
        io.opentelemetry.api.trace.Tracer tracer = tracerProvider.get("io.micrometer.micrometer-tracing");
        io.opentelemetry.api.trace.Span parentSpan = tracer.spanBuilder("parent").startSpan();
        // e.g. what OtelPropagator#extract produces: the baggage lives in the stored
        // context only
        Context storedContext = Context.root().with(parentSpan).with(Baggage.builder().put("foo", "bar").build());
        TraceContext parent = new OtelTraceContext(storedContext, parentSpan.getSpanContext(), parentSpan);

        Span child;
        try (Scope scope = Baggage.builder().put("current", "value").build().makeCurrent()) {
            child = new OtelSpanBuilder(tracer).name("child").setParent(parent).start();
        }
        child.end();

        SpanData finishedSpan = exporter.getFinishedSpanItems().get(0);
        then(finishedSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanContext().getSpanId());
        then(finishedSpan.getAttributes().get(AttributeKey.stringKey("foo"))).isEqualTo("bar");
        then(finishedSpan.getAttributes().get(AttributeKey.stringKey("current"))).isEqualTo("value");
    }

    @Test
    void should_prefer_baggage_stored_in_parent_trace_context_over_current_baggage() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn())
            .addSpanProcessor(new BaggageTaggingSpanProcessor(Collections.singletonList("foo")))
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
        io.opentelemetry.api.trace.Tracer tracer = tracerProvider.get("io.micrometer.micrometer-tracing");
        io.opentelemetry.api.trace.Span parentSpan = tracer.spanBuilder("parent").startSpan();
        Context storedContext = Context.root().with(parentSpan).with(Baggage.builder().put("foo", "stored").build());
        TraceContext parent = new OtelTraceContext(storedContext, parentSpan.getSpanContext(), parentSpan);

        try (Scope scope = Baggage.builder().put("foo", "current").build().makeCurrent()) {
            new OtelSpanBuilder(tracer).name("child").setParent(parent).start().end();
        }

        SpanData finishedSpan = exporter.getFinishedSpanItems().get(0);
        then(finishedSpan.getAttributes().get(AttributeKey.stringKey("foo"))).isEqualTo("stored");
    }

}
