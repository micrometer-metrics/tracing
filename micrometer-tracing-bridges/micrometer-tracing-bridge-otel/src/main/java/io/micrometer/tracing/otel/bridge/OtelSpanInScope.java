/*
 * Copyright 2013-2020 the original author or authors.
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

package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtelSpanInScope implements Tracer.SpanInScope {

    private static final Logger log = LoggerFactory.getLogger(OtelSpanInScope.class);

    final Scope delegate;

    final OtelSpan sleuthSpan;

    final io.opentelemetry.api.trace.Span otelSpan;

    final SpanContext spanContext;

    OtelSpanInScope(OtelSpan sleuthSpan, io.opentelemetry.api.trace.Span otelSpan) {
        this.sleuthSpan = sleuthSpan;
        this.otelSpan = otelSpan;
        this.delegate = otelSpan.makeCurrent();
        this.spanContext = otelSpan.getSpanContext();
    }

    @Override
    public void close() {
        log.trace("Will close scope for trace context [{}]", this.spanContext);
        this.delegate.close();
    }

}
