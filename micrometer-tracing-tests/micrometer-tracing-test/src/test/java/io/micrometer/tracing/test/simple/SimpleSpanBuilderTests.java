/*
 * Copyright 2021 VMware, Inc.
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

import io.micrometer.tracing.Span;
import org.junit.jupiter.api.Test;

class SimpleSpanBuilderTests {

    @Test
    void should_build_a_span_using_builder() {
        SimpleTracer simpleTracer = new SimpleTracer();
        SimpleSpanBuilder builder = new SimpleSpanBuilder(simpleTracer);

        builder.name("foo").kind(Span.Kind.CLIENT).remoteIpAndPort("1.2.3.4", 80).error(new Throwable())
                .event("foo event").tag("tag", "value").remoteServiceName("bar").setNoParent().start().end();

        TracerAssert.assertThat(simpleTracer).onlySpan().hasNameEqualTo("foo").hasRemoteServiceNameEqualTo("bar")
                .hasTag("tag", "value").hasEventWithNameEqualTo("foo event").hasIpThatIsNotBlank().hasPortThatIsSet()
                .hasKindEqualTo(Span.Kind.CLIENT).assertThatThrowable().isInstanceOf(Throwable.class);
    }

}
