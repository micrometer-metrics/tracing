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
package io.micrometer.tracing.brave.bridge;

import brave.Tracer;
import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Brave implementation of a {@link Span.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class BraveSpanBuilder implements Span.Builder {

    private final Tracer tracer;

    brave.@Nullable Span delegate;

    @Nullable TraceContextOrSamplingFlags parentContext;

    private long startTimestamp;

    private @Nullable String name;

    private List<String> events = new ArrayList<>();

    private Map<String, String> tags = new HashMap<>();

    private @Nullable Throwable error;

    private brave.Span.@Nullable Kind kind;

    private @Nullable String remoteServiceName;

    private @Nullable String ip;

    private int port;

    BraveSpanBuilder(Tracer tracer) {
        this.tracer = tracer;
    }

    BraveSpanBuilder(Tracer tracer, TraceContextOrSamplingFlags parentContext) {
        this.tracer = tracer;
        this.parentContext = parentContext;
    }

    static Span.Builder toBuilder(Tracer tracer, TraceContextOrSamplingFlags context) {
        return new BraveSpanBuilder(tracer, context);
    }

    private brave.Span span() {
        brave.Span span;
        if (this.parentContext != null) {
            span = this.tracer.nextSpan(this.parentContext);
        }
        else {
            span = this.tracer.nextSpan();
        }
        span.name(this.name);
        this.events.forEach(span::annotate);
        this.tags.forEach(span::tag);
        span.error(this.error);
        span.kind(this.kind);
        span.remoteServiceName(this.remoteServiceName);
        span.remoteIpAndPort(this.ip, this.port);
        this.delegate = span;
        return span;
    }

    @Override
    public Span.Builder setParent(TraceContext context) {
        this.parentContext = TraceContextOrSamplingFlags.create(BraveTraceContext.toBrave(context));
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
        this.error = throwable;
        return this;
    }

    @Override
    public Span.Builder kind(Span.Kind kind) {
        this.kind = kind != null ? brave.Span.Kind.valueOf(kind.toString()) : null;
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
        this.startTimestamp = unit.toMicros(startTimestamp);
        return this;
    }

    @Override
    public Span.Builder addLink(Link link) {
        brave.propagation.TraceContext braveContext = BraveTraceContext.toBrave(link.getTraceContext());
        long nextId = LinkUtils.nextIndex(this.tags);
        if (braveContext != null) {
            this.tags.put(LinkUtils.spanIdKey(nextId), braveContext.spanIdString());
            this.tags.put(LinkUtils.traceIdKey(nextId), braveContext.traceIdString());
        }
        link.getTags().forEach((key, value) -> this.tags.put(LinkUtils.tagKey(nextId, key), String.valueOf(value)));
        return this;
    }

    @Override
    public Span start() {
        brave.Span span = span();
        if (this.startTimestamp > 0) {
            span.start(this.startTimestamp);
        }
        else {
            span.start();
        }
        return BraveSpan.fromBrave(span);
    }

    @Override
    public String toString() {
        return "{" + " delegate='" + this.delegate + "'" + ", parentContext='" + this.parentContext + "'"
                + ", startTimestamp='" + this.startTimestamp + "'" + "}";
    }

}
