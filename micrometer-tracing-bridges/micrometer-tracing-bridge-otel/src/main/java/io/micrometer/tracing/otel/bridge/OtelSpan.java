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

import io.micrometer.tracing.Span;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;

/**
 * OpenTelemetry implementation of a {@link Span}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelSpan implements Span {

    static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

    final io.opentelemetry.api.trace.Span delegate;

    final OtelTraceContext otelTraceContext;

    public OtelSpan(io.opentelemetry.api.trace.Span delegate) {
        this.delegate = delegate;
        this.otelTraceContext = new OtelTraceContext(delegate.getSpanContext(), delegate);
    }

    public OtelSpan(io.opentelemetry.api.trace.Span delegate, Context context) {
        this.delegate = delegate;
        this.otelTraceContext = new OtelTraceContext(context, delegate.getSpanContext(), delegate);
    }

    public OtelSpan(OtelTraceContext traceContext) {
        this.delegate = traceContext.span != null ? traceContext.span : io.opentelemetry.api.trace.Span.current();
        this.otelTraceContext = traceContext;
    }

    public static io.opentelemetry.api.trace.Span toOtel(Span span) {
        return ((OtelSpan) span).delegate;
    }

    public static Span fromOtel(io.opentelemetry.api.trace.Span span) {
        return new OtelSpan(span);
    }

    public static Span fromOtel(io.opentelemetry.api.trace.Span span, Context context) {
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
        return this.otelTraceContext;
    }

    @Override
    public Span start() {
        // they are already started via the builder
        return this;
    }

    @Override
    public Span name(String name) {
        this.delegate.updateName(name);
        return this;
    }

    @Override
    public Span event(String value) {
        this.delegate.addEvent(value);
        return this;
    }

    @Override
    public Span event(String value, long time, TimeUnit timeUnit) {
        this.delegate.addEvent(value, time, timeUnit);
        return this;
    }

    @Override
    public Span tag(String key, String value) {
        this.delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, long value) {
        this.delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, double value) {
        this.delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, boolean value) {
        this.delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tagOfStrings(String key, List<String> values) {
        this.delegate.setAttribute(AttributeKey.stringArrayKey(key), values);
        return this;
    }

    @Override
    public Span tagOfLongs(String key, List<Long> values) {
        this.delegate.setAttribute(AttributeKey.longArrayKey(key), values);
        return this;
    }

    @Override
    public Span tagOfDoubles(String key, List<Double> values) {
        this.delegate.setAttribute(AttributeKey.doubleArrayKey(key), values);
        return this;
    }

    @Override
    public Span tagOfBooleans(String key, List<Boolean> values) {
        this.delegate.setAttribute(AttributeKey.booleanArrayKey(key), values);
        return this;
    }

    @Override
    public void end(long time, TimeUnit timeUnit) {
        if (this.isStatusUnset()) {
            this.delegate.setStatus(StatusCode.OK);
        }
        this.delegate.end(time, timeUnit);
    }

    @Override
    public Span remoteIpAndPort(String ip, int port) {
        this.delegate.setAttribute(NETWORK_PEER_ADDRESS, ip);
        this.delegate.setAttribute(NETWORK_PEER_PORT, port);
        return this;
    }

    @Override
    public Span error(Throwable throwable) {
        this.delegate.recordException(throwable);
        if (throwable.getMessage() == null) {
            this.delegate.setStatus(StatusCode.ERROR);
        }
        else {
            this.delegate.setStatus(StatusCode.ERROR, throwable.getMessage());
        }
        return this;
    }

    @Override
    public void end() {
        if (this.isStatusUnset()) {
            this.delegate.setStatus(StatusCode.OK);
        }
        this.delegate.end();
    }

    @Override
    public void abandon() {
        // Do nothing
    }

    @Override
    public Span remoteServiceName(String remoteServiceName) {
        this.delegate.setAttribute(PEER_SERVICE, remoteServiceName);
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
        io.opentelemetry.api.trace.Span span = otelSpan.delegate;
        if (span instanceof SpanFromSpanContext) {
            span = ((SpanFromSpanContext) span).span;
        }
        return Objects.equals(this.delegate, span);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.delegate);
    }

    private boolean isStatusUnset() {
        if (this.delegate instanceof ReadableSpan) {
            return ((ReadableSpan) this.delegate).toSpanData().getStatus().getStatusCode() == StatusCode.UNSET;
        }
        return false;
    }

}
