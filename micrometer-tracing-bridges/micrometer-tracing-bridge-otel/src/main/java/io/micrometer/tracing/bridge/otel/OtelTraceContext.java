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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;

import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry implementation of a {@link TraceContext}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelTraceContext implements TraceContext {

	final AtomicReference<Context> context;

	final SpanContext delegate;

	final Span span;

	OtelTraceContext(Context context, SpanContext delegate, @Nullable Span span) {
		this(new AtomicReference<>(context), delegate, span);
	}

	OtelTraceContext(AtomicReference<Context> context, SpanContext delegate, @Nullable Span span) {
		this.context = context;
		this.delegate = delegate;
		this.span = span;
	}

	OtelTraceContext(SpanContext delegate, @Nullable Span span) {
		this.context = new AtomicReference<>(Context.current());
		this.delegate = delegate;
		this.span = span;
	}

	OtelTraceContext(Span span) {
		this(Context.current(), span.getSpanContext(), span);
	}

	OtelTraceContext(SpanFromSpanContext span) {
		this(span.otelTraceContext.context.get(), span.getSpanContext(), span);
	}

	public static TraceContext fromOtel(SpanContext traceContext) {
		return new OtelTraceContext(traceContext, null);
	}

	public static Context toOtelContext(TraceContext context) {
		if (context instanceof OtelTraceContext) {
			Span span = ((OtelTraceContext) context).span;
			if (span != null) {
				return span.storeInContext(Context.current());
			}
		}
		return Context.current();
	}

	@Override
	public String traceId() {
		return this.delegate.getTraceId();
	}

	@Override
	@Nullable
	public String parentId() {
		if (this.span instanceof ReadableSpan) {
			ReadableSpan readableSpan = (ReadableSpan) this.span;
			return readableSpan.toSpanData().getParentSpanId();
		}
		return null;
	}

	@Override
	public String spanId() {
		return this.delegate.getSpanId();
	}

	@Override
	public Boolean sampled() {
		return this.delegate.isSampled();
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	Span span() {
		return this.span;
	}

	Context context() {
		return this.context.get();
	}

	void updateContext(Context context) {
		this.context.set(context);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OtelTraceContext context = (OtelTraceContext) o;
		return Objects.equals(this.delegate, context.delegate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.delegate);
	}

}
