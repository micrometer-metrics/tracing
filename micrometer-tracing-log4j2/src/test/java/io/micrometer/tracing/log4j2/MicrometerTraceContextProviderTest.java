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
package io.micrometer.tracing.log4j2;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MicrometerTraceContextProviderTest {

    private final MicrometerTraceContextProvider provider = new MicrometerTraceContextProvider();

    @AfterEach
    void tearDown() {
        MicrometerTraceContextProvider.setTracer(null);
    }

    @Test
    void should_return_null_when_tracer_is_not_set() {
        assertThat(provider.getTraceId()).isNull();
        assertThat(provider.getSpanId()).isNull();
        assertThat(provider.getTraceFlags()).isNull();
    }

    @Test
    void should_return_null_when_no_active_span() {
        Tracer tracer = mock(Tracer.class);
        given(tracer.currentSpan()).willReturn(null);

        MicrometerTraceContextProvider.setTracer(tracer);

        assertThat(provider.getTraceId()).isNull();
        assertThat(provider.getSpanId()).isNull();
        assertThat(provider.getTraceFlags()).isNull();
    }

    @Test
    void should_return_trace_and_span_ids_from_active_span() {
        TraceContext context = mock(TraceContext.class);
        given(context.traceId()).willReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        given(context.spanId()).willReturn("00f067aa0ba902b7");

        Span span = mock(Span.class);
        given(span.context()).willReturn(context);

        Tracer tracer = mock(Tracer.class);
        given(tracer.currentSpan()).willReturn(span);

        MicrometerTraceContextProvider.setTracer(tracer);

        assertThat(provider.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(provider.getSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(provider.getTraceFlags()).isNull();
    }
}
