/**
 * Copyright 2024 the original author or authors.
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

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation.ContextView;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.CurrentTraceContext.Scope;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;

import java.util.*;

class RevertingScope implements CurrentTraceContext.Scope {

    private final TracingObservationHandler.TracingContext tracingContext;

    private final CurrentTraceContext.Scope currentScope;

    private final CurrentTraceContext.Scope previousScope;

    RevertingScope(TracingContext tracingContext, Scope currentScope, @Nullable Scope previousScope) {
        this.tracingContext = tracingContext;
        this.currentScope = currentScope;
        this.previousScope = previousScope;
    }

    @Override
    public void close() {
        this.currentScope.close();
        this.tracingContext.setScope(this.previousScope);
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
                && Objects.equals(previousScope, that.previousScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tracingContext, currentScope, previousScope);
    }

    static RevertingScope maybeWithBaggage(Tracer tracer, TracingContext tracingContext,
            @Nullable TraceContext newContext, RevertingScope revertingScopeForSpan,
            Scope previousScopeOnThisObservation) {
        RevertingScope revertingScope = revertingScopeForSpan;
        ContextView context = tracingContext.getContext();
        if (context == null) {
            return revertingScope;
        }
        Collection<KeyValue> baggageKeyValues = matchingBaggageKeyValues(tracer, context);
        if (baggageKeyValues.isEmpty()) {
            return revertingScope;
        }
        ArrayDeque<BaggageInScope> scopes = startBaggageScopes(tracer, newContext, baggageKeyValues);
        return new RevertingScope(tracingContext, () -> {
            for (BaggageInScope scope : scopes) {
                scope.close();
            }
            revertingScope.close();
        }, previousScopeOnThisObservation);
    }

    private static ArrayDeque<BaggageInScope> startBaggageScopes(Tracer tracer, TraceContext newContext,
            Collection<KeyValue> baggageKeyValues) {
        ArrayDeque<BaggageInScope> scopes = new ArrayDeque<>();
        for (KeyValue keyValue : baggageKeyValues) {
            if (newContext != null) {
                scopes.addFirst(tracer.createBaggageInScope(newContext, keyValue.getKey(), keyValue.getValue()));
            }
            else {
                scopes.addFirst(tracer.createBaggageInScope(keyValue.getKey(), keyValue.getValue()));
            }
        }
        return scopes;
    }

    private static Collection<KeyValue> matchingBaggageKeyValues(Tracer tracer, ContextView context) {
        Set<String> lowerCaseRemoteFields = new HashSet<>();
        for (String remoteField : tracer.getBaggageFields()) {
            lowerCaseRemoteFields.add(remoteField.toLowerCase());
        }
        Collection<KeyValue> baggageKeyValues = new ArrayList<>();
        for (KeyValue keyValue : context.getAllKeyValues()) {
            if (lowerCaseRemoteFields.contains(keyValue.getKey().toLowerCase())) {
                baggageKeyValues.add(keyValue);
            }
        }
        return baggageKeyValues;
    }

}
