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
import io.micrometer.tracing.CurrentTraceContext.Scope;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;

import java.util.Map;
import java.util.Objects;

class RevertingScope implements CurrentTraceContext.Scope {

    private final TracingObservationHandler.TracingContext tracingContext;

    private final CurrentTraceContext.Scope currentScope;

    private final CurrentTraceContext.Scope previousScope;

    private final Map<String, String> previousBaggage;

    RevertingScope(TracingContext tracingContext, Scope currentScope, Scope previousScope,
            Map<String, String> previousBaggage) {
        this.tracingContext = tracingContext;
        this.currentScope = currentScope;
        this.previousScope = previousScope;
        this.previousBaggage = previousBaggage;
    }

    @Override
    public void close() {
        this.currentScope.close();
        this.tracingContext.setScope(this.previousScope);
        this.tracingContext.setBaggage(this.previousBaggage);
    }

    @Override
    public String toString() {
        return "RevertingScope{" + "tracingContext=" + tracingContext + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RevertingScope that = (RevertingScope) o;
        return Objects.equals(tracingContext, that.tracingContext) && Objects.equals(currentScope, that.currentScope)
                && Objects.equals(previousScope, that.previousScope)
                && Objects.equals(previousBaggage, that.previousBaggage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tracingContext, currentScope, previousScope, previousBaggage);
    }

}
