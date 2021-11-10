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

import java.util.LinkedList;
import java.util.List;

import io.opentelemetry.api.trace.SpanKind;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.util.StringUtils;

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

	OtelSpanBuilder(io.opentelemetry.api.trace.SpanBuilder delegate) {
		this.delegate = delegate;
	}

	static Span.Builder fromOtel(io.opentelemetry.api.trace.SpanBuilder builder) {
		return new OtelSpanBuilder(builder);
	}

	@Override
	public Span.Builder setParent(TraceContext context) {
		this.delegate.setParent(OtelTraceContext.toOtelContext(context));
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
	public Span start() {
		io.opentelemetry.api.trace.Span span = this.delegate.startSpan();
		if (StringUtils.hasText(this.name)) {
			span.updateName(this.name);
		}
		if (this.error != null) {
			span.recordException(error);
		}
		this.annotations.forEach(span::addEvent);
		return OtelSpan.fromOtel(span);
	}

}
