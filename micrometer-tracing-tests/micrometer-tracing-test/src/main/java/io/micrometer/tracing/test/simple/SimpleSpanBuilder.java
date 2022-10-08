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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A test implementation of a span builder.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpanBuilder implements Span.Builder {

    private List<String> events = new ArrayList<>();

    private Map<String, String> tags = new HashMap<>();

    private Throwable throwable;

    private String remoteServiceName;

    private Span.Kind spanKind;

    private String name;

    private String ip;

    private int port;

    private SimpleTracer simpleTracer;

    private long startTimestamp;

    private TimeUnit startTimestampUnit;

    /**
     * Creates a new instance of {@link SimpleSpanBuilder}.
     * @param simpleTracer simple tracer
     */
    public SimpleSpanBuilder(SimpleTracer simpleTracer) {
        this.simpleTracer = simpleTracer;
    }

    @Override
    public Span.Builder setParent(TraceContext context) {
        return this;
    }

    @Override
    public Span.Builder setNoParent() {
        return this;
    }

    @Override
    public Span.Builder name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Span.Builder event(String value) {
        this.events.add(value);
        return this;
    }

    @Override
    public Span.Builder tag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    @Override
    public Span.Builder error(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    @Override
    public Span.Builder kind(Span.Kind spanKind) {
        this.spanKind = spanKind;
        return this;
    }

    @Override
    public Span.Builder remoteServiceName(String remoteServiceName) {
        this.remoteServiceName = remoteServiceName;
        return this;
    }

    @Override
    public Span.Builder remoteIpAndPort(String ip, int port) {
        this.ip = ip;
        this.port = port;
        return this;
    }

    @Override
    public Span.Builder startTimestamp(long startTimestamp, TimeUnit unit) {
        this.startTimestamp = startTimestamp;
        this.startTimestampUnit = unit;
        return this;
    }

    @Override
    public Span start() {
        SimpleSpan span = new SimpleSpan();
        this.getTags().forEach(span::tag);
        this.getEvents().forEach(span::event);
        span.remoteServiceName(this.getRemoteServiceName());
        span.error(this.getThrowable());
        span.setSpanKind(this.getSpanKind());
        span.name(this.getName());
        span.remoteIpAndPort(this.getIp(), this.getPort());
        span.start();
        if (this.startTimestampUnit != null) {
            span.setStartMillis(this.startTimestampUnit.toMillis(this.startTimestamp));
        }
        this.simpleTracer.getSpans().add(span);
        return span;
    }

    /**
     * List of events.
     * @return events
     */
    public List<String> getEvents() {
        return events;
    }

    /**
     * Map of tags.
     * @return tags
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Throwable corresponding to the span.
     * @return throwable
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Remote service name of the span.
     * @return service name
     */
    public String getRemoteServiceName() {
        return remoteServiceName;
    }

    /**
     * Span kind.
     * @return span kind
     */
    public Span.Kind getSpanKind() {
        return spanKind;
    }

    /**
     * Span name.
     * @return span name
     */
    public String getName() {
        return name;
    }

    /**
     * Remote service ip.
     * @return ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * Remote service port.
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * Simple tracer.
     * @return tracer
     */
    public SimpleTracer getSimpleTracer() {
        return simpleTracer;
    }

}
