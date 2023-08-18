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

import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry implementation of a {@link Span.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class OtelSpanBuilder implements Span.Builder {

    static final String REMOTE_SERVICE_NAME_KEY = "peer.service";

    private final Tracer tracer;

    private final List<String> annotations = new LinkedList<>();

    private final AttributesBuilder attributes = Attributes.builder();

    private String name;

    private Throwable error;

    private TraceContext parentTraceContext;

    private boolean noParent;

    private SpanKind spanKind = SpanKind.INTERNAL;

    private long startTimestamp;

    private TimeUnit startTimestampUnit;

    OtelSpanBuilder(Tracer tracer) {
        this.tracer = tracer;
    }

    static Span.Builder fromOtel(Tracer tracer) {
        return new OtelSpanBuilder(tracer);
    }

    @Override
    public Span.Builder setParent(TraceContext context) {
        this.parentTraceContext = context;
        return this;
    }

    @Override
    public Span.Builder setNoParent() {
        this.noParent = true;
        return this;
    }

    @Override
    public Span.Builder name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Span.Builder event(String value) {
        this.annotations.add(value);
        return this;
    }

    @Override
    public Span.Builder tag(String key, String value) {
        this.attributes.put(key, value);
        return this;
    }

    @Override
    public Span.Builder error(Throwable throwable) {
        this.error = throwable;
        return this;
    }

    @Override
    public Span.Builder kind(Span.Kind spanKind) {
        if (spanKind == null) {
            this.spanKind = SpanKind.INTERNAL;
            return this;
        }
        SpanKind kind = SpanKind.INTERNAL;
        switch (spanKind) {
            case CLIENT:
                kind = SpanKind.CLIENT;
                break;
            case SERVER:
                kind = SpanKind.SERVER;
                break;
            case PRODUCER:
                kind = SpanKind.PRODUCER;
                break;
            case CONSUMER:
                kind = SpanKind.CONSUMER;
                break;
        }
        this.spanKind = kind;
        return this;
    }

    @Override
    public Span.Builder remoteServiceName(String remoteServiceName) {
        this.attributes.put(REMOTE_SERVICE_NAME_KEY, remoteServiceName);
        return this;
    }

    @Override
    public Span.Builder remoteIpAndPort(String ip, int port) {
        this.attributes.put(SemanticAttributes.NET_SOCK_PEER_ADDR.getKey(), ip);
        this.attributes.put(SemanticAttributes.NET_PEER_PORT.getKey(), port);
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
        SpanBuilder spanBuilder = this.tracer.spanBuilder(StringUtils.isNotEmpty(this.name) ? this.name : "");
        if (this.parentTraceContext != null) {
            spanBuilder.setParent(OtelTraceContext.toOtelContext(this.parentTraceContext));
        }
        if (this.noParent) {
            spanBuilder.setNoParent();
        }
        spanBuilder.setAllAttributes(this.attributes.build());
        spanBuilder.setSpanKind(this.spanKind);
        if (this.startTimestampUnit != null) {
            spanBuilder.setStartTimestamp(this.startTimestamp, this.startTimestampUnit);
        }
        io.opentelemetry.api.trace.Span span = spanBuilder.startSpan();
        if (this.error != null) {
            span.recordException(this.error);
        }
        this.annotations.forEach(span::addEvent);
        Span otelSpan = OtelSpan.fromOtel(span);
        if (this.parentTraceContext != null) {
            return OtelSpan.fromOtel(
                    new SpanFromSpanContext(span, span.getSpanContext(), (OtelTraceContext) this.parentTraceContext));
        }
        return otelSpan;
    }

}
