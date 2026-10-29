/*
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
package io.micrometer.tracing.otel.bridge;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * OpenTelemetry implementation of a {@link CurrentTraceContext}.
 *
 * @author Marcin Grzejszczak
 * @author John Watson
 * @since 1.0.0
 */
public class OtelCurrentTraceContext implements CurrentTraceContext {

    private static final String TRACING_OTEL_CONTEXT_KEY = "otelTraceContext";

    static final ContextKey<OtelTraceContext> OTEL_CONTEXT_KEY = ContextKey.named(TRACING_OTEL_CONTEXT_KEY);

    @Override
    public @Nullable TraceContext context() {
        OtelTraceContext otelTraceContext = Context.current().get(OTEL_CONTEXT_KEY);
        if (otelTraceContext != null) {
            return otelTraceContext;
        }
        Span currentSpan = Span.current();
        if (Span.getInvalid().equals(currentSpan)) {
            return null;
        }
        return new OtelTraceContext(currentSpan);
    }

    /**
     * Since OpenTelemetry works on statics, and we would like to pass the tracing
     * information on the {@link TraceContext} we are checking what we have currently in
     * ThreadLocal and what was passed on {@link TraceContext}.
     * @param context span to place into scope or {@code null} to clear the scope
     * @return scope that always must be closed
     */
    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public Scope newScope(@Nullable TraceContext context) {
        OtelTraceContext otelTraceContext = (OtelTraceContext) context;
        if (otelTraceContext == null) {
            return new WrappedScope(io.opentelemetry.context.Scope.noop());
        }
        Context current = Context.current();
        // The stored context can be replaced by OtelBaggageInScope with one that does not
        // contain this span, so store it again (no-op if it is already there)
        Context spanContext = otelTraceContext.storeInContext(otelTraceContext.context());
        Span spanFromCurrentCtx = Span.fromContext(current);
        boolean sameSpan = spanFromCurrentCtx.getSpanContext().equals(otelTraceContext.delegate);
        Baggage currentBaggage = Baggage.fromContext(current);
        Baggage spanBaggage = Baggage.fromContext(spanContext);
        if (sameSpan && currentBaggage.equals(spanBaggage)) {
            return new WrappedScope(io.opentelemetry.context.Scope.noop());
        }
        Baggage updatedBaggage = OtelBaggageManager.merge(currentBaggage, spanBaggage);
        Context newContext = spanContext.with(updatedBaggage);
        io.opentelemetry.context.Scope newScope = newContext.makeCurrent();
        return new WrappedScope(newScope);
    }

    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public Scope maybeScope(@Nullable TraceContext context) {
        if (context == null) {
            io.opentelemetry.context.Scope scope = Context.root().makeCurrent();
            return new WrappedScope(scope);
        }
        return newScope(context);
    }

    @Override
    public <C> Callable<C> wrap(Callable<C> task) {
        return Context.current().wrap(task);
    }

    @Override
    public Runnable wrap(Runnable task) {
        return Context.current().wrap(task);
    }

    @Override
    public Executor wrap(Executor delegate) {
        return Context.current().wrap(delegate);
    }

    @Override
    public ExecutorService wrap(ExecutorService delegate) {
        return Context.current().wrap(delegate);
    }

    static class WrappedScope implements Scope {

        final io.opentelemetry.context.Scope scope;

        WrappedScope(io.opentelemetry.context.Scope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            this.scope.close();
        }

    }

}
