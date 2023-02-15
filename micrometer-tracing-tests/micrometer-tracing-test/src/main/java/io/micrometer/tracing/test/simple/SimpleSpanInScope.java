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
import io.micrometer.tracing.SpanAndScope;
import io.micrometer.tracing.Tracer;

/**
 * A test implementation of a span in scope.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpanInScope implements Tracer.SpanInScope {

    private volatile boolean closed;

    private final ThreadLocal<SpanAndScope> scopedSpans;

    private final Span span;

    private final SpanAndScope spanAndScope;

    private final SpanAndScope previousSpanAndScope;

    /**
     * Creates a new instance of {@link SimpleSpanInScope}.
     * @param span span
     * @param scopedSpans scoped spans
     */
    public SimpleSpanInScope(Span span, ThreadLocal<SpanAndScope> scopedSpans) {
        this.span = span;
        this.scopedSpans = scopedSpans;
        this.spanAndScope = new SpanAndScope(span, this);
        this.previousSpanAndScope = scopedSpans.get();
        if (span != null) {
            this.scopedSpans.set(this.spanAndScope);
        }
        else {
            this.scopedSpans.remove();
        }
    }

    @Override
    public void close() {
        this.closed = true;
        SpanAndScope current = this.scopedSpans.get();
        if (current != null && current != this.spanAndScope) {
            throw new IllegalStateException("Trying to close scope for span [" + span
                    + "] but current span in scope is [" + current.getSpan() + "]");
        }
        this.scopedSpans.set(this.previousSpanAndScope);
    }

    /**
     * @return was scoped closed?
     */
    public boolean isClosed() {
        return closed;
    }

}
