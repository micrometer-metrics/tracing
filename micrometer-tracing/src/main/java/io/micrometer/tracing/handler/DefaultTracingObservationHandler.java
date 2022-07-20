/*
 * Copyright 2021-2021 the original author or authors.
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

package io.micrometer.tracing.handler;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * TracingRecordingListener that uses the Tracing API to record events.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class DefaultTracingObservationHandler implements TracingObservationHandler<Observation.Context> {

	private final Tracer tracer;

	/**
	 * Creates a new instance of {@link DefaultTracingObservationHandler}.
	 * @param tracer the tracer to use to record events
	 */
	public DefaultTracingObservationHandler(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void onStart(Observation.Context context) {
		Span parentSpan = getParentSpan(context);
		Span childSpan = parentSpan != null ? getTracer().nextSpan(parentSpan) : getTracer().nextSpan();
		childSpan.start();
		getTracingContext(context).setSpan(childSpan);
	}

	@Override
	public void onStop(Observation.Context context) {
		Span span = getRequiredSpan(context);
		span.name(getSpanName(context));
		tagSpan(context, span);
		span.end();
	}

	@Override
	public void onError(Observation.Context context) {
		Span span = getTracingContext(context).getSpan();
		context.getError().ifPresent(span::error);
	}

	@Override
	public void onEvent(Observation.Event event, Observation.Context context) {
		getTracingContext(context).getSpan().event(event.getContextualName());
	}

	@Override
	public Tracer getTracer() {
		return this.tracer;
	}

}
