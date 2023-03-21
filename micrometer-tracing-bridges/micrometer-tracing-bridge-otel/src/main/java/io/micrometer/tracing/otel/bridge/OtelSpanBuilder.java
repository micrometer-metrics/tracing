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
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry implementation of a {@link Span.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class OtelSpanBuilder implements Span.Builder {

    static final String REMOTE_SERVICE_NAME_KEY = "peer.service";

    private final io.opentelemetry.api.trace.SpanBuilder delegate;

    private final List<String> annotations = new LinkedList<>();

    private String name;

    private Throwable error;

    private TraceContext parentTraceContext;

    OtelSpanBuilder(io.opentelemetry.api.trace.SpanBuilder delegate) {
        this.delegate = delegate;
    }

    static Span.Builder fromOtel(io.opentelemetry.api.trace.SpanBuilder builder) {
        return new OtelSpanBuilder(builder);
    }

    @Override
    public Span.Builder setParent(TraceContext context) {
        this.delegate.setParent(OtelTraceContext.toOtelContext(context));
        this.parentTraceContext = context;
        return this;
    }

    @Override
    public Span.Builder setNoParent() {
        this.delegate.setNoParent();
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
        this.delegate.setAttribute(key, value);
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
            this.delegate.setSpanKind(SpanKind.INTERNAL);
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
        this.delegate.setSpanKind(kind);
        return this;
    }

    @Override
    public Span.Builder remoteServiceName(String remoteServiceName) {
        this.delegate.setAttribute(REMOTE_SERVICE_NAME_KEY, remoteServiceName);
        return this;
    }

    @Override
    public Span.Builder remoteIpAndPort(String ip, int port) {
        this.delegate.setAttribute(SemanticAttributes.NET_SOCK_PEER_ADDR, ip);
        this.delegate.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) port);
        return this;
    }

    @Override
    public Span.Builder startTimestamp(long startTimestamp, TimeUnit unit) {
        this.delegate.setStartTimestamp(startTimestamp, unit);
        return this;
    }

    @Override
    public Span.Builder addLink(Link link) {
        TraceContext traceContext = link.getTraceContext();
        SpanContext spanContext = ((OtelTraceContext) traceContext).spanContext();
        AttributesBuilder otelAttributes = Attributes.empty().toBuilder();
        for (Map.Entry<String, String> entry : link.getTags().entrySet()) {
            otelAttributes = otelAttributes.put(entry.getKey(), entry.getValue());
        }
        this.delegate.addLink(spanContext, otelAttributes.build());
        return this;
    }

    @Override
    public Span start() {
        io.opentelemetry.api.trace.Span span = this.delegate.startSpan();
        if (StringUtils.isNotEmpty(this.name)) {
            span.updateName(this.name);
        }
        if (this.error != null) {
            span.recordException(error);
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
