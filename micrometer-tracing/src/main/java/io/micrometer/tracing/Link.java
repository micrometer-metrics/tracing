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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a link between spans.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
public class Link {

    /**
     * A noop implementation.
     */
    public static final Link NOOP = new Link(TraceContext.NOOP, Collections.emptyMap());

    private final TraceContext traceContext;

    private final Map<String, Object> tags;

    /**
     * Creates a new instance of {@link Link}.
     * @param traceContext trace context attached to the link
     * @param tags attached to the link
     */
    public Link(TraceContext traceContext, Map<String, Object> tags) {
        this.traceContext = traceContext;
        this.tags = tags;
    }

    /**
     * Creates a new instance of {@link Link}.
     * @param traceContext trace context attached to the link
     */
    public Link(TraceContext traceContext) {
        this.traceContext = traceContext;
        this.tags = Collections.emptyMap();
    }

    /**
     * Creates a new instance of {@link Link}.
     * @param span span attached to the link
     * @param tags attached to the link
     */
    public Link(Span span, Map<String, Object> tags) {
        this.traceContext = span.context();
        this.tags = tags;
    }

    /**
     * Creates a new instance of {@link Link}.
     * @param span span attached to the link
     */
    public Link(Span span) {
        this.traceContext = span.context();
        this.tags = Collections.emptyMap();
    }

    /**
     * {@link TraceContext} attached to this link.
     * @return trace context
     */
    public TraceContext getTraceContext() {
        return this.traceContext;
    }

    /**
     * Tags attached to this link.
     * @return tags
     */
    public Map<String, Object> getTags() {
        return this.tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Link link = (Link) o;
        return Objects.equals(this.traceContext, link.traceContext) && Objects.equals(this.tags, link.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.traceContext, this.tags);
    }

    @Override
    public String toString() {
        return "Link{" + "traceContext=" + this.traceContext + ", tags=" + this.tags + '}';
    }

}
