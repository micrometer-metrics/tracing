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

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static org.assertj.core.api.BDDAssertions.then;

class OtelFinishedSpanTests {

    private static final String SCHEMA_URL = "https://opentelemetry.io/schemas/1.24.0";

    @Test
    void should_set_name() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getName()).isEqualTo("foo");

        span.setName("bar");

        then(span.getName()).isEqualTo("bar");
    }

    @Test
    void should_set_tags() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getTags()).isEmpty();

        Map<String, String> map = new HashMap<>();
        map.put("foo", "bar");
        span.setTags(map);

        then(span.getTags().get("foo")).isEqualTo("bar");
    }

    @Test
    void should_set_typed_tags() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getTypedTags()).isEmpty();

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 2L);
        map.put("bar", Arrays.asList("a", "b", "c"));
        map.put("baz", Arrays.asList(1, 2, 3));
        map.put("qux", Collections.emptyList());
        span.setTypedTags(map);

        then(span.getTypedTags()).hasSize(4)
            .containsEntry("foo", 2L)
            .containsEntry("bar", Arrays.asList("a", "b", "c"))
            .containsEntry("baz", Arrays.asList(1, 2, 3))
            .containsEntry("qux", Collections.emptyList());
    }

    @Test
    void should_set_events() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getEvents()).isEmpty();

        List<Map.Entry<Long, String>> eventData = new ArrayList<>();
        eventData.add(new AbstractMap.SimpleEntry<>(System.nanoTime(), "foo"));
        span.setEvents(eventData);

        then(span.getEvents()).hasSize(1);
        then(span.getEvents().iterator().next().getValue()).isEqualTo("foo");
    }

    @Test
    void should_set_local_ip() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getLocalIp()).isNotEmpty();

        span.setLocalIp("bar");

        then(span.getLocalIp()).isEqualTo("bar");
    }

    @Test
    void should_set_remote_port() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getRemotePort()).isZero();

        span.setRemotePort(80);

        then(span.getRemotePort()).isEqualTo(80);
    }

    @Test
    void should_set_error() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getError()).isNull();

        span.setError(new RuntimeException("foo"));

        then(span.getError()).hasMessageContaining("foo");
    }

    @Test
    void should_set_remote_service_name() {
        FinishedSpan span = OtelFinishedSpan.fromOtel(new CustomSpanData());
        then(span.getRemoteServiceName()).isNull();

        span.setRemoteServiceName("bar");

        then(span.getRemoteServiceName()).isEqualTo("bar");
    }

    @Test
    void should_keep_resource_instance_and_schema_url() {
        Resource resource = Resource.create(Attributes.of(SERVICE_NAME, "foo", stringKey("bar"), "baz"), SCHEMA_URL);
        SpanData spanData = OtelFinishedSpan.toOtel(OtelFinishedSpan.fromOtel(spanDataWithResource(resource)));

        then(spanData.getResource()).isSameAs(resource);
        then(spanData.getResource().getSchemaUrl()).isEqualTo(SCHEMA_URL);
    }

    @Test
    void should_set_local_service_name() {
        Resource resource = Resource.create(Attributes.of(SERVICE_NAME, "foo", stringKey("bar"), "baz"), SCHEMA_URL);
        FinishedSpan span = OtelFinishedSpan.fromOtel(spanDataWithResource(resource));
        then(span.getLocalServiceName()).isEqualTo("foo");

        span.setLocalServiceName("qux");

        then(span.getLocalServiceName()).isEqualTo("qux");
        Resource changed = OtelFinishedSpan.toOtel(span).getResource();
        then(changed.getAttributes().asMap()).hasSize(2).containsEntry(stringKey("bar"), "baz");
        then(changed.getSchemaUrl()).isEqualTo(SCHEMA_URL);
        then(changed).isSameAs(OtelFinishedSpan.toOtel(span).getResource());
    }

    private static SpanData spanDataWithResource(Resource resource) {
        return TestSpanData.builder()
            .setName("foo")
            .setKind(SpanKind.INTERNAL)
            .setStartEpochNanos(1)
            .setEndEpochNanos(2)
            .setHasEnded(true)
            .setStatus(StatusData.unset())
            .setResource(resource)
            .build();
    }

    @Test
    void should_calculate_instant_from_otel_timestamps() {
        long startMicros = System.nanoTime();
        long endMicros = System.nanoTime();

        FinishedSpan finishedSpan = OtelFinishedSpan.fromOtel(new CustomSpanData(startMicros, endMicros));

        then(finishedSpan.getStartTimestamp().toEpochMilli()).isEqualTo(TimeUnit.NANOSECONDS.toMillis(startMicros));
        then(finishedSpan.getEndTimestamp().toEpochMilli()).isEqualTo(TimeUnit.NANOSECONDS.toMillis(endMicros));
        then(finishedSpan.getDuration().toNanos()).isEqualTo(endMicros - startMicros);
    }

    @Test
    void should_set_links() {
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

        Span.Builder builder = new OtelSpanBuilder(otelTracer).name("foo");
        Span span1 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span2 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span3 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span4 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());

        builder.addLink(new Link(span1.context())).addLink(new Link(span2, tags())).start().end();

        SpanData traceFinishedSpan = processor.getFinishedSpanItems().get(0);
        OtelFinishedSpan finishedSpan = new OtelFinishedSpan(traceFinishedSpan);

        finishedSpan.addLink(new Link(span3, tags()));
        finishedSpan.addLinks(Collections.singletonList(new Link(span4.context(), tags())));

        then(finishedSpan.getLinks()).hasSize(4)
            .contains(new Link(span1.context(), Collections.emptyMap()), new Link(span2.context(), tags()),
                    new Link(span3.context(), tags()), new Link(span4.context(), tags()));
    }

    private Map<String, Object> tags() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

    static class CustomSpanData implements SpanData {

        private final long startNanos;

        private final long endNanos;

        CustomSpanData(long startNanos, long endNanos) {
            this.startNanos = startNanos;
            this.endNanos = endNanos;
        }

        CustomSpanData() {
            this(System.nanoTime(), System.nanoTime());
        }

        @Override
        public String getName() {
            return "foo";
        }

        @Override
        public SpanKind getKind() {
            return SpanKind.PRODUCER;
        }

        @Override
        public SpanContext getSpanContext() {
            return SpanContext.getInvalid();
        }

        @Override
        public SpanContext getParentSpanContext() {
            return SpanContext.getInvalid();
        }

        @Override
        public StatusData getStatus() {
            return StatusData.ok();
        }

        @Override
        public long getStartEpochNanos() {
            return this.startNanos;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.empty();
        }

        @Override
        public List<EventData> getEvents() {
            return Collections.emptyList();
        }

        @Override
        public List<LinkData> getLinks() {
            return Collections.emptyList();
        }

        @Override
        public long getEndEpochNanos() {
            return this.endNanos;
        }

        @Override
        public boolean hasEnded() {
            return true;
        }

        @Override
        public int getTotalRecordedEvents() {
            return 10;
        }

        @Override
        public int getTotalRecordedLinks() {
            return 20;
        }

        @Override
        public int getTotalAttributeCount() {
            return 30;
        }

        @Override
        public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
            return InstrumentationLibraryInfo.empty();
        }

        @Override
        public InstrumentationScopeInfo getInstrumentationScopeInfo() {
            return InstrumentationScopeInfo.empty();
        }

        @Override
        public Resource getResource() {
            return Resource.empty();
        }

    }

}
