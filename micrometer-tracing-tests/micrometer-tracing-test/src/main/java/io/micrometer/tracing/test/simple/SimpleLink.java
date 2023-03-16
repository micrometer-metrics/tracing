/**
 * Copyright 2023 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.micrometer.tracing.TraceContext;

/**
 * Represents a link between spans.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
public class SimpleLink {

    private final TraceContext traceContext;

    private final Map<String, String> tags;

    /**
     * Creates a new instance of {@link SimpleLink}.
     * @param traceContext trace context
     * @param tags tags
     */
    public SimpleLink(TraceContext traceContext, Map<String, String> tags) {
        this.traceContext = traceContext;
        this.tags = tags;
    }

    public TraceContext getTraceContext() {
        return this.traceContext;
    }

    /**
     * Creates a new instance of {@link SimpleLink}.
     * @param traceContext trace context
     */
    public SimpleLink(TraceContext traceContext) {
        this(traceContext, new HashMap<>());
    }

    public Map<String, String> getTags() {
        return this.tags;
    }

    @Override
    public String toString() {
        return "SimpleLink{" + "traceContext=" + traceContext + ", tags=" + tags + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleLink link = (SimpleLink) o;
        return Objects.equals(traceContext, link.traceContext) && Objects.equals(tags, link.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceContext, tags);
    }

}
