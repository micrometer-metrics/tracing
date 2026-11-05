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

import org.jspecify.annotations.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.Arrays;
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

    private final String[] tagFields;

    private final String name;

    private final BaggageEntryMetadata metadata;

    private volatile @Nullable String value;

    private final AtomicReference<@Nullable OtelTraceContext> mutatedTraceContext = new AtomicReference<>();

    /**
     * The context stored in the {@link #mutatedTraceContext} before this baggage was
     * stored in it, to restore when the scope is closed.
     */
    private final AtomicReference<@Nullable Context> previousStoredContext = new AtomicReference<>();

    private final AtomicReference<@Nullable Scope> scope = new AtomicReference<>();

    OtelBaggageInScope(OtelBaggageManager otelBaggageManager, CurrentTraceContext currentTraceContext,
            @Nullable OtelTraceContext traceContext, String[] tagFields, String name, @Nullable String value,
            BaggageEntryMetadata metadata) {
        this.otelBaggageManager = otelBaggageManager;
        this.currentTraceContext = currentTraceContext;
        this.mutatedTraceContext.set(traceContext);
        this.tagFields = tagFields;
        this.name = name;
        this.metadata = metadata;
        this.value = value;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public @Nullable String get() {
        return this.value;
    }

    @Override
    public @Nullable String get(TraceContext traceContext) {
        return this.otelBaggageManager.getValue((OtelTraceContext) traceContext, this.name);
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage set(@Nullable String value) {
        return doSet(this.currentTraceContext.context(), value);
    }

    private io.micrometer.tracing.Baggage doSet(@Nullable TraceContext context, @Nullable String value) {
        OtelTraceContext otelTraceContext = updateValue(context, value);
        if (otelTraceContext != null) {
            Context retained = otelTraceContext.context();
            otelTraceContext.updateContext(retained.with(baggageWithEntry(retained, Context.current())));
            updateAttributesForBaggage(value, Span.current());
        }
        return this;
    }

    /**
     * Prepares the value and restoration point without storing baggage, so setting and
     * activating a value together only needs one merge.
     */
    private @Nullable OtelTraceContext updateValue(@Nullable TraceContext context, @Nullable String value) {
        if (context == null) {
            return null;
        }
        OtelTraceContext otelTraceContext = (OtelTraceContext) context;
        if (!Objects.equals(mutatedTraceContext.get(), otelTraceContext)) {
            log.trace(
                    "This is unexpected - someone created baggage when mutatedTraceContext was current and now when makeCurrent() was called a new traceContext is present");
        }
        this.value = value;
        mutatedTraceContext.set(otelTraceContext);
        previousStoredContext.set(otelTraceContext.context());
        return otelTraceContext;
    }

    /**
     * The baggage of the trace context, overridden by the baggage that is current on this
     * thread, overridden by this entry.
     */
    @SuppressWarnings("ReferenceEquality")
    private Baggage baggageWithEntry(Context retainedContext, Context currentContext) {
        String value = this.value;
        Baggage retained = Baggage.fromContext(retainedContext);
        Baggage current = Baggage.fromContext(currentContext);
        BaggageBuilder builder;
        if (retained == current || retained.isEmpty()) {
            builder = current.toBuilder();
        }
        else {
            builder = retained.toBuilder();
            current
                .forEach((key, baggageEntry) -> builder.put(key, baggageEntry.getValue(), baggageEntry.getMetadata()));
        }
        return builder.put(this.name, value, this.metadata).build();
    }

    private void updateAttributesForBaggage(@Nullable String value, Span currentSpan) {
        for (String tagField : this.tagFields) {
            if (tagField.equalsIgnoreCase(this.name)) {
                currentSpan.setAttribute(this.name, value);
                return;
            }
        }
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage set(TraceContext traceContext, @Nullable String value) {
        return doSet(traceContext, value);
    }

    @Override
    public BaggageInScope makeCurrent(@Nullable String value) {
        boolean valueUpdated = updateValue(currentTraceContext.context(), value) != null;
        return makeCurrent(valueUpdated);
    }

    @Override
    public BaggageInScope makeCurrent(TraceContext traceContext, @Nullable String value) {
        updateValue(traceContext, value);
        return makeCurrent(true);
    }

    @Override
    public BaggageInScope makeCurrent() {
        return makeCurrent(false);
    }

    @SuppressWarnings({ "MustBeClosedChecker", "ReferenceEquality" })
    BaggageInScope makeCurrent(boolean tagCurrentSpan) {
        Context current = Context.current();
        OtelTraceContext otelTraceContext = this.mutatedTraceContext.get();
        Context retained = otelTraceContext != null ? otelTraceContext.context() : current;
        Baggage baggage = baggageWithEntry(retained, current);
        Context withBaggage = current.with(baggage);
        if (otelTraceContext != null) {
            // Keep the context from before a preceding set, if any.
            previousStoredContext.compareAndSet(null, retained);
            // Equal baggage does not imply equal spans or other context entries.
            otelTraceContext.updateContext(retained == current ? withBaggage : retained.with(baggage));
        }
        if (tagCurrentSpan) {
            updateAttributesForBaggage(this.value, Span.current());
        }
        this.scope.set(withBaggage.makeCurrent());
        return this;
    }

    @Override
    public void close() {
        Scope scope = this.scope.getAndSet(null);
        if (scope != null) {
            scope.close();
            OtelTraceContext traceContext = this.mutatedTraceContext.get();
            Context previous = this.previousStoredContext.getAndSet(null);
            if (traceContext != null && previous != null) {
                traceContext.updateContext(previous);
            }
        }
    }

    @Override
    public String toString() {
        return "OtelBaggageInScope{" + "tagFields=" + Arrays.toString(tagFields) + ", name=" + this.name + '}';
    }

}
