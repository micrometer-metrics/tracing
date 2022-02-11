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

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of a current trace context.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleCurrentTraceContext implements CurrentTraceContext {

    private final SimpleTracer simpleTracer;

    public SimpleCurrentTraceContext(SimpleTracer simpleTracer) {
        this.simpleTracer = simpleTracer;
    }

    @Override
    public TraceContext context() {
        Span span = this.simpleTracer.currentSpan();
        if (span != null) {
            return span.context();
        }
        return null;
    }

    @Override
    public Scope newScope(TraceContext context) {
        Span span = Objects.requireNonNull(SimpleSpanAndScope.traceContextsToSpans.get(context), "You must create a span with this context before");
        SimpleSpanInScope inScope = this.simpleTracer.withSpan(span);
        return inScope::close;
    }

    @Override
    public Scope maybeScope(TraceContext context) {
        Span span = Objects.requireNonNull(SimpleSpanAndScope.traceContextsToSpans.get(context), "You must create a span with this context before");
        if (this.simpleTracer.currentSpan() == span) {
            return () -> {

            };
        }
        return newScope(span.context());
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
}
