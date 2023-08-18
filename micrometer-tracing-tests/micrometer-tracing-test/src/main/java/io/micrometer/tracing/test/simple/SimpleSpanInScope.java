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

import io.micrometer.tracing.*;
import io.micrometer.tracing.CurrentTraceContext.Scope;

/**
 * A test implementation of a span in scope.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleSpanInScope implements Tracer.SpanInScope {

    private volatile boolean closed;

    private final ThreadLocal<SpanAndScope> scopedSpans;

    private final SpanAndScope spanAndScope;

    private final SpanAndScope previousSpanAndScope;

    private final CurrentTraceContext.Scope scope;

    /**
     * Creates a new instance of {@link SimpleSpanInScope}.
     * @param span span
     * @param scopedSpans scoped spans
     * @deprecated use {@link #SimpleSpanInScope(Scope)}
     */
    @Deprecated
    public SimpleSpanInScope(Span span, ThreadLocal<SpanAndScope> scopedSpans) {
        this.scopedSpans = scopedSpans;
        this.spanAndScope = new SpanAndScope(span, this);
        this.previousSpanAndScope = scopedSpans.get();
        this.scope = () -> {
            SpanAndScope current = this.scopedSpans.get();
            if (current != null && current != this.spanAndScope) {
                throw new IllegalStateException("\nTrying to close scope for span \n\n[" + span
                        + "] \n\nbut current span in scope is \n\n[" + current.getSpan() + "]");
            }
            this.scopedSpans.set(this.previousSpanAndScope);
        };
        if (span != null) {
            this.scopedSpans.set(this.spanAndScope);
        }
        else {
            this.scopedSpans.remove();
        }
    }

    /**
     * Creates a new instance of {@link SimpleSpanInScope}.
     * @param scope current trace context scope
     */
    public SimpleSpanInScope(CurrentTraceContext.Scope scope) {
        this.scopedSpans = null;
        this.spanAndScope = null;
        this.previousSpanAndScope = null;
        this.scope = scope;
    }

    @Override
    public void close() {
        this.closed = true;
        this.scope.close();
    }

    /**
     * @return was scoped closed?
     */
    public boolean isClosed() {
        return closed;
    }

}
