/**
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;

import java.util.Objects;

/**
 * A test implementation of a baggage/baggage in scope.
 *
 * @author Marcin Grzejszczak
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class SimpleBaggageInScope implements Baggage, BaggageInScope {

    private final String name;

    private volatile TraceContext traceContext = null;

    private volatile String value = null;

    private volatile boolean inScope = false;

    private volatile boolean closed = false;

    private CurrentTraceContext currentTraceContext = CurrentTraceContext.NOOP;

    /**
     * Creates a new instance of {@link SimpleBaggageInScope}.
     * @param name name
     * @deprecated use {@link SimpleBaggageInScope(CurrentTraceContext, String,
     * TraceContext)}
     */
    @Deprecated
    public SimpleBaggageInScope(String name) {
        this.name = name;
    }

    /**
     * Creates a new instance of {@link SimpleBaggageInScope}.
     * @param currentTraceContext current trace context
     * @param name name
     * @param traceContext trace context
     */
    public SimpleBaggageInScope(CurrentTraceContext currentTraceContext, String name, TraceContext traceContext) {
        this.currentTraceContext = currentTraceContext;
        this.name = name;
        this.traceContext = traceContext;
    }

    /**
     * Creates a new instance of {@link SimpleBaggageInScope}.
     * @param currentTraceContext current trace context
     * @param name name
     */
    public SimpleBaggageInScope(CurrentTraceContext currentTraceContext, String name) {
        this.currentTraceContext = currentTraceContext;
        this.name = name;
        this.traceContext = currentTraceContext.context();
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String get() {
        return get(currentTraceContext.context());
    }

    @Override
    public String get(TraceContext traceContext) {
        if (!this.inScope) {
            return null;
        }
        if (this.traceContext == traceContext) {
            return this.value;
        }
        return null;
    }

    @Override
    @Deprecated
    public Baggage set(String value) {
        this.value = value;
        this.traceContext = currentTraceContext.context();
        return this;
    }

    @Override
    @Deprecated
    public Baggage set(TraceContext traceContext, String value) {
        this.value = value;
        this.traceContext = traceContext;
        return this;
    }

    @Override
    public BaggageInScope makeCurrent(String value) {
        this.value = value;
        return makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent(TraceContext traceContext, String value) {
        this.value = value;
        this.traceContext = traceContext;
        return makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent() {
        this.inScope = true;
        return this;
    }

    @Override
    public void close() {
        this.inScope = false;
        this.closed = true;
    }

    /**
     * Checks if baggage is in scope.
     * @return {@code true} when baggage in scope
     */
    public boolean isInScope() {
        return this.inScope;
    }

    /**
     * Checks if baggage scope was closed.
     * @return {@code true} when baggage was closed
     */
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleBaggageInScope baggage = (SimpleBaggageInScope) o;
        return Objects.equals(this.name, baggage.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

}
