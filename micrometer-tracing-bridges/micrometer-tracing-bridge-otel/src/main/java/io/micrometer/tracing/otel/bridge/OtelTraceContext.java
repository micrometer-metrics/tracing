/*
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

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenTelemetry implementation of a {@link TraceContext}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelTraceContext implements TraceContext {

    final AtomicReference<@Nullable Context> otelContext;

    final SpanContext delegate;

    final @Nullable Span span;

    OtelTraceContext(@Nullable Context context, SpanContext delegate, @Nullable Span span) {
        this.delegate = delegate;
        this.span = span;
        this.otelContext = new AtomicReference<>(storeInContext(context == null ? Context.current() : context));
    }

    OtelTraceContext(SpanContext delegate, @Nullable Span span) {
        this(Context.current(), delegate, span);
    }

    OtelTraceContext(Span span) {
        this(span.getSpanContext(), span);
    }

    /**
     * Converts from OTel to Tracing.
     * @param context OTel version
     * @return Tracing version
     */
    public static TraceContext fromOtel(SpanContext context) {
        return new OtelTraceContext(context, null);
    }

    /**
     * Converts from Tracing to OTel.
     * @param context Tracing version
     * @return OTel Context
     */
    public static Context toOtelContext(TraceContext context) {
        if (context instanceof OtelTraceContext) {
            Span span = ((OtelTraceContext) context).span;
            if (span != null) {
                return span.storeInContext(Context.current());
            }
            else {
                return Context.current().with(Span.wrap(((OtelTraceContext) context).delegate));
            }
        }
        return Context.current();
    }

    /**
     * Converts from Tracing to OTel SpanContext.
     * @param context Tracing version
     * @return OTel version
     * @since 1.1.0
     */
    public static @Nullable SpanContext toOtelSpanContext(TraceContext context) {
        if (context instanceof OtelTraceContext) {
            return ((OtelTraceContext) context).delegate;
        }
        return null;
    }

    @Override
    public String traceId() {
        return this.delegate.getTraceId();
    }

    @Override
    public @Nullable String parentId() {
        if (this.span instanceof ReadableSpan) {
            ReadableSpan readableSpan = (ReadableSpan) this.span;
            String parentSpanId = readableSpan.toSpanData().getParentSpanId();
            if (Objects.equals(Span.getInvalid().getSpanContext().getSpanId(), parentSpanId)) {
                return null;
            }
            return parentSpanId;
        }
        return null;
    }

    @Override
    public String spanId() {
        return this.delegate.getSpanId();
    }

    @Override
    public @Nullable Boolean sampled() {
        return this.delegate.isSampled();
    }

    @Override
    public String toString() {
        return this.delegate != null ? this.delegate.toString() : "null";
    }

    Context context() {
        Context ctx = this.otelContext.get();
        return ctx != null ? ctx : Context.root();
    }

    SpanContext spanContext() {
        return this.delegate;
    }

    /**
     * Stores this trace context and its span in the given context. Returns the given
     * context unchanged if both are already stored in it.
     * @param base context to store this trace context in
     * @return context containing this trace context and its span
     */
    Context storeInContext(Context base) {
        Context context = base;
        if (this.span != null) {
            context = this.span.storeInContext(context);
        }
        else if (!Span.fromContext(context).getSpanContext().equals(this.delegate)) {
            context = context.with(Span.wrap(this.delegate));
        }
        return context.with(OtelCurrentTraceContext.OTEL_CONTEXT_KEY, this);
    }

    void updateContext(@Nullable Context context) {
        this.otelContext.set(context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OtelTraceContext otelTraceContext = (OtelTraceContext) o;
        return Objects.equals(this.delegate, otelTraceContext.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.delegate);
    }

}
