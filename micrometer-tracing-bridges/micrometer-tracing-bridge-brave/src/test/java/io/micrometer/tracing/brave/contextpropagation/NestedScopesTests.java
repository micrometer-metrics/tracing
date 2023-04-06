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
package io.micrometer.tracing.brave.contextpropagation;

import brave.Tracing;
import brave.test.TestSpanHandler;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.CurrentTraceContext.Scope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NestedScopesTests {

    TestSpanHandler testSpanHandler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(testSpanHandler).build();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
            new BraveBaggageManager());

    DefaultTracingObservationHandler handler = new DefaultTracingObservationHandler(tracer);

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(handler);
    }

    @Test
    void nestedScopesShouldBeMaintained() {

        // given
        Observation obs1 = Observation.start("parent", observationRegistry);
        Observation obs2 = Observation.start("foo", observationRegistry);

        thenCurrentObservationIsNull();

        try (Observation.Scope obs1Scope1 = obs1.openScope()) {
            Span spanObs1Scope1 = tracer.currentSpan();
            Scope inScopeObs1Scope1 = getCurrentScope();
            try (Observation.Scope obs1Scope2 = obs1.openScope()) {
                Span spanObs1Scope2 = tracer.currentSpan();
                Scope inScopeObs1Scope2 = getCurrentScope();
                try (Observation.Scope obs2Scope1 = obs2.openScope()) {
                    Span spanObs2Scope1 = tracer.currentSpan();
                    Scope inScopeObs2Scope1 = getCurrentScope();
                    try (Observation.Scope obs2Scope2 = obs2.openScope()) {
                        Span spanObs2Scope2 = tracer.currentSpan();
                        Scope inScopeObs2Scope2 = getCurrentScope();
                        try (Observation.Scope obs1Scope3 = obs1.openScope()) {
                            Span spanObs1Scope3 = tracer.currentSpan();
                            Scope inScopeObs1Scope3 = getCurrentScope();
                            try (Observation.Scope obs1Scope4 = obs1.openScope()) {
                                //
                            }
                            thenCurrentSpanEqualTo(spanObs1Scope3);
                            thenCurrentScopeSameAs(inScopeObs1Scope3);
                        }
                        thenCurrentSpanEqualTo(spanObs2Scope2);
                        thenCurrentScopeSameAs(inScopeObs2Scope2);
                    }
                    thenCurrentSpanEqualTo(spanObs2Scope1);
                    thenCurrentScopeSameAs(inScopeObs2Scope1);
                }
                thenCurrentSpanEqualTo(spanObs1Scope2);
                thenCurrentScopeSameAs(inScopeObs1Scope2);
            }
            thenCurrentSpanEqualTo(spanObs1Scope1);
            thenCurrentScopeSameAs(inScopeObs1Scope1);
        }

        thenCurrentSpanEqualTo(null);
        thenCurrentObservationIsNull();

        obs2.stop();
        obs1.stop();
    }

    @Test
    void nestedScopesShouldBeMaintainedWithContextPropagationApi() {

        // given
        Observation obs1 = Observation.start("obs1", observationRegistry);
        ContextSnapshot snapshot1 = null;
        try (Observation.Scope scope = obs1.openScope()) {
            snapshot1 = ContextSnapshot.captureAll();
        }

        Observation obs2 = Observation.start("obs2", observationRegistry);
        ContextSnapshot snapshot2 = null;
        try (Observation.Scope scope = obs2.openScope()) {
            snapshot2 = ContextSnapshot.captureAll();
        }

        thenCurrentObservationIsNull();

        try (ContextSnapshot.Scope obs1Scope1 = snapshot1.setThreadLocals()) {
            Span spanObs1Scope1 = tracer.currentSpan();
            try (ContextSnapshot.Scope obs1Scope2 = snapshot1.setThreadLocals()) {
                Span spanObs1Scope2 = tracer.currentSpan();
                try (ContextSnapshot.Scope obs2Scope1 = snapshot2.setThreadLocals()) {
                    Span spanObs2Scope1 = tracer.currentSpan();
                    try (ContextSnapshot.Scope obs2Scope2 = snapshot2.setThreadLocals()) {
                        Span spanObs2Scope2 = tracer.currentSpan();
                        try (ContextSnapshot.Scope obs1Scope3 = snapshot1.setThreadLocals()) {
                            Span spanObs1Scope3 = tracer.currentSpan();
                            try (ContextSnapshot.Scope obs1Scope4 = snapshot1.setThreadLocals()) {

                            }
                            thenCurrentSpanEqualTo(spanObs1Scope3);
                        }
                        thenCurrentSpanEqualTo(spanObs2Scope2);
                    }
                    thenCurrentSpanEqualTo(spanObs2Scope1);
                }
                thenCurrentSpanEqualTo(spanObs1Scope2);
            }
            thenCurrentSpanEqualTo(spanObs1Scope1);
        }

        thenCurrentSpanEqualTo(null);
        thenCurrentObservationIsNull();

        obs2.stop();
        obs1.stop();
    }

    private void thenCurrentScopeSameAs(Scope scope) {
        BDDAssertions.then(getCurrentScope()).isSameAs(scope);
    }

    private void thenCurrentSpanEqualTo(Span span) {
        BDDAssertions.then(tracer.currentSpan()).isEqualTo(span);
    }

    private Scope getCurrentScope() {
        Observation observation = observationRegistry.getCurrentObservation();
        TracingObservationHandler.TracingContext tracingContext = observation.getContextView()
            .get(TracingObservationHandler.TracingContext.class);
        return tracingContext.getScope();
    }

    private void thenCurrentObservationIsNull() {
        BDDAssertions.then(observationRegistry.getCurrentObservation()).isNull();
    }

}
