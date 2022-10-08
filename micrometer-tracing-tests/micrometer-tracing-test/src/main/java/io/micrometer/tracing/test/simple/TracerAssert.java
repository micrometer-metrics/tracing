/**
 * Copyright 2022 the original author or authors.
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

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractCollectionAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;

/**
 * Assertion methods for {@code SimpleTracer}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link TracerAssert#assertThat(SimpleTracer)} or
 * {@link TracerAssert#then(SimpleTracer)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings("rawtypes")
public class TracerAssert extends AbstractAssert<TracerAssert, SimpleTracer> {

    /**
     * Creates a new instance of {@link TracerAssert}.
     * @param actual a {@link SimpleTracer} object to assert
     */
    protected TracerAssert(SimpleTracer actual) {
        super(actual, TracerAssert.class);
    }

    /**
     * Creates the assert object for {@link SimpleTracer}.
     * @param actual tracer to assert against
     * @return meter registry assertions
     */
    public static TracerAssert assertThat(SimpleTracer actual) {
        return new TracerAssert(actual);
    }

    /**
     * Creates the assert object for {@link SimpleTracer}.
     * @param actual tracer to assert against
     * @return meter registry assertions
     */
    public static TracerAssert then(SimpleTracer actual) {
        return new TracerAssert(actual);
    }

    /**
     * Verifies that there was only one span created by a {@link SimpleTracer}.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(simpleTracer).onlySpan();
     *
     * // assertions fail
     * assertThat(simpleTracerWithNoSpans).onlySpan();</code></pre>
     * @return {@link SpanAssert} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there were zero or more than one spans
     * @throws AssertionError if the span wasn't started
     * @since 1.0.0
     */
    public SpanAssert onlySpan() {
        isNotNull();
        return SpanAssert.assertThat(this.actual.onlySpan());
    }

    /**
     * Returns assertion options for the last span created by {@link SimpleTracer}.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(simpleTracer).lastSpan();
     *
     * // assertions fail
     * assertThat(simpleTracerWithNoSpans).lastSpan();</code></pre>
     * @return {@link SpanAssert} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there were no spans
     * @throws AssertionError if the span wasn't started
     * @since 1.0.0
     */
    public SpanAssert lastSpan() {
        isNotNull();
        return SpanAssert.assertThat(this.actual.lastSpan());
    }

    /**
     * Returns assertion options for all spans created by {@link SimpleTracer}.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(simpleTracer).reportedSpans();
     *
     * // assertions fail
     * assertThat(simpleTracerWithNoSpans).reportedSpans();</code></pre>
     * @return {@link SpanAssert} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there were no spans created by this tracer
     * @since 1.0.0
     */
    public AbstractCollectionAssert<?, Collection<? extends SimpleSpan>, SimpleSpan, ObjectAssert<SimpleSpan>> reportedSpans() {
        return Assertions.assertThat(this.actual.getSpans());
    }

}
