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

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import org.springframework.lang.Nullable;

class SpanFromSpanContext implements io.opentelemetry.api.trace.Span {

	final io.opentelemetry.api.trace.Span span;

	final SpanContext newSpanContext;

	final OtelTraceContext otelTraceContext;

	SpanFromSpanContext(io.opentelemetry.api.trace.Span span, SpanContext newSpanContext,
			OtelTraceContext otelTraceContext) {
		this.span = span != null ? span : io.opentelemetry.api.trace.Span.wrap(newSpanContext);
		this.newSpanContext = newSpanContext;
		this.otelTraceContext = otelTraceContext;
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, @Nullable String value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, long value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, double value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(String key, boolean value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name) {
		return span.addEvent(name);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, long timestamp, TimeUnit unit) {
		return span.addEvent(name, timestamp, unit);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, Instant timestamp) {
		return span.addEvent(name, timestamp);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, Attributes attributes) {
		return span.addEvent(name, attributes);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
		return span.addEvent(name, attributes, timestamp, unit);
	}

	@Override
	public io.opentelemetry.api.trace.Span addEvent(String name, Attributes attributes, Instant timestamp) {
		return span.addEvent(name, attributes, timestamp);
	}

	@Override
	public io.opentelemetry.api.trace.Span setStatus(StatusCode canonicalCode) {
		return span.setStatus(canonicalCode);
	}

	@Override
	public io.opentelemetry.api.trace.Span setStatus(StatusCode canonicalCode, String description) {
		return span.setStatus(canonicalCode, description);
	}

	@Override
	public io.opentelemetry.api.trace.Span setAttribute(AttributeKey<Long> key, int value) {
		return span.setAttribute(key, value);
	}

	@Override
	public <T> io.opentelemetry.api.trace.Span setAttribute(AttributeKey<T> key, T value) {
		return span.setAttribute(key, value);
	}

	@Override
	public io.opentelemetry.api.trace.Span recordException(Throwable exception) {
		return span.recordException(exception);
	}

	@Override
	public io.opentelemetry.api.trace.Span recordException(Throwable exception, Attributes additionalAttributes) {
		return span.recordException(exception, additionalAttributes);
	}

	@Override
	public io.opentelemetry.api.trace.Span updateName(String name) {
		return span.updateName(name);
	}

	@Override
	public void end() {
		span.end();
	}

	@Override
	public void end(long timestamp, TimeUnit unit) {
		span.end(timestamp, unit);
	}

	@Override
	public void end(Instant timestamp) {
		span.end(timestamp);
	}

	@Override
	public SpanContext getSpanContext() {
		return newSpanContext;
	}

	@Override
	public boolean isRecording() {
		return span.isRecording();
	}

	@Override
	public Context storeInContext(Context context) {
		return span.storeInContext(context);
	}

	@Override
	public String toString() {
		return "SpanFromSpanContext{" + "span=" + span + ", newSpanContext=" + newSpanContext + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SpanFromSpanContext that = (SpanFromSpanContext) o;
		return Objects.equals(span, that.span) && Objects.equals(this.newSpanContext, that.newSpanContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.span, this.newSpanContext);
	}

}
