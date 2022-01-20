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

package io.micrometer.tracing.test.simple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Clock;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of a span.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpan implements Span {

    private Map<String, String> tags = new HashMap<>();

    private boolean started;

    private boolean ended;

    private boolean abandoned;

    private long startMicros;

    private long endMicros;

    private Throwable throwable;

    private String remoteServiceName;

    private Span.Kind spanKind;

    private List<Event> events = new ArrayList<>();

    private String name;

    private String ip;

    private int port;

    private boolean noOp;

    private Clock clock = Clock.SYSTEM;

    @Override
    public boolean isNoop() {
        return this.isNoOp();
    }

    @Override
    public TraceContext context() {
        return new SimpleTraceContext();
    }

    @Override
    public SimpleSpan start() {
        this.started = true;
        return this;
    }

    @Override
    public SimpleSpan name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public SimpleSpan event(String value) {
        this.events.add(new Event(value, this.getClock().wallTime()));
        return this;
    }

    @Override
    public Span event(String value, long time, TimeUnit timeUnit) {
        this.events.add(new Event(value, timeUnit.toMicros(time)));
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
    public Span remoteIpAndPort(String ip, int port) {
        this.ip = ip;
        this.port = port;
        return this;
    }

    @Override
    public void end() {
        this.ended = true;
    }

    @Override
    public void end(long time, TimeUnit timeUnit) {
        end();
        this.endMicros = timeUnit.toMicros(time);
    }

    @Override
    public void abandon() {
        this.abandoned = true;
    }

    @Override
    public Span remoteServiceName(String remoteServiceName) {
        this.remoteServiceName = remoteServiceName;
        return this;
    }

    /**
     * @return list of event names
     */
    public List<String> getEventNames() {
        return this.getEvents().stream().map(event -> event.name).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "SimpleSpan{" + "tags=" + this.getTags() + ", started=" + this.isStarted() + ", ended=" + this.isEnded()
                + ", abandoned=" + this.isAbandoned() + ", startMicros=" + this.getStartMicros() + ", endMicros="
                + this.getEndMicros() + ", throwable=" + this.getThrowable() + ", remoteServiceName='" + this.getRemoteServiceName()
                + '\'' + ", spanKind=" + this.getSpanKind() + ", events=" + this.getEvents() + ", name='" + this.getName() + '\''
                + ", ip='" + this.getIp() + '\'' + ", port=" + this.getPort() + ", noOp=" + this.isNoOp() + '}';
    }

    /**
     * Map of tags.
     *
     * @return tags
     */
    public Map<String, String> getTags() {
        return this.tags;
    }

    /**
     * Span started.
     *
     * @return {@code true} when span started
     */
    public boolean isStarted() {
        return this.started;
    }

    /**
     * Span ended.
     *
     * @return {@code true} when span ended
     */
    public boolean isEnded() {
        return this.ended;
    }

    /**
     * Span abandoned.
     *
     * @return {@code true} when span abandoned
     */
    public boolean isAbandoned() {
        return this.abandoned;
    }

    /**
     * Span start timestamp in micros.
     *
     * @return start time in micro seconds
     */
    public long getStartMicros() {
        return this.startMicros;
    }

    void setStartMicros(long startMicros) {
        this.startMicros = startMicros;
    }

    /**
     * Span end timestamp in micros.
     * @return end time in micros
     */
    public long getEndMicros() {
        return this.endMicros;
    }

    /**
     * Throwable corresponding to the span.
     * @return throwable
     */
    public Throwable getThrowable() {
        return this.throwable;
    }

    /**
     * Remote service name of the span.
     * @return remote service name
     */
    public String getRemoteServiceName() {
        return this.remoteServiceName;
    }

    /**
     * Span kind.
     * @return span kind
     */
    public Kind getSpanKind() {
        return this.spanKind;
    }

    /**
     * Span kind.
     */
    void setSpanKind(Kind kind) {
        this.spanKind = kind;
    }

    /**
     * List of events.
     *
     * @return events
     */
    public List<Event> getEvents() {
        return this.events;
    }

    /**
     * Span name.
     *
     * @return span name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Remote service ip.
     *
     * @return ip
     */
    public String getIp() {
        return this.ip;
    }

    /**
     * Remote service port.
     *
     * @return port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Is span no op?
     *
     * @return {@code true} when span no op
     */
    public boolean isNoOp() {
        return this.noOp;
    }

    /**
     * Clock used for time measurements.
     *
     * @return clock
     */
    public Clock getClock() {
        return this.clock;
    }

    /**
     * Event annotated on a span.
     */
    public static class Event {

        /**
         * Name of the event.
         */
        private final String name;

        /**
         * Timestamp of the event.
         */
        private final long timestamp;

        /**
         * Creates a new instance of an event.
         *
         * @param name event name
         * @param timestamp event timestamp
         */
        public Event(String name, long timestamp) {
            this.name = name;
            this.timestamp = timestamp;
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
            return this.timestamp == event.timestamp && Objects.equals(this.name, event.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name, this.timestamp);
        }

        /**
         * Event name.
         *
         * @return name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Event timestamp.
         *
         * @return timestamp
         */
        public long getTimestamp() {
            return this.timestamp;
        }
    }

}
