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

import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadableSpan;

/**
 * OpenTelemetry implementation of a {@link ScopedSpan}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class OtelScopedSpan implements ScopedSpan {

    final Span span;

    final Scope scope;

    OtelScopedSpan(Span span, Scope scope) {
        this.span = span;
        this.scope = scope;
    }

    @Override
    public boolean isNoop() {
        return !this.span.isRecording();
    }

    @Override
    public TraceContext context() {
        return new OtelTraceContext(this.span);
    }

    @Override
    public ScopedSpan name(String name) {
        this.span.updateName(name);
        return this;
    }

    @Override
    public ScopedSpan tag(String key, String value) {
        this.span.setAttribute(key, value);
        return this;
    }

    @Override
    public ScopedSpan event(String value) {
        this.span.addEvent(value);
        return this;
    }

    @Override
    public ScopedSpan error(Throwable throwable) {
        this.span.recordException(throwable);
        this.span.setStatus(StatusCode.ERROR, throwable.getMessage());
        return this;
    }

    @Override
    public void end() {
        if (this.span instanceof ReadableSpan) {
            StatusCode status = ((ReadableSpan) this.span).toSpanData().getStatus().getStatusCode();
            if (status == StatusCode.UNSET) {
                this.span.setStatus(StatusCode.OK);
            }
        }

        this.scope.close();
        this.span.end();
    }

}
