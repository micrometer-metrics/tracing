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

import brave.Span;
import brave.Tags;
import brave.baggage.BaggageField;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.TraceContext;

import java.util.List;

/**
 * Brave implementation of a {@link BaggageInScope}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class BraveBaggageInScope implements Baggage, BaggageInScope {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BraveBaggageInScope.class);

    private final BaggageField delegate;

    private final String previousBaggage;

    private final List<String> tagFields;

    @Nullable
    private brave.propagation.TraceContext traceContext;

    @Nullable
    private final Span span;

    BraveBaggageInScope(BaggageField delegate, @Nullable brave.propagation.TraceContext traceContext,
            @Nullable Span span, List<String> tagFields) {
        this.delegate = delegate;
        this.traceContext = traceContext;
        this.previousBaggage = delegate.getValue();
        this.tagFields = tagFields;
        this.span = span;
    }

    @Override
    public String name() {
        return this.delegate.name();
    }

    @Override
    public String get() {
        return this.traceContext != null ? this.delegate.getValue(traceContext) : this.delegate.getValue();
    }

    @Override
    public String get(TraceContext traceContext) {
        return this.delegate.getValue(BraveTraceContext.toBrave(traceContext));
    }

    @Override
    @Deprecated
    public Baggage set(String value) {
        if (this.traceContext != null) {
            this.delegate.updateValue(this.traceContext, value);
        }
        else {
            this.delegate.updateValue(value);
        }
        tagSpanIfOnTagList();
        return this;
    }

    private void tagSpanIfOnTagList() {
        if (this.span != null) {
            this.tagFields.stream()
                .filter(s -> s.equalsIgnoreCase(name()))
                .findFirst()
                .ifPresent(s -> Tags.BAGGAGE_FIELD.tag(this.delegate, span));
        }
    }

    @Override
    @Deprecated
    public Baggage set(TraceContext traceContext, String value) {
        brave.propagation.TraceContext braveContext = updateBraveTraceContext(traceContext);
        this.delegate.updateValue(braveContext, value);
        tagSpanIfOnTagList();
        return this;
    }

    private brave.propagation.TraceContext updateBraveTraceContext(TraceContext traceContext) {
        brave.propagation.TraceContext braveContext = BraveTraceContext.toBrave(traceContext);
        if (this.traceContext != braveContext) {
            logger.debug(
                    "Create on baggage was called on <{}> but now you want to set baggage on <{}>. That's unexpected.",
                    this.traceContext, traceContext);
            this.traceContext = braveContext;
        }
        return braveContext;
    }

    @Override
    public BaggageInScope makeCurrent() {
        return this;
    }

    @Override
    public BaggageInScope makeCurrent(String value) {
        if (this.traceContext != null) {
            this.delegate.updateValue(this.traceContext, value);
        }
        else {
            this.delegate.updateValue(value);
        }
        tagSpanIfOnTagList();
        return makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent(TraceContext traceContext, String value) {
        brave.propagation.TraceContext braveContext = updateBraveTraceContext(traceContext);
        this.delegate.updateValue(braveContext, value);
        tagSpanIfOnTagList();
        return makeCurrent();
    }

    @Override
    public void close() {
        if (this.traceContext != null) {
            this.delegate.updateValue(this.traceContext, this.previousBaggage);
        }
    }

}
