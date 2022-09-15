/*
 * Copyright 2013-2020 the original author or authors.
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

package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenTelemetry implementation of a {@link Span}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class OtelSpan implements Span {

    final io.opentelemetry.api.trace.Span delegate;

    private final AtomicReference<Context> context;

    OtelSpan(io.opentelemetry.api.trace.Span delegate) {
        this.delegate = delegate;
        if (delegate instanceof SpanFromSpanContext) {
            SpanFromSpanContext fromSpanContext = (SpanFromSpanContext) delegate;
            this.context = fromSpanContext.otelTraceContext.context;
        }
        else {
            this.context = new AtomicReference<>(Context.current());
        }
    }

    OtelSpan(io.opentelemetry.api.trace.Span delegate, Context context) {
        this.delegate = delegate;
        this.context = new AtomicReference<>(context);
    }

    static io.opentelemetry.api.trace.Span toOtel(Span span) {
        return ((OtelSpan) span).delegate;
    }

    static Span fromOtel(io.opentelemetry.api.trace.Span span) {
        return new OtelSpan(span);
    }

    static Span fromOtel(io.opentelemetry.api.trace.Span span, Context context) {
        return new OtelSpan(span, context);
    }

    @Override
    public boolean isNoop() {
        return !this.delegate.isRecording();
    }

    @Override
    public OtelTraceContext context() {
        if (this.delegate == null) {
            return null;
        }
        return new OtelTraceContext(this.context, this.delegate.getSpanContext(), this.delegate);
    }

    @Override
    public Span start() {
        // they are already started via the builder
        return this;
    }

    @Override
    public Span name(String name) {
        this.delegate.updateName(name);
        return new OtelSpan(this.delegate);
    }

    @Override
    public Span event(String value) {
        this.delegate.addEvent(value);
        return new OtelSpan(this.delegate);
    }

    @Override
    public Span event(String value, long time, TimeUnit timeUnit) {
        this.delegate.addEvent(value, time, timeUnit);
        return null;
    }

    @Override
    public Span tag(String key, String value) {
        this.delegate.setAttribute(key, value);
        return new OtelSpan(this.delegate);
    }

    @Override
    public void end(long time, TimeUnit timeUnit) {
        this.delegate.end(time, timeUnit);
    }

    @Override
    public Span remoteIpAndPort(String ip, int port) {
        this.delegate.setAttribute(SemanticAttributes.NET_PEER_IP, ip);
        this.delegate.setAttribute(SemanticAttributes.NET_PEER_PORT, port);
        return this;
    }

    @Override
    public Span error(Throwable throwable) {
        this.delegate.recordException(throwable);
        this.delegate.setStatus(StatusCode.ERROR, throwable.getMessage());
        return new OtelSpan(this.delegate);
    }

    @Override
    public void end() {
        this.delegate.end();
    }

    @Override
    public void abandon() {
        // TODO: [OTEL] doesn't seem to have this notion yet
    }

    @Override
    public Span remoteServiceName(String remoteServiceName) {
        this.delegate.setAttribute(OtelSpanBuilder.REMOTE_SERVICE_NAME_KEY, remoteServiceName);
        return this;
    }

    @Override
    public String toString() {
        return this.delegate != null ? this.delegate.toString() : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OtelSpan otelSpan = (OtelSpan) o;
        return Objects.equals(this.delegate, otelSpan.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.delegate);
    }

}
