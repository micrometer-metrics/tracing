/**
 * Copyright 2023 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static io.micrometer.tracing.otel.bridge.OtelSpan.PEER_SERVICE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;

/**
 * OpenTelemetry implementation of a {@link FinishedSpan}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelFinishedSpan implements FinishedSpan {

    private final MutableSpanData spanData;

    private volatile @Nullable String linkLocalIp;

    OtelFinishedSpan(SpanData spanData) {
        this.spanData = new MutableSpanData(spanData);
    }

    /**
     * Converts from OTel to Tracing.
     * @param span OTel version
     * @return Tracing version
     */
    public static FinishedSpan fromOtel(SpanData span) {
        return new OtelFinishedSpan(span);
    }

    /**
     * Converts from Tracing to OTel.
     * @param span Tracing version
     * @return OTel version
     */
    public static SpanData toOtel(FinishedSpan span) {
        return ((OtelFinishedSpan) span).spanData;
    }

    @Override
    public FinishedSpan setName(String name) {
        this.spanData.name = name;
        return this;
    }

    @Override
    public String getName() {
        return this.spanData.getName();
    }

    @Override
    public Instant getStartTimestamp() {
        return Instant.ofEpochSecond(0L, this.spanData.getStartEpochNanos());
    }

    @Override
    public Instant getEndTimestamp() {
        return Instant.ofEpochSecond(0L, this.spanData.getEndEpochNanos());
    }

    @Override
    public FinishedSpan setTags(Map<String, String> tags) {
        this.spanData.tags.clear();
        this.spanData.tags.putAll(tags.entrySet()
            .stream()
            .collect(Collectors.toMap(e -> AttributeKey.stringKey(e.getKey()), Map.Entry::getValue)));
        return this;
    }

    @Override
    public Map<String, String> getTags() {
        return this.spanData.tags.entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().getKey(), entry -> String.valueOf(entry.getValue())));
    }

    @Override
    public FinishedSpan setTypedTags(Map<String, Object> tags) {
        this.spanData.tags.clear();
        this.spanData.tags.putAll(tags.entrySet().stream().collect(Collectors.toMap(e -> {
            Object value = e.getValue();
            return getAttributeKey(e.getKey(), value);
        }, Map.Entry::getValue)));
        return this;
    }

    @SuppressWarnings("raw")
    private static AttributeKey getAttributeKey(String key, Object value) {
        if (value instanceof Double) {
            return AttributeKey.doubleKey(key);
        }
        else if (value instanceof Long) {
            return AttributeKey.longKey(key);
        }
        else if (value instanceof Boolean) {
            return AttributeKey.booleanKey(key);
        }
        else if (value instanceof List) {
            List<?> valueAsList = (List<?>) value;
            if (valueAsList.isEmpty()) {
                return AttributeKey.stringArrayKey(key);
            }
            Object firstValue = valueAsList.get(0);
            if (firstValue instanceof Double) {
                return AttributeKey.doubleArrayKey(key);
            }
            else if (firstValue instanceof Long) {
                return AttributeKey.longArrayKey(key);
            }
            else if (firstValue instanceof Boolean) {
                return AttributeKey.doubleArrayKey(key);
            }
            return AttributeKey.stringArrayKey(key);
        }
        return AttributeKey.stringKey(key);
    }

    @Override
    public Map<String, Object> getTypedTags() {
        return this.spanData.tags.entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().getKey(), Map.Entry::getValue));
    }

    @Override
    public FinishedSpan setEvents(Collection<Map.Entry<Long, String>> events) {
        this.spanData.events.clear();
        this.spanData.events.addAll(events.stream()
            .map(e -> EventData.create(e.getKey(), e.getValue(), Attributes.empty()))
            .collect(Collectors.toList()));
        return this;
    }

    @Override
    public Collection<Map.Entry<Long, String>> getEvents() {
        return this.spanData.getEvents()
            .stream()
            .map(e -> new AbstractMap.SimpleEntry<>(e.getEpochNanos(), e.getName()))
            .collect(Collectors.toList());
    }

    @Override
    public String getSpanId() {
        return this.spanData.getSpanId();
    }

    @Override
    public @Nullable String getParentId() {
        return this.spanData.getParentSpanId();
    }

    @Override
    public @Nullable String getRemoteIp() {
        return getTags().get(NETWORK_PEER_ADDRESS.getKey());
    }

    @Override
    public @Nullable String getLocalIp() {
        // taken from Brave
        // uses synchronized variant of double-checked locking as getting the endpoint can
        // be expensive
        if (this.linkLocalIp != null) {
            return this.linkLocalIp;
        }
        synchronized (this) {
            if (this.linkLocalIp == null) {
                this.linkLocalIp = produceLinkLocalIp();
            }
        }
        return this.linkLocalIp;
    }

    @Override
    public FinishedSpan setLocalIp(String ip) {
        this.linkLocalIp = ip;
        return this;
    }

    private @Nullable String produceLinkLocalIp() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        }
        catch (Exception e) {
        }
        return null;
    }

    @Override
    public int getRemotePort() {
        String port = getTags().get(NETWORK_PEER_PORT.getKey());
        if (port == null) {
            return 0;
        }
        return Integer.parseInt(port);
    }

    @Override
    public FinishedSpan setRemotePort(int port) {
        this.spanData.tags.put(NETWORK_PEER_PORT, String.valueOf(port));
        return this;
    }

    @Override
    public String getTraceId() {
        return this.spanData.getTraceId();
    }

    @Override
    public @Nullable Throwable getError() {
        Attributes attributes = this.spanData.getEvents()
            .stream()
            .filter(e -> e.getName().equals("exception"))
            .findFirst()
            .map(EventData::getAttributes)
            .orElse(null);
        if (attributes != null) {
            return new AssertingThrowable(attributes);
        }
        return null;
    }

    @Override
    public FinishedSpan setError(Throwable error) {
        this.spanData.getEvents()
            .add(EventData.create(System.nanoTime(), "exception", Attributes.of(EXCEPTION_MESSAGE, error.toString())));
        return this;
    }

    @Override
    public Span.@Nullable Kind getKind() {
        if (this.spanData.getKind() == SpanKind.INTERNAL) {
            return null;
        }
        return Span.Kind.valueOf(this.spanData.getKind().name());
    }

    @Override
    public @Nullable String getRemoteServiceName() {
        return this.spanData.getAttributes().get(PEER_SERVICE);
    }

    @Override
    public FinishedSpan setRemoteServiceName(String remoteServiceName) {
        this.spanData.tags.put(PEER_SERVICE, remoteServiceName);
        return this;
    }

    @Override
    public @Nullable String getLocalServiceName() {
        return this.spanData.getResource().getAttribute(SERVICE_NAME);
    }

    @Override
    public FinishedSpan setLocalServiceName(String localServiceName) {
        this.spanData.resources.put(SERVICE_NAME, localServiceName);
        return this;
    }

    @Override
    public List<Link> getLinks() {
        return this.spanData.getLinks()
            .stream()
            .map(linkData -> new Link(OtelTraceContext.fromOtel(linkData.getSpanContext()),
                    linkData.getAttributes()
                        .asMap()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(e -> e.getKey().getKey(), e -> String.valueOf(e.getValue())))))
            .collect(Collectors.toList());
    }

    @Override
    public FinishedSpan addLinks(List<Link> links) {
        links.forEach(this::addLink);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FinishedSpan addLink(Link link) {
        TraceContext traceContext = link.getTraceContext();
        Map<String, Object> tags = link.getTags();
        AttributesBuilder builder = Attributes.builder();
        tags.forEach((s, o) -> builder.put(getAttributeKey(s, o), o));
        this.spanData.getLinks()
            .add(LinkData.create(OtelTraceContext.toOtelSpanContext(traceContext), builder.build()));
        return this;
    }

    @Override
    public String toString() {
        return "SpanDataToReportedSpan{" + "spanData=" + spanData + '}';
    }

    /**
     * {@link Throwable} with attributes.
     */
    public static class AssertingThrowable extends Throwable {

        /**
         * Attributes set on the span.
         */
        public final Attributes attributes;

        AssertingThrowable(Attributes attributes) {
            super(attributes.get(EXCEPTION_MESSAGE));
            this.attributes = attributes;
        }

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static class MutableSpanData extends DelegatingSpanData {

        String name;

        long startEpochNanos;

        long endEpochNanos;

        final Map<AttributeKey, Object> tags = new HashMap<>();

        final Map<AttributeKey, Object> resources = new HashMap<>();

        final List<EventData> events = new ArrayList<>();

        final List<LinkData> links = new ArrayList<>();

        MutableSpanData(SpanData delegate) {
            super(delegate);
            this.name = delegate.getName();
            this.startEpochNanos = delegate.getStartEpochNanos();
            this.endEpochNanos = delegate.getEndEpochNanos();
            delegate.getAttributes().forEach(this.tags::put);
            this.events.addAll(delegate.getEvents());
            this.links.addAll(delegate.getLinks());
            this.resources.putAll(delegate.getResource().getAttributes().asMap());
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public long getStartEpochNanos() {
            return this.startEpochNanos;
        }

        @Override
        public Attributes getAttributes() {
            AttributesBuilder builder = Attributes.builder();
            for (Map.Entry<AttributeKey, Object> entry : this.tags.entrySet()) {
                builder = builder.put(entry.getKey(), entry.getValue());
            }
            return builder.build();
        }

        @Override
        public List<EventData> getEvents() {
            return this.events;
        }

        @Override
        public long getEndEpochNanos() {
            return this.endEpochNanos;
        }

        @Override
        public int getTotalRecordedEvents() {
            return getEvents().size();
        }

        @Override
        public int getTotalAttributeCount() {
            return getAttributes().size();
        }

        @Override
        public List<LinkData> getLinks() {
            return this.links;
        }

        @Override
        public Resource getResource() {
            AttributesBuilder builder = Attributes.builder();
            for (Map.Entry<AttributeKey, Object> entry : this.resources.entrySet()) {
                builder = builder.put(entry.getKey(), entry.getValue());
            }
            return Resource.create(builder.build());
        }

    }

}
