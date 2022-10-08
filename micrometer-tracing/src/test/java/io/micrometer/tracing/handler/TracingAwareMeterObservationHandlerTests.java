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

import io.micrometer.core.instrument.observation.MeterObservationHandler;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracingAwareMeterObservationHandler}.
 *
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class TracingAwareMeterObservationHandlerTests {

    @Mock
    private MeterObservationHandler<Observation.Context> delegate;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private TracingAwareMeterObservationHandler<Observation.Context> handler;

    @Test
    void callsShouldBeDelegated() {
        Observation.Context context = new Observation.Context();
        context.put(TracingObservationHandler.TracingContext.class, new TracingObservationHandler.TracingContext());

        handler.onStart(context);
        verify(delegate).onStart(context);

        handler.onError(context);
        verify(delegate).onError(context);

        Observation.Event event = Observation.Event.of("test");
        handler.onEvent(event, context);
        verify(delegate).onEvent(event, context);

        handler.onScopeOpened(context);
        verify(delegate).onScopeOpened(context);

        handler.onStop(context);
        verify(delegate).onStop(context);

        handler.onScopeClosed(context);
        verify(delegate).onScopeClosed(context);

        handler.supportsContext(context);
        verify(delegate).supportsContext(context);
    }

    @Test
    void spanShouldBeAvailableOnStop() {
        Observation.Context observationContext = new Observation.Context();
        TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
        observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);

        Span span = mock(Span.class);
        tracingContext.setSpan(span);
        Tracer.SpanInScope spanInScope = mock(Tracer.SpanInScope.class);
        when(tracer.withSpan(span)).thenReturn(spanInScope);

        handler.onStop(observationContext);

        verify(spanInScope).close();
        verify(tracer).withSpan(span);
        verify(delegate).onStop(observationContext);
    }

}
