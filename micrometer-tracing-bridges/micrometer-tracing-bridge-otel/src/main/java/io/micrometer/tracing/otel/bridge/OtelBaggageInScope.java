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

import org.jspecify.annotations.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenTelemetry implementation of a {@link BaggageInScope}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class OtelBaggageInScope implements io.micrometer.tracing.Baggage, BaggageInScope {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(OtelBaggageInScope.class);

    private final OtelBaggageManager otelBaggageManager;

    private final CurrentTraceContext currentTraceContext;

    private final List<String> tagFields;

    private final AtomicReference<Entry> entry = new AtomicReference<>();

    private final AtomicReference<Context> contextWithoutBaggage = new AtomicReference<>(null);

    private final AtomicReference<OtelTraceContext> mutatedTraceContext = new AtomicReference<>(null);

    private final AtomicReference<Context> contextWithBaggage = new AtomicReference<>(null);

    private final AtomicReference<Scope> scope = new AtomicReference<>();

    OtelBaggageInScope(OtelBaggageManager otelBaggageManager, CurrentTraceContext currentTraceContext,
            List<String> tagFields, Entry entry) {
        this.otelBaggageManager = otelBaggageManager;
        this.currentTraceContext = currentTraceContext;
        this.mutatedTraceContext.set((OtelTraceContext) currentTraceContext.context());
        this.tagFields = tagFields;
        this.entry.set(entry);
        if (entry.value != null) {
            updateAttributesForBaggage(entry.value, Span.current());
        }
    }

    OtelBaggageInScope(OtelBaggageManager otelBaggageManager, CurrentTraceContext currentTraceContext,
            OtelTraceContext traceContext, List<String> tagFields, Entry entry) {
        this.otelBaggageManager = otelBaggageManager;
        this.currentTraceContext = currentTraceContext;
        this.mutatedTraceContext.set(traceContext);
        this.tagFields = tagFields;
        this.entry.set(entry);
    }

    @Override
    public String name() {
        return entry().getKey();
    }

    @Override
    public @Nullable String get() {
        if (entry.get() != null) {
            return entry.get().value;
        }
        return this.otelBaggageManager.currentBaggage().getEntryValue(entry().getKey());
    }

    @Override
    public @Nullable String get(TraceContext traceContext) {
        Entry entry = this.otelBaggageManager.getEntry((OtelTraceContext) traceContext, entry().getKey());
        if (entry == null) {
            return null;
        }
        return entry.getValue();
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage set(String value) {
        return doSet(this.currentTraceContext.context(), value);
    }

    private io.micrometer.tracing.Baggage doSet(@Nullable TraceContext context, String value) {
        if (context == null) {
            return this;
        }
        Context current = Context.current();
        Span currentSpan = Span.current();
        io.opentelemetry.api.baggage.Baggage baggage;
        OtelTraceContext ctx = (OtelTraceContext) context;
        if (!Objects.equals(mutatedTraceContext.get(), ctx)) {
            log.trace(
                    "This is unexpected - someone created baggage when mutatedTraceContext was current and now when makeCurrent() was called a new traceContext is present");
        }
        mutatedTraceContext.set(ctx);
        Context storedCtx = ctx.context();
        contextWithoutBaggage.set(storedCtx);
        Baggage fromContext = Baggage.fromContext(storedCtx);

        BaggageBuilder newBaggageBuilder = fromContext.toBuilder();
        Baggage.current()
            .forEach((key, baggageEntry) -> newBaggageBuilder.put(key, baggageEntry.getValue(),
                    baggageEntry.getMetadata()));

        baggage = newBaggageBuilder.put(entry().getKey(), value, entry().getMetadata()).build();
        current = current.with(baggage);
        Context withBaggage = current.with(baggage);
        ctx.updateContext(withBaggage);
        contextWithBaggage.set(withBaggage);
        updateAttributesForBaggage(value, currentSpan);
        Entry previous = entry();
        this.entry.set(new Entry(previous.getKey(), value, previous.getMetadata()));
        return this;
    }

    private void updateAttributesForBaggage(String value, Span currentSpan) {
        if (this.tagFields.stream().anyMatch(s -> s.equalsIgnoreCase(entry().getKey()))) {
            currentSpan.setAttribute(entry().getKey(), value);
        }
    }

    private Entry entry() {
        return this.entry.get();
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage set(TraceContext traceContext, String value) {
        return doSet(traceContext, value);
    }

    @Override
    public BaggageInScope makeCurrent(String value) {
        return doSet(currentTraceContext.context(), value).makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent(TraceContext traceContext, String value) {
        return doSet(traceContext, value).makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent() {
        Entry storedEntry = entry();
        Context context = contextWithBaggage.get();
        if (context == null) {
            context = Context.current();
        }
        Baggage baggage = Baggage.fromContext(context)
            .toBuilder()
            .put(storedEntry.getKey(), storedEntry.getValue(), storedEntry.getMetadata())
            .build();
        Context updated = context.with(baggage);
        OtelTraceContext otelTraceContext = this.mutatedTraceContext.get();
        if (otelTraceContext != null) {
            otelTraceContext.updateContext(updated);
        }
        Scope currentScope = updated.makeCurrent();
        this.scope.set(currentScope);
        return this;
    }

    @Override
    public void close() {
        Scope scope = this.scope.get();
        if (scope != null) {
            this.scope.set(null);
            scope.close();
            OtelTraceContext traceContext = this.mutatedTraceContext.get();
            if (traceContext != null) {
                traceContext.updateContext(this.contextWithoutBaggage.get());
            }
        }
    }

    @Override
    public String toString() {
        return "OtelBaggageInScope{" + "tagFields=" + tagFields + ", entry=" + entry + '}';
    }

}
