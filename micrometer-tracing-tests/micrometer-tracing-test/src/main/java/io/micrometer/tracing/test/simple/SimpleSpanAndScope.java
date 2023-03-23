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

import io.micrometer.common.lang.Nullable;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.SpanAndScope;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.Objects;

/**
 * Container object for {@link Span} and its corresponding {@link Tracer.SpanInScope}.
 *
 * @author Marcin Grzejszczak
 * @author Arthur Gavlyukovskiy
 * @since 1.0.0
 */
public class SimpleSpanAndScope extends SpanAndScope {

    private final TraceContext traceContext;

    /**
     * Creates a new span and scope
     * @param span span
     * @param scope scope
     */
    public SimpleSpanAndScope(Span span, @Nullable Tracer.SpanInScope scope) {
        super(span, scope);
        this.traceContext = span.context();
    }

    /**
     * Creates a new span and scope
     * @param traceContext trace context
     * @param scope scope
     */
    public SimpleSpanAndScope(TraceContext traceContext, @Nullable Tracer.SpanInScope scope) {
        super(Objects.requireNonNull(SimpleTracer.getSpanForTraceContext(traceContext),
                "You must create a span with this context before"), scope);
        this.traceContext = traceContext;
    }

    /**
     * Gets the trace context.
     * @return trace context
     */
    public TraceContext getTraceContext() {
        return traceContext;
    }

}
