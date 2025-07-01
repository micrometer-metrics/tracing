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
package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A test implementation of a span.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpan implements Span, FinishedSpan {

    private final Map<String, String> tags = new ConcurrentHashMap<>();

    private volatile boolean abandoned;

    private volatile long startMillis;

    private volatile long endMillis;

    private volatile @Nullable Throwable throwable;

    private volatile @Nullable String remoteServiceName;

    private volatile @Nullable String localServiceName;

    private volatile Span.@Nullable Kind spanKind;

    private final Queue<Event> events = new ConcurrentLinkedQueue<>();

    private volatile @Nullable String name;

    private volatile @Nullable String ip;

    private volatile int port;

    private volatile boolean noop;

    private final Clock clock = Clock.SYSTEM;

    private final SimpleTraceContext context = new SimpleTraceContext();

    private final List<Link> links = new ArrayList<>();

    /**
     * Creates a new instance of {@link SimpleSpan}.
     */
    public SimpleSpan() {
        SimpleTracer.bindSpanToTraceContext(context(), this);
    }

    @Override
    public boolean isNoop() {
        return this.noop;
    }

    @Override
    public SimpleTraceContext context() {
        return this.context;
    }

    @Override
    public SimpleSpan start() {
        this.startMillis = this.clock.wallTime();
        return this;
    }

    @Override
    public SimpleSpan name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public SimpleSpan event(String value) {
        this.events.add(new Event(MILLISECONDS.toMicros(this.clock.wallTime()), value));
        return this;
    }

    @Override
    public Span event(String value, long time, TimeUnit timeUnit) {
        this.events.add(new Event(timeUnit.toMicros(time), value));
        return this;
    }

    @Override
    public SimpleSpan tag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    @Override
    public SimpleSpan error(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    @Override
    public SimpleSpan remoteIpAndPort(String ip, int port) {
        this.ip = ip;
        this.port = port;
        return this;
    }

    @Override
    public void end() {
        this.endMillis = this.clock.wallTime();
    }

    @Override
    public void end(long time, TimeUnit timeUnit) {
        this.endMillis = timeUnit.toMicros(time);
    }

    @Override
    public void abandon() {
        this.abandoned = true;
    }

    @Override
    public SimpleSpan remoteServiceName(String remoteServiceName) {
        this.remoteServiceName = remoteServiceName;
        return this;
    }

    /**
     * Map of tags.
     * @return tags
     */
    @Override
    public Map<String, String> getTags() {
        return this.tags;
    }

    @Override
    public SimpleSpan setEvents(Collection<Map.Entry<Long, String>> events) {
        return this;
    }

    @Override
    public Collection<Map.Entry<Long, String>> getEvents() {
        return this.events.stream().map(Event::toEntry).collect(Collectors.toList());
    }

    void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    /**
     * Remote service name of the span.
     * @return remote service name
     */
    @Override
    public @Nullable String getRemoteServiceName() {
        return this.remoteServiceName;
    }

    @Override
    public SimpleSpan setRemoteServiceName(String remoteServiceName) {
        this.remoteServiceName = remoteServiceName;
        return this;
    }

    @Override
    public @Nullable String getLocalServiceName() {
        return this.localServiceName;
    }

    @Override
    public SimpleSpan setLocalServiceName(String localServiceName) {
        this.localServiceName = localServiceName;
        return this;
    }

    @Override
    public List<Link> getLinks() {
        return this.links;
    }

    @Override
    public SimpleSpan addLinks(List<Link> links) {
        this.links.addAll(links);
        return this;
    }

    @Override
    public SimpleSpan addLink(Link link) {
        this.links.add(link);
        return this;
    }

    /**
     * Span kind.
     */
    void setSpanKind(Kind kind) {
        this.spanKind = kind;
    }

    @Override
    public String getSpanId() {
        return this.context.spanId();
    }

    @Override
    public String getParentId() {
        return this.context.parentId();
    }

    @Override
    public @Nullable String getRemoteIp() {
        return this.ip;
    }

    @Override
    public @Nullable String getLocalIp() {
        return this.ip;
    }

    @Override
    public FinishedSpan setLocalIp(String ip) {
        return this;
    }

    @Override
    public int getRemotePort() {
        return this.port;
    }

    @Override
    public FinishedSpan setRemotePort(int port) {
        return this;
    }

    @Override
    public String getTraceId() {
        return this.context.traceId();
    }

    @Override
    public @Nullable Throwable getError() {
        return this.throwable;
    }

    @Override
    public FinishedSpan setError(Throwable error) {
        return this;
    }

    @Override
    public @Nullable Kind getKind() {
        return this.spanKind;
    }

    @Override
    public FinishedSpan setName(String name) {
        return this;
    }

    /**
     * Span name.
     * @return span name
     */
    @Override
    // TODO decide what to do about name nullability
    @SuppressWarnings("NullAway")
    public String getName() {
        return this.name;
    }

    @Override
    public Instant getStartTimestamp() {
        return Instant.ofEpochMilli(this.startMillis);
    }

    @Override
    public Instant getEndTimestamp() {
        return Instant.ofEpochMilli(this.endMillis);
    }

    @Override
    public FinishedSpan setTags(Map<String, String> tags) {
        return this;
    }

    /**
     * Clock used for time measurements.
     * @return clock
     */
    public Clock getClock() {
        return this.clock;
    }

    @Override
    public String toString() {
        return "SimpleSpan{" + "tags=" + tags + ", abandoned=" + abandoned + ", startMillis=" + startMillis
                + ", endMillis=" + endMillis + ", throwable=" + throwable + ", remoteServiceName='" + remoteServiceName
                + '\'' + ", spanKind=" + spanKind + ", events=" + events + ", name='" + name + '\'' + ", ip='" + ip
                + '\'' + ", port=" + port + ", noop=" + noop + ", clock=" + clock + ", context=" + context + '}';
    }

    static class Event {

        final Long timestamp;

        final String eventName;

        Event(Long timestamp, String eventName) {
            this.timestamp = timestamp;
            this.eventName = eventName;
        }

        Map.Entry<Long, String> toEntry() {
            return new AbstractMap.SimpleEntry<>(this.timestamp, this.eventName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Event event = (Event) o;
            return Objects.equals(this.timestamp, event.timestamp) && Objects.equals(this.eventName, event.eventName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.timestamp, this.eventName);
        }

    }

}
