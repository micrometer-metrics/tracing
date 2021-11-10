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

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * OpenTelemetry implementation of a {@link CurrentTraceContext}.
 *
 * @author Marcin Grzejszczak
 * @author John Watson
 * @since 1.0.0
 */
public class OtelCurrentTraceContext implements CurrentTraceContext {

	@Override
	public TraceContext context() {
		Span currentSpan = Span.current();
		if (Span.getInvalid().equals(currentSpan)) {
			return null;
		}
		if (currentSpan instanceof SpanFromSpanContext) {
			return new OtelTraceContext((SpanFromSpanContext) currentSpan);
		}
		return new OtelTraceContext(currentSpan);
	}

	@Override
	public Scope newScope(TraceContext context) {
		OtelTraceContext otelTraceContext = (OtelTraceContext) context;
		if (otelTraceContext == null) {
			return io.opentelemetry.context.Scope::noop;
		}
		Context current = Context.current();
		Context old = otelTraceContext.context();

		Span currentSpan = Span.fromContext(current);
		Span oldSpan = Span.fromContext(otelTraceContext.context());
		SpanContext spanContext = otelTraceContext.delegate;
		boolean sameSpan = currentSpan.getSpanContext().equals(oldSpan.getSpanContext())
				&& currentSpan.getSpanContext().equals(spanContext);
		SpanFromSpanContext fromContext = new SpanFromSpanContext(((OtelTraceContext) context).span, spanContext,
				otelTraceContext);

		Baggage currentBaggage = Baggage.fromContext(current);
		Baggage oldBaggage = Baggage.fromContext(old);
		boolean sameBaggage = sameBaggage(currentBaggage, oldBaggage);

		if (sameSpan && sameBaggage) {
			return io.opentelemetry.context.Scope::noop;
		}

		BaggageBuilder baggageBuilder = currentBaggage.toBuilder();
		oldBaggage.forEach(
				(key, baggageEntry) -> baggageBuilder.put(key, baggageEntry.getValue(), baggageEntry.getMetadata()));
		Baggage updatedBaggage = baggageBuilder.build();

		io.opentelemetry.context.Scope attach = old.with(fromContext).with(updatedBaggage).makeCurrent();
		return attach::close;
	}

	private boolean sameBaggage(Baggage currentBaggage, Baggage oldBaggage) {
		return currentBaggage.equals(oldBaggage);
	}

	@Override
	public Scope maybeScope(TraceContext context) {
		return newScope(context);
	}

	@Override
	public <C> Callable<C> wrap(Callable<C> task) {
		return Context.current().wrap(task);
	}

	@Override
	public Runnable wrap(Runnable task) {
		return Context.current().wrap(task);
	}

	@Override
	public Executor wrap(Executor delegate) {
		return Context.current().wrap(delegate);
	}

	@Override
	public ExecutorService wrap(ExecutorService delegate) {
		return Context.current().wrap(delegate);
	}

}
