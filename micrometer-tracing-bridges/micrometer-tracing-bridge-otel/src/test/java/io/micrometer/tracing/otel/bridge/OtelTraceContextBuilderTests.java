/**
 * Copyright 2025 the original author or authors.
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

import io.micrometer.tracing.TraceContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class OtelTraceContextBuilderTests {

    public static final String TRACE_ID = "3e425f2373d89640bde06e8285e7bf88";

    public static final String PARENT_SPAN_ID = "9a5fdefae3abb440";

    public static final String SPAN_ID = "ae3abb4409a5fdef";

    @Test
    void should_build_trace_context_for_non_null_sampled() {
        TraceContext traceContext = new OtelTraceContextBuilder().traceId(TRACE_ID)
            .parentId(PARENT_SPAN_ID)
            .spanId(SPAN_ID)
            .sampled(true)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(traceContext.spanId()).isEqualTo(SPAN_ID);
            softly.assertThat(traceContext.traceId()).isEqualTo(TRACE_ID);
            softly.assertThat(traceContext.parentId()).isEqualTo(PARENT_SPAN_ID);
            softly.assertThat(traceContext.sampled()).isTrue();
        });
    }

    @Test
    void should_build_trace_context_for_null_sampled() {
        TraceContext traceContext = new OtelTraceContextBuilder().traceId(TRACE_ID)
            .parentId(PARENT_SPAN_ID)
            .spanId(SPAN_ID)
            .sampled(null)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(traceContext.spanId()).isEqualTo(SPAN_ID);
            softly.assertThat(traceContext.traceId()).isEqualTo(TRACE_ID);
            softly.assertThat(traceContext.parentId()).isEqualTo(PARENT_SPAN_ID);
            softly.assertThat(traceContext.sampled()).isNull();
        });
    }

}
