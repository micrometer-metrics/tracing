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
package io.micrometer.tracing.handler;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.InOrder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class TracingObservationHandlerTests {

    Tracer tracer = mock(Tracer.class);

    CurrentTraceContext currentTraceContext = mock(CurrentTraceContext.class);

    @BeforeEach
    void setup() {
        given(tracer.currentTraceContext()).willReturn(currentTraceContext);
    }

    @Test
    void iseShouldBeCalledWhenNoSpanInContext() {
        TracingObservationHandler<Observation.Context> handler = () -> null;

        thenThrownBy(() -> handler.onEvent(() -> "", new Observation.Context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Span wasn't started");
    }

    @Test
    void spanShouldBeClearedOnScopeReset() {
        TracingObservationHandler<Observation.Context> handler = () -> tracer;
        Observation.Context context = new Observation.Context();
        TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
        CurrentTraceContext.Scope scope1 = mock(CurrentTraceContext.Scope.class);
        CurrentTraceContext.Scope scope2 = mock(CurrentTraceContext.Scope.class);
        RevertingScope revertingScope1 = new RevertingScope(tracingContext, scope1, null, Collections.emptyMap());
        RevertingScope revertingScope2 = new RevertingScope(tracingContext, scope2, revertingScope1,
                Collections.emptyMap());
        tracingContext.setScope(revertingScope2);
        context.put(TracingObservationHandler.TracingContext.class, tracingContext);

        handler.onScopeReset(context);

        InOrder inOrder = inOrder(scope2, scope1);
        inOrder.verify(scope2).close();
        inOrder.verify(scope1).close();
        BDDMockito.then(currentTraceContext).should().maybeScope(null);
    }

    @Test
    void nullScopeShouldBeSupported() {
        TracingObservationHandler<Observation.Context> handler = () -> null;

        thenNoException().isThrownBy(() -> handler.onScopeClosed(new Observation.Context()));
    }

    @Test
    void spanShouldNotBeOverriddenWhenResettingScope() {
        Span span = mock(Span.class);
        Observation.Context context = new Observation.Context();
        TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
        tracingContext.setSpan(span);
        context.put(TracingObservationHandler.TracingContext.class, tracingContext);
        TracingObservationHandler<Observation.Context> handler = () -> tracer;

        handler.onScopeReset(context);

        assertThat(tracingContext.getSpan()).isSameAs(span);
    }

}
