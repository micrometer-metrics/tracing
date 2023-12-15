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
package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.TraceContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class SimpleTraceContextBuilderTests {

    @Test
    void should_build_trace_context_through_builder_with_default_values() {
        TraceContext traceContext = new SimpleTraceContextBuilder().build();

        SoftAssertions.assertSoftly(softAssertions -> {
            softAssertions.assertThat(traceContext.traceId()).isEmpty();
            softAssertions.assertThat(traceContext.spanId()).isEmpty();
            softAssertions.assertThat(traceContext.parentId()).isEmpty();
            softAssertions.assertThat(traceContext.sampled()).isFalse();
        });
    }

    @Test
    void should_build_trace_context_through_builder_with_overridden_values() {
        TraceContext traceContext = new SimpleTraceContextBuilder().traceId("1")
            .spanId("2")
            .parentId("3")
            .sampled(true)
            .build();

        SoftAssertions.assertSoftly(softAssertions -> {
            softAssertions.assertThat(traceContext.traceId()).isEqualTo("1");
            softAssertions.assertThat(traceContext.spanId()).isEqualTo("2");
            softAssertions.assertThat(traceContext.parentId()).isEqualTo("3");
            softAssertions.assertThat(traceContext.sampled()).isTrue();
        });
    }

}
