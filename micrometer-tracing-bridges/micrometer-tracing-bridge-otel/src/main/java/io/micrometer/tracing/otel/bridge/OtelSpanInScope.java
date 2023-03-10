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

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

class OtelSpanInScope implements Tracer.SpanInScope {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(OtelSpanInScope.class);

    final Scope delegate;

    final OtelSpan span;

    final io.opentelemetry.api.trace.Span otelSpan;

    final SpanContext spanContext;

    OtelSpanInScope(OtelSpan span, io.opentelemetry.api.trace.Span otelSpan) {
        this.span = span;
        this.otelSpan = otelSpan;
        this.delegate = storedContext(otelSpan);
        this.spanContext = otelSpan.getSpanContext();
    }

    private Scope storedContext(io.opentelemetry.api.trace.Span otelSpan) {
        if (this.span == null || this.span.context() == null || this.span.context().context() == null) {
            return otelSpan.makeCurrent();
        }
        Context context = this.span.context().context();
        return context.with(otelSpan).makeCurrent();
    }

    @Override
    public void close() {
        log.trace("Will close scope for trace context [{}]", this.spanContext);
        this.delegate.close();
    }

}
