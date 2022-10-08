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
package io.micrometer.tracing.brave.bridge;

import brave.Tracer;
import brave.Tracing;
import io.micrometer.tracing.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class BraveSpanBuilderTests {

    @Test
    void should_set_child_span_when_using_builders() {
        Tracer tracer = Tracing.newBuilder().build().tracer();
        Span.Builder builder = new BraveSpanBuilder(tracer);
        Span parentSpan = BraveSpan.fromBrave(tracer.nextSpan());

        Span child = builder.setParent(parentSpan.context()).start();

        then(child.context().traceId()).isEqualTo(parentSpan.context().traceId());
        then(child.context().parentId()).isEqualTo(parentSpan.context().spanId());
    }

}
