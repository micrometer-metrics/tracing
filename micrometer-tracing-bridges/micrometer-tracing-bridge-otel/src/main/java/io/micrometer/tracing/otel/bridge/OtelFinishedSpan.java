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

import io.micrometer.common.lang.Nullable;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenTelemetry implementation of a {@link FinishedSpan}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelFinishedSpan implements FinishedSpan {

    private final MutableSpanData spanData;

    @Nullable
    private volatile String linkLocalIp;

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
    @Nullable
    public String getParentId() {
        return this.spanData.getParentSpanId();
    }

    @Override
    @Nullable
    public String getRemoteIp() {
        return getTags().get(SemanticAttributes.NET_SOCK_PEER_ADDR.getKey());
    }

    @Override
    public String getLocalIp() {
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

    @Nullable
    private String produceLinkLocalIp() {
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
        String port = getTags().get("net.peer.port");
        if (port == null) {
            return 0;
        }
        return Integer.parseInt(port);
    }

    @Override
    public FinishedSpan setRemotePort(int port) {
        this.spanData.tags.put(AttributeKey.longKey("net.peer.port"), String.valueOf(port));
        return this;
    }

    @Override
    public String getTraceId() {
        return this.spanData.getTraceId();
    }

    @Override
    @Nullable
    public Throwable getError() {
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
            .add(EventData.create(System.nanoTime(), "exception",
                    Attributes.of(AttributeKey.stringKey("exception.message"), error.toString())));
        return this;
    }

    @Override
    public Span.Kind getKind() {
        if (this.spanData.getKind() == SpanKind.INTERNAL) {
            return null;
        }
        return Span.Kind.valueOf(this.spanData.getKind().name());
    }

    @Override
    @Nullable
    public String getRemoteServiceName() {
        return this.spanData.getAttributes().get(AttributeKey.stringKey("peer.service"));
    }

    @Override
    public FinishedSpan setRemoteServiceName(String remoteServiceName) {
        this.spanData.tags.put(AttributeKey.stringKey("peer.service"), remoteServiceName);
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
            super(attributes.get(AttributeKey.stringKey("exception.message")));
            this.attributes = attributes;
        }

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static class MutableSpanData extends DelegatingSpanData {

        String name;

        long startEpochNanos;

        long endEpochNanos;

        final Map<AttributeKey, Object> tags = new HashMap<>();

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

    }

}
