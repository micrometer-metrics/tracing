/*
 * Copyright 2022 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.test.simple;

import java.util.Collection;

import io.micrometer.tracing.exporter.FinishedSpan;

/**
 * Entry point assertions.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.0.0
 */
public class TracingAssertions {

	/**
	 * Creates a new instance of {@link SpanAssert}.
	 * @param actual a span to assert against
	 * @return span assertions
	 */
	@SuppressWarnings("rawtypes")
	public static SpanAssert assertThat(FinishedSpan actual) {
		return new SpanAssert(actual);
	}

	/**
	 * Creates a new instance of {@link SpanAssert}.
	 * @param actual a span to assert against
	 * @return span assertions
	 */
	@SuppressWarnings("rawtypes")
	public static SpanAssert then(FinishedSpan actual) {
		return new SpanAssert(actual);
	}

	/**
	 * Creates a new instance of {@link SpansAssert}.
	 * @param actual spans to assert against
	 * @return spans assertions
	 */
	public static SpansAssert assertThat(Collection<FinishedSpan> actual) {
		return new SpansAssert(actual);
	}

	/**
	 * Creates a new instance of {@link SpansAssert}.
	 * @param actual spans to assert against
	 * @return spans assertions
	 */
	public static SpansAssert then(Collection<FinishedSpan> actual) {
		return new SpansAssert(actual);
	}

	/**
	 * Creates a new instance of {@link TracerAssert}.
	 * @param actual a tracer to assert against
	 * @return tracer assertions
	 */
	public static TracerAssert assertThat(SimpleTracer actual) {
		return new TracerAssert(actual);
	}

	/**
	 * Creates a new instance of {@link TracerAssert}.
	 * @param actual a tracer to assert against
	 * @return tracer assertions
	 */
	public static TracerAssert then(SimpleTracer actual) {
		return new TracerAssert(actual);
	}

}
