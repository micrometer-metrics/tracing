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
package io.micrometer.tracing;

import java.io.Closeable;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import org.jspecify.annotations.Nullable;

/**
 * Container object for {@link Span} and its corresponding {@link Tracer.SpanInScope}.
 *
 * @author Marcin Grzejszczak
 * @author Arthur Gavlyukovskiy
 * @since 1.0.0
 */
public class SpanAndScope implements Closeable {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SpanAndScope.class);

    private final Span span;

    private final Tracer.@Nullable SpanInScope scope;

    /**
     * Creates a new span and scope
     * @param span span
     * @param scope scope
     */
    public SpanAndScope(Span span, Tracer.@Nullable SpanInScope scope) {
        this.span = span;
        this.scope = scope;
    }

    /**
     * Gets the span.
     * @return span
     */
    public Span getSpan() {
        return this.span;
    }

    /**
     * Gets the scope.
     * @return scope
     */
    public Tracer.@Nullable SpanInScope getScope() {
        return this.scope;
    }

    @Override
    public String toString() {
        return "SpanAndScope{" + "span=" + this.span + '}';
    }

    @Override
    public void close() {
        if (log.isTraceEnabled()) {
            log.trace("Closing span [" + this.span + "]");
        }
        if (this.scope != null) {
            this.scope.close();
        }
        this.span.end();
    }

}
