/*
 * Copyright 2013-2021 the original author or authors.
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

package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.SpanCustomizer;

/**
 * A test implementation of a span customizer.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpanCustomizer implements SpanCustomizer {

	private final SimpleSpan span;

	/**
	 * Creates a new instance of {@link SimpleSpanCustomizer}.
	 * @param span simple span
	 */
	public SimpleSpanCustomizer(SimpleSpan span) {
		this.span = span;
	}

	@Override
	public SpanCustomizer name(String name) {
		this.span.name(name);
		return this;
	}

	@Override
	public SpanCustomizer tag(String key, String value) {
		this.span.tag(key, value);
		return this;
	}

	@Override
	public SpanCustomizer event(String value) {
		this.span.event(value);
		return this;
	}

}
