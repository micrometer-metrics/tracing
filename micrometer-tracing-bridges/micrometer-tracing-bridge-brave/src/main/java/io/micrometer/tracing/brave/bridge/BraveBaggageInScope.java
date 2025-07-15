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
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.TraceContext;
import org.jspecify.annotations.Nullable;

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

    private final @Nullable String previousBaggage;

    private final List<String> tagFields;

    // Null TC would happen pretty much in exceptional cases (there was no span in scope)
    // but someone wanted to set baggage
    private brave.propagation.@Nullable TraceContext traceContext;

    private final @Nullable Span span;

    BraveBaggageInScope(BaggageField delegate, brave.propagation.@Nullable TraceContext traceContext,
            @Nullable Span span, List<String> tagFields) {
        this.delegate = delegate;
        this.traceContext = traceContext;
        this.previousBaggage = traceContext != null ? delegate.getValue(traceContext) : delegate.getValue();
        this.tagFields = tagFields;
        this.span = span;
    }

    @Override
    public String name() {
        return this.delegate.name();
    }

    @Override
    public @Nullable String get() {
        return this.traceContext != null ? this.delegate.getValue(traceContext) : this.delegate.getValue();
    }

    @Override
    public @Nullable String get(TraceContext traceContext) {
        return this.delegate.getValue(BraveTraceContext.toBrave(traceContext));
    }

    @Override
    @Deprecated
    public Baggage set(@Nullable String value) {
        if (this.traceContext != null) {
            boolean success = this.delegate.updateValue(this.traceContext, value);
            if (logger.isTraceEnabled()) {
                logger.trace("Managed to update the baggage on set [" + success + "]. Provided value [" + value + "]");
            }
        }
        else {
            boolean success = this.delegate.updateValue(value);
            if (logger.isTraceEnabled()) {
                logger.trace("Managed to update the baggage on set [" + success + "]. Provided value [" + value + "]");
            }
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
    public Baggage set(TraceContext traceContext, @Nullable String value) {
        brave.propagation.TraceContext braveContext = updateBraveTraceContext(traceContext);
        boolean success = this.delegate.updateValue(braveContext, value);
        if (logger.isTraceEnabled()) {
            logger.trace("Managed to update the baggage on set [" + success + "]. Provided value [" + value
                    + "], trace context [" + traceContext + "]");
        }
        tagSpanIfOnTagList();
        return this;
    }

    private brave.propagation.@Nullable TraceContext updateBraveTraceContext(TraceContext traceContext) {
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
    public BaggageInScope makeCurrent(@Nullable String value) {
        if (this.traceContext != null) {
            boolean success = this.delegate.updateValue(this.traceContext, value);
            if (logger.isTraceEnabled()) {
                logger.trace("Managed to update the baggage on make current [" + success + "]");
            }
        }
        else {
            boolean success = this.delegate.updateValue(value);
            if (logger.isTraceEnabled()) {
                logger.trace("Managed to update the baggage on make current [" + success + "]");
            }
        }
        tagSpanIfOnTagList();
        return makeCurrent();
    }

    @Override
    public BaggageInScope makeCurrent(TraceContext traceContext, @Nullable String value) {
        brave.propagation.TraceContext braveContext = updateBraveTraceContext(traceContext);
        boolean success = this.delegate.updateValue(braveContext, value);
        if (logger.isTraceEnabled()) {
            logger.trace("Managed to update the baggage on close [" + success + "]. Provided value [" + value
                    + "], trace context [" + traceContext + "]");
        }
        tagSpanIfOnTagList();
        return makeCurrent();
    }

    @Override
    public void close() {
        if (this.traceContext != null) {
            boolean success = this.delegate.updateValue(this.traceContext, this.previousBaggage);
            if (logger.isTraceEnabled()) {
                logger.trace("Managed to update the baggage on close [" + success + "]");
            }
        }
    }

    @Override
    public String toString() {
        return "BraveBaggageInScope{" + "delegate=" + delegate + ", previousBaggage='" + previousBaggage + '\''
                + ", tagFields=" + tagFields + ", traceContext=" + traceContext + ", span=" + span + '}';
    }

}
