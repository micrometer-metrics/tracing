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
package io.micrometer.tracing.test.simple;

import java.util.HashMap;
import java.util.Map;

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import org.junit.jupiter.api.Test;

class SimpleSpanBuilderTests {

    @Test
    void should_build_a_span_using_builder() {
        SimpleTracer simpleTracer = new SimpleTracer();
        SimpleSpanBuilder builder = new SimpleSpanBuilder(simpleTracer);
        SimpleTraceContext context1 = new SimpleTraceContext();
        SimpleTraceContext context2 = new SimpleTraceContext();

        builder.name("foo")
            .kind(Span.Kind.CLIENT)
            .remoteIpAndPort("1.2.3.4", 80)
            .error(new Throwable())
            .event("foo event")
            .tag("tag", "value")
            .remoteServiceName("bar")
            .setNoParent()
            .addLink(new Link(context1))
            .addLink(new Link(context2, tags()))
            .start()
            .end();

        TracerAssert.assertThat(simpleTracer)
            .onlySpan()
            .hasNameEqualTo("foo")
            .hasRemoteServiceNameEqualTo("bar")
            .hasTag("tag", "value")
            .hasEventWithNameEqualTo("foo event")
            .hasIpThatIsNotBlank()
            .hasPortThatIsSet()
            .hasKindEqualTo(Span.Kind.CLIENT)
            .hasLink(new Link(context1))
            .hasLink(new Link(context2, tags()))
            .assertThatThrowable()
            .isInstanceOf(Throwable.class);
    }

    @Test
    void should_build_a_span_using_builder_with_parent() {
        SimpleTracer simpleTracer = new SimpleTracer();
        SimpleSpanBuilder builder = new SimpleSpanBuilder(simpleTracer);
        SimpleTraceContextBuilder ctxBuilder = new SimpleTraceContextBuilder();
        TraceContext parentCtx = ctxBuilder.spanId("spam").traceId("bar").sampled(true).build();

        builder.name("foo").setParent(parentCtx).start().end();

        TracerAssert.assertThat(simpleTracer)
            .onlySpan()
            .hasNameEqualTo("foo")
            .hasTraceIdEqualTo("bar")
            .hasParentIdEqualTo("spam");
    }

    private Map<String, Object> tags() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

}
