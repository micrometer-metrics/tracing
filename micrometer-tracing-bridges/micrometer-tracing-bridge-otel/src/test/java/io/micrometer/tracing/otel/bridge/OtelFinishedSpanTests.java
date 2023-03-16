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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class OtelFinishedSpanTests {

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
    void should_calculate_instant_from_otel_timestamps() {
        long startMicros = System.nanoTime();
        long endMicros = System.nanoTime();

        FinishedSpan finishedSpan = OtelFinishedSpan.fromOtel(new CustomSpanData(startMicros, endMicros));

        then(finishedSpan.getStartTimestamp().toEpochMilli()).isEqualTo(TimeUnit.NANOSECONDS.toMillis(startMicros));
        then(finishedSpan.getEndTimestamp().toEpochMilli()).isEqualTo(TimeUnit.NANOSECONDS.toMillis(endMicros));
    }

    @Test
    void should_set_links() {
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

        Span.Builder builder = new OtelSpanBuilder(otelTracer.spanBuilder("foo"));
        Span span1 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span2 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span3 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());
        Span span4 = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());

        builder.addLink(span1.context()).addLink(span2.context(), tags()).start().end();

        SpanData finishedSpan = processor.spans().poll();
        OtelFinishedSpan otelFinishedSpan = new OtelFinishedSpan(finishedSpan);

        otelFinishedSpan.addLink(span3.context(), tags());
        otelFinishedSpan.addLinks(Collections.singletonMap(span4.context(), tags()));

        then(otelFinishedSpan.getLinks()).hasSize(4)
            .containsEntry(span1.context(), Collections.emptyMap())
            .containsEntry(span2.context(), tags())
            .containsEntry(span3.context(), tags())
            .containsEntry(span4.context(), tags());
    }

    private Map<String, String> tags() {
        Map<String, String> map = new HashMap<>();
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
