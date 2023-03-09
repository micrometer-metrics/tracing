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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.Tracer;

/**
 * A test implementation of a span customizer.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpanCustomizer implements SpanCustomizer {

    private final SimpleSpan span;

    private final Tracer tracer;

    /**
     * Creates a new instance of {@link SimpleSpanCustomizer}.
     * @param span simple span
     * @deprecated use {@link SimpleSpanCustomizer(SimpleTracer)}
     */
    @Deprecated
    public SimpleSpanCustomizer(SimpleSpan span) {
        this.span = span;
        this.tracer = Tracer.NOOP;
    }

    /**
     * Creates a new instance of {@link SimpleSpanCustomizer}.
     * @param tracer simple tracer
     */
    public SimpleSpanCustomizer(SimpleTracer tracer) {
        this.span = null;
        this.tracer = tracer;
    }

    private Span currentSpan() {
        Span currentSpan = this.tracer.currentSpan();
        if (currentSpan == null) {
            throw new IllegalStateException("Current span is null");
        }
        return currentSpan;
    }

    @Override
    public SpanCustomizer name(String name) {
        currentSpan().name(name);
        return this;
    }

    @Override
    public SpanCustomizer tag(String key, String value) {
        currentSpan().tag(key, value);
        return this;
    }

    @Override
    public SpanCustomizer event(String value) {
        currentSpan().event(value);
        return this;
    }

}
