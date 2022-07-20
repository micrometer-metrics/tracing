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

import java.util.Map;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

/**
 * OpenTelemetry implementation of a {@link Tracer}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelTracer implements Tracer {

	private final io.opentelemetry.api.trace.Tracer tracer;

	private final BaggageManager otelBaggageManager;

	private final OtelCurrentTraceContext otelCurrentTraceContext;

	private final EventPublisher publisher;

	/**
	 * Creates a new instance of {@link OtelTracer}.
	 * @param tracer tracer
	 * @param otelCurrentTraceContext current trace context
	 * @param publisher event publisher
	 * @param otelBaggageManager baggage manager
	 */
	public OtelTracer(io.opentelemetry.api.trace.Tracer tracer, OtelCurrentTraceContext otelCurrentTraceContext,
			EventPublisher publisher, BaggageManager otelBaggageManager) {
		this.tracer = tracer;
		this.publisher = publisher;
		this.otelBaggageManager = otelBaggageManager;
		this.otelCurrentTraceContext = otelCurrentTraceContext;
	}

	@Override
	public Span nextSpan(Span parent) {
		if (parent == null) {
			return nextSpan();
		}
		return OtelSpan.fromOtel(
				this.tracer.spanBuilder("").setParent(OtelTraceContext.toOtelContext(parent.context())).startSpan());
	}

	@Override
	public Tracer.SpanInScope withSpan(Span span) {
		io.opentelemetry.api.trace.Span delegate = delegate(span);
		return new OtelSpanInScope((OtelSpan) span, delegate);
	}

	private io.opentelemetry.api.trace.Span delegate(Span span) {
		if (span == null) {
			// remove any existing span/baggage data from the current state of anything
			// that might be holding on to it.
			this.publisher.publishEvent(new EventPublishingContextWrapper.ScopeClosedEvent());
			return io.opentelemetry.api.trace.Span.getInvalid();
		}
		return ((OtelSpan) span).delegate;
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return new OtelSpanCustomizer();
	}

	@Override
	public Span currentSpan() {
		io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
		if (currentSpan == null || currentSpan.equals(io.opentelemetry.api.trace.Span.getInvalid())) {
			return null;
		}
		return new OtelSpan(currentSpan);
	}

	@Override
	public Span nextSpan() {
		return new OtelSpan(this.tracer.spanBuilder("").startSpan());
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		io.opentelemetry.api.trace.Span span = this.tracer.spanBuilder(name).startSpan();
		return new OtelScopedSpan(span, span.makeCurrent());
	}

	@Override
	public Span.Builder spanBuilder() {
		return new OtelSpanBuilder(this.tracer.spanBuilder(""));
	}

	@Override
	public TraceContext.Builder traceContextBuilder() {
		return new OtelTraceContextBuilder();
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		return this.otelCurrentTraceContext;
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return this.otelBaggageManager.getAllBaggage();
	}

	@Override
	public BaggageInScope getBaggage(String name) {
		return this.otelBaggageManager.getBaggage(name);
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return this.otelBaggageManager.getBaggage(traceContext, name);
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return this.otelBaggageManager.createBaggage(name);
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		return this.otelBaggageManager.createBaggage(name, value);
	}

	public interface EventPublisher {

		void publishEvent(Object event);

	}

}
