/**
 * Copyright 2023 the original author or authors.
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
package io.micrometer.tracing.handler;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;

class RevertingScope implements CurrentTraceContext.Scope {

    private final TracingObservationHandler.TracingContext tracingContext;

    private final CurrentTraceContext.Scope currentScope;

    private final CurrentTraceContext.Scope previousScope;

    private final Span previousSpan;

    RevertingScope(TracingObservationHandler.TracingContext tracingContext, CurrentTraceContext.Scope currentScope,
            CurrentTraceContext.Scope previousScope, Span previousSpan) {
        this.tracingContext = tracingContext;
        this.currentScope = currentScope;
        this.previousScope = previousScope;
        this.previousSpan = previousSpan;
    }

    @Override
    public void close() {
        this.currentScope.close();
        this.tracingContext.setSpanAndScope(this.previousSpan, this.previousScope);
    }

    @Override
    public String toString() {
        return "RevertingScope{" + "tracingContext=" + tracingContext + ", currentScope=" + currentScope
                + ", previousScope=" + previousScope + ", previousSpan=" + previousSpan + '}';
    }

}
