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

package org.springframework.boot.autoconfigure.observability.tracing.reporter.wavefront;

import java.io.Closeable;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;

import org.springframework.boot.autoconfigure.observability.tracing.brave.bridge.BraveFinishedSpan;
import org.springframework.boot.autoconfigure.observability.tracing.brave.bridge.BraveTraceContext;

/**
 * A {@link SpanHandler} that sends spans to Wavefront.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class WavefrontBraveSpanHandler extends SpanHandler implements Runnable, Closeable {

	private final WavefrontSpringObservabilitySpanHandler spanHandler;

	/**
	 * @param spanHandler wavefront span handler
	 */
	public WavefrontBraveSpanHandler(WavefrontSpringObservabilitySpanHandler spanHandler) {
		this.spanHandler = spanHandler;
	}

	@Override
	public boolean end(TraceContext context, MutableSpan span, Cause cause) {
		return spanHandler.end(BraveTraceContext.fromBrave(context), BraveFinishedSpan.fromBrave(span));
	}

	@Override
	public void close() {
		this.spanHandler.close();
	}

	@Override
	public void run() {
		this.spanHandler.run();
	}

}
