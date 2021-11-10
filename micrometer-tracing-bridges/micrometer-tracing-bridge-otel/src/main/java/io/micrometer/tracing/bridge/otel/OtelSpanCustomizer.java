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

import io.opentelemetry.api.trace.Span;

import org.springframework.cloud.sleuth.SpanCustomizer;

/**
 * OpenTelemetry implementation of a {@link SpanCustomizer}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelSpanCustomizer implements SpanCustomizer {

	@Override
	public SpanCustomizer name(String name) {
		currentSpan().updateName(name);
		return this;
	}

	private Span currentSpan() {
		return Span.current();
	}

	@Override
	public SpanCustomizer tag(String key, String value) {
		currentSpan().setAttribute(key, value);
		return this;
	}

	@Override
	public SpanCustomizer event(String value) {
		currentSpan().addEvent(value);
		return this;
	}

}
