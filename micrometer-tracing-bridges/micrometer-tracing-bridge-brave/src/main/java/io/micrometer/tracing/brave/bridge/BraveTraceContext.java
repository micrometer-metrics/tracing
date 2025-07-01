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
package io.micrometer.tracing.brave.bridge;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.TraceContext;

/**
 * Brave implementation of a {@link TraceContext}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveTraceContext implements TraceContext {

    final brave.propagation.TraceContext traceContext;

    /**
     * Creates a new instance of {@link BraveTraceContext}.
     * @param traceContext Brave {@link TraceContext}
     */
    public BraveTraceContext(brave.propagation.TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    /**
     * Converts from Tracing to Brave.
     * @param traceContext Tracing version
     * @return Brave version
     */
    public static brave.propagation.@Nullable TraceContext toBrave(@Nullable TraceContext traceContext) {
        if (traceContext == null) {
            return null;
        }
        return ((BraveTraceContext) traceContext).traceContext;
    }

    /**
     * Converts from Brave to Tracing.
     * @param traceContext Brave version
     * @return Tracing version
     */
    public static TraceContext fromBrave(brave.propagation.TraceContext traceContext) {
        return new BraveTraceContext(traceContext);
    }

    @Override
    public String traceId() {
        return this.traceContext.traceIdString();
    }

    @Override
    @Nullable public String parentId() {
        return this.traceContext.parentIdString();
    }

    @Override
    public String spanId() {
        return this.traceContext.spanIdString();
    }

    @Override
    @Nullable public Boolean sampled() {
        return this.traceContext.sampled();
    }

    @Override
    public String toString() {
        return this.traceContext != null ? this.traceContext.toString() : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BraveTraceContext that = (BraveTraceContext) o;
        return Objects.equals(this.traceContext, that.traceContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.traceContext);
    }

}
