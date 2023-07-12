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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * OpenTelemetry implementation of a {@link CurrentTraceContext}.
 *
 * @author Marcin Grzejszczak
 * @author John Watson
 * @since 1.0.0
 */
public class OtelCurrentTraceContext implements CurrentTraceContext {

    @Override
    public TraceContext context() {
        Span currentSpan = Span.current();
        if (Span.getInvalid().equals(currentSpan)) {
            return null;
        }
        if (currentSpan instanceof SpanFromSpanContext) {
            return new OtelTraceContext((SpanFromSpanContext) currentSpan);
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
    public Scope newScope(TraceContext context) {
        OtelTraceContext otelTraceContext = (OtelTraceContext) context;
        if (otelTraceContext == null) {
            return new WrappedScope(io.opentelemetry.context.Scope.noop());
        }
        Context current = Context.current();
        Context old = otelTraceContext.context();
        // Check if there's a span in the static OTel context
        Span spanFromCurrentCtx = Span.fromContext(current);
        // Check if there's a span in the ctx attached to TraceContext
        Span spanFromCtxOnNewSpan = Span.fromContext(otelTraceContext.context());
        SpanContext spanContext = otelTraceContext.delegate;
        boolean sameSpan = spanFromCurrentCtx.getSpanContext().equals(spanFromCtxOnNewSpan.getSpanContext())
                && spanFromCurrentCtx.getSpanContext().equals(spanContext);
        SpanFromSpanContext fromContext = new SpanFromSpanContext(((OtelTraceContext) context).span, spanContext,
                otelTraceContext);
        Baggage currentBaggage = Baggage.fromContext(current);
        Baggage oldBaggage = Baggage.fromContext(old);
        boolean sameBaggage = sameBaggage(currentBaggage, oldBaggage);
        if (sameSpan && sameBaggage) {
            return new WrappedScope(io.opentelemetry.context.Scope.noop());
        }
        Baggage updatedBaggage = mergeBaggage(currentBaggage, oldBaggage);
        Context newContext = old.with(fromContext).with(updatedBaggage);
        io.opentelemetry.context.Scope attach = newContext.makeCurrent();
        otelTraceContext.updateContext(newContext);
        return new WrappedScope(attach, otelTraceContext, old);
    }

    private static Baggage mergeBaggage(Baggage currentBaggage, Baggage oldBaggage) {
        BaggageBuilder baggageBuilder = currentBaggage.toBuilder();
        oldBaggage.forEach(
                (key, baggageEntry) -> baggageBuilder.put(key, baggageEntry.getValue(), baggageEntry.getMetadata()));
        return baggageBuilder.build();
    }

    private boolean sameBaggage(Baggage currentBaggage, Baggage oldBaggage) {
        return currentBaggage.equals(oldBaggage);
    }

    @Override
    public Scope maybeScope(TraceContext context) {
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

        final OtelTraceContext currentOtelTraceContext;

        final Context oldContext;

        WrappedScope(io.opentelemetry.context.Scope scope) {
            this(scope, null, null);
        }

        WrappedScope(io.opentelemetry.context.Scope scope, OtelTraceContext currentOtelTraceContext,
                Context oldContext) {
            this.scope = scope;
            this.currentOtelTraceContext = currentOtelTraceContext;
            this.oldContext = oldContext;
        }

        @Override
        public void close() {
            if (this.currentOtelTraceContext != null) {
                currentOtelTraceContext.updateContext(oldContext);
            }
            this.scope.close();
        }

    }

}
