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

package org.springframework.boot.autoconfigure.observability.tracing.listener;

import io.micrometer.core.instrument.TagsProvider;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimerRecordingListener;
import io.micrometer.core.instrument.tracing.CurrentTraceContext;
import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.Tracer;

import org.springframework.lang.Nullable;

/**
 * Marker interface for tracing listeners.
 *
 * @author Marcin Grzejszczak
 * @param <T> type of event
 * @since 6.0.0
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public interface TracingRecordingListener<T extends Timer.Context>
		extends TimerRecordingListener<T> {

	class HandledContextAttribute {

	}

	// @Override
	// default void onCreate(Timer.Sample sample) {
	// Span span = getTracer().currentSpan();
	// if (span != null) {
	// setSpanAndScope(sample, span);
	// }
	// }

	/**
	 * Sets span and a scope for that span in context.
	 *
	 * @param context recording with context to mutate
	 * @param span span to put in context
	 */
	default void setSpanAndScope(T context, Span span) {
		if (span == null) {
			return;
		}
		CurrentTraceContext.Scope scope = getTracer().currentTraceContext()
				.maybeScope(span.context());
		getTracingContext(context).setSpanAndScope(span, scope);
	}

	default void tagSpan(T context, Span span) {
		TagsProvider<?> tagsProvider = context.getTagsProvider();
		if (tagsProvider != null) {
			tagsProvider.getAllTags()
					.forEach(tag -> span.tag(tag.getKey(), tag.getValue()));
		}
	}

	/**
	 * Cleans the scope present in the context.
	 *
	 * @param context recording with context containing scope
	 */
	default void cleanup(T context) {
		TracingContext tracingContext = getTracingContext(context);
		tracingContext.getScope().close();
	}

	@Override
	default void onRestore(Timer.Sample intervalRecording, T context) {
		Span span = getTracingContext(context).getSpan();
		setSpanAndScope(context, span);
	}

	@Nullable
	default TracingContext getTracingContext(T context) {
		// maybe consider returning a null ?
		return context.computeIfAbsent(TracingContext.class,
				(clazz) -> new TracingContext());
	}

	@Nullable
	default void setTracingContext(T context, TracingContext tracingContext) {
		context.put(TracingContext.class, tracingContext);
	}

	@Override
	default boolean supportsContext(Timer.Context context) {
		return true;
	}

	/**
	 * Returns the {@link Tracer}.
	 *
	 * @return tracer
	 */
	Tracer getTracer();

	/**
	 * Basic tracing context.
	 *
	 * @author Marcin Grzejszczak
	 * @since 6.0.0
	 */
	class TracingContext {

		private Span span;

		private CurrentTraceContext.Scope scope;

		/**
		 * Returns the span.
		 *
		 * @return span
		 */
		Span getSpan() {
			return this.span;
		}

		/**
		 * Sets the span.
		 *
		 * @param span span to set
		 */
		void setSpan(Span span) {
			this.span = span;
		}

		/**
		 * Returns the scope of the span.
		 *
		 * @return scope of the span
		 */
		CurrentTraceContext.Scope getScope() {
			return this.scope;
		}

		/**
		 * Sets the current trace context scope.
		 *
		 * @param scope scope to set
		 */
		void setScope(CurrentTraceContext.Scope scope) {
			this.scope = scope;
		}

		/**
		 * Convenience method to set both span and scope.
		 *
		 * @param span span to set
		 * @param scope scope to set
		 */
		void setSpanAndScope(Span span, CurrentTraceContext.Scope scope) {
			setSpan(span);
			setScope(scope);
		}

	}

}
