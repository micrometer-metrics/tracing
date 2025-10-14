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
package io.micrometer.tracing.brave.bridge;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler.Cause;
import brave.internal.codec.HexCodec;
import brave.propagation.TraceContext;
import io.micrometer.tracing.exporter.TestSpanReporter;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Objects;

class CompositeSpanHandlerTests {

    @Test
    void should_store_spans_through_test_span_reporter() {
        TestSpanReporter testSpanReporter = new TestSpanReporter();
        TraceContext traceContext = TraceContext.newBuilder().traceId(id()).spanId(id()).build();
        MutableSpan mutableSpan = new MutableSpan(traceContext, null);
        mutableSpan.name("bar");

        boolean success = new CompositeSpanHandler(null, Collections.singletonList(testSpanReporter), null)
            .end(traceContext, mutableSpan, Cause.FINISHED);

        BDDAssertions.then(success).isTrue();
        BDDAssertions.then(testSpanReporter.spans()).hasSize(1);
        BDDAssertions.then(Objects.requireNonNull(testSpanReporter.poll()).getName()).isEqualTo("bar");
    }

    private static long id() {
        return HexCodec.lowerHexToUnsignedLong("ff000000000000000000000000000041");
    }

}
