/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.tracing.test.simple;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of a current trace context.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleCurrentTraceContext implements CurrentTraceContext {

    private TraceContext traceContext;

    private boolean scopeClosed;

    /**
     * @param simpleTracer simple tracer
     * @return simple current trace context with the current context
     */
    public static SimpleCurrentTraceContext withTracer(SimpleTracer simpleTracer) {
        return new SimpleCurrentTraceContext() {
            @Override
            public TraceContext context() {
                return simpleTracer.currentSpan() != null ? simpleTracer.currentSpan().context() : null;
            }
        };
    }

    @Override
    public TraceContext context() {
        return this.getTraceContext();
    }

    @Override
    public Scope newScope(TraceContext context) {
        this.traceContext = context;
        return () -> scopeClosed = true;
    }

    @Override
    public Scope maybeScope(TraceContext context) {
        this.traceContext = context;
        return () -> scopeClosed = true;
    }

    @Override
    public <C> Callable<C> wrap(Callable<C> task) {
        return task;
    }

    @Override
    public Runnable wrap(Runnable task) {
        return task;
    }

    @Override
    public Executor wrap(Executor delegate) {
        return delegate;
    }

    @Override
    public ExecutorService wrap(ExecutorService delegate) {
        return delegate;
    }

    /**
     * Current trace context.
     * @return current trace context
     */
    public TraceContext getTraceContext() {
        return this.traceContext;
    }

    /**
     * Was scope closed?
     * @return {@code true} when scope closed
     */
    public boolean isScopeClosed() {
        return this.scopeClosed;
    }
}
