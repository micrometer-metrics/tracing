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

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static io.micrometer.tracing.test.simple.SpanAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class SpanAssertTests {

    @Test
    void should_not_throw_exception_when_name_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenNoException().isThrownBy(() -> assertThat(span).hasNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_name_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenThrownBy(() -> assertThat(span).hasNameEqualTo("bar")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_name_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveNameEqualTo("bar"));
    }

    @Test
    void should_throw_exception_when_name_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenThrownBy(() -> assertThat(span).doesNotHaveNameEqualTo("foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_jump_to_and_back_from_throwable_assert() {
        SimpleSpan span = new SimpleSpan().name("foo").error(new RuntimeException("bar"));

        thenNoException().isThrownBy(() -> assertThat(span).hasNameEqualTo("foo")
            .thenThrowable()
            .hasMessage("bar")
            .backToSpan()
            .hasNameEqualTo("foo"));
    }

    @Test
    void should_not_fail_when_tags_are_missing() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).hasNoTags());
    }

    @Test
    void should_fail_when_tags_are_present() {
        SimpleSpan span = new SimpleSpan().tag("foo", "bar");

        thenThrownBy(() -> assertThat(span).hasNoTags()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_tag_with_key_and_value_missing() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveTag("foo", "bar"));
        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveTag(() -> "foo", "bar"));
    }

    @Test
    void should_fail_when_tag_with_key_and_value_present() {
        SimpleSpan span = new SimpleSpan().tag("foo", "bar");

        thenThrownBy(() -> assertThat(span).doesNotHaveTag("foo", "bar")).isInstanceOf(AssertionError.class);
        thenThrownBy(() -> assertThat(span).doesNotHaveTag(() -> "foo", "bar")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_tag_with_key_and_value_present() {
        SimpleSpan span = new SimpleSpan().tag("foo", "bar");

        thenNoException().isThrownBy(() -> assertThat(span).hasTag("foo", "bar"));
        thenNoException().isThrownBy(() -> assertThat(span).hasTag(() -> "foo", "bar"));
    }

    @Test
    void should_fail_when_tag_with_key_and_value_missing() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).hasTag("foo", "bar")).isInstanceOf(AssertionError.class);
        thenThrownBy(() -> assertThat(span).hasTag(() -> "foo", "bar")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_tag_missing() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveTagWithKey("foo"));
        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveTagWithKey(() -> "foo"));
    }

    @Test
    void should_fail_when_tag_present() {
        SimpleSpan span = new SimpleSpan().tag("foo", "bar");

        thenThrownBy(() -> assertThat(span).doesNotHaveTagWithKey("foo")).isInstanceOf(AssertionError.class);
        thenThrownBy(() -> assertThat(span).doesNotHaveTagWithKey(() -> "foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_tag_present() {
        SimpleSpan span = new SimpleSpan().tag("foo", "bar");

        thenNoException().isThrownBy(() -> assertThat(span).hasTagWithKey("foo"));
        thenNoException().isThrownBy(() -> assertThat(span).hasTagWithKey(() -> "foo"));
    }

    @Test
    void should_fail_when_tag_missing() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).hasTagWithKey("foo")).isInstanceOf(AssertionError.class);
        thenThrownBy(() -> assertThat(span).hasTagWithKey(() -> "foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_started() {
        SimpleSpan span = new SimpleSpan().start();

        thenNoException().isThrownBy(() -> assertThat(span).isStarted());
    }

    @Test
    void should_fail_when_not_started() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).isStarted()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_not_started() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).isNotStarted());
    }

    @Test
    void should_fail_when_started() {
        SimpleSpan span = new SimpleSpan().start();

        thenThrownBy(() -> assertThat(span).isNotStarted()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_ended() {
        SimpleSpan span = new SimpleSpan().start();
        span.end();

        thenNoException().isThrownBy(() -> assertThat(span).isEnded());
    }

    @Test
    void should_fail_when_not_ended() {
        SimpleSpan span = new SimpleSpan().start();

        thenThrownBy(() -> assertThat(span).isEnded()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_not_ended() {
        SimpleSpan span = new SimpleSpan().start();

        thenNoException().isThrownBy(() -> assertThat(span).isNotEnded());
    }

    @Test
    void should_fail_when_ended() {
        SimpleSpan span = new SimpleSpan().start();
        span.end();

        thenThrownBy(() -> assertThat(span).isNotEnded()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_remote_service_name_equal() {
        SimpleSpan span = new SimpleSpan().remoteServiceName("foo");

        thenNoException().isThrownBy(() -> assertThat(span).hasRemoteServiceNameEqualTo("foo"));
    }

    @Test
    void should_fail_when_remote_service_name_not_equal() {
        SimpleSpan span = new SimpleSpan().remoteServiceName("foo");

        thenThrownBy(() -> assertThat(span).hasRemoteServiceNameEqualTo("bar")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_remote_service_name_not_equal() {
        SimpleSpan span = new SimpleSpan().remoteServiceName("foo");

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveRemoteServiceNameEqualTo("bar"));
    }

    @Test
    void should_fail_when_remote_service_name_equal() {
        SimpleSpan span = new SimpleSpan().remoteServiceName("foo");

        thenThrownBy(() -> assertThat(span).doesNotHaveRemoteServiceNameEqualTo("foo"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_kind_equal() {
        SimpleSpan span = new SimpleSpan();
        span.setSpanKind(Span.Kind.CLIENT);

        thenNoException().isThrownBy(() -> assertThat(span).hasKindEqualTo(Span.Kind.CLIENT));
    }

    @Test
    void should_fail_when_kind_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.setSpanKind(Span.Kind.CLIENT);

        thenThrownBy(() -> assertThat(span).hasKindEqualTo(Span.Kind.PRODUCER)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_kind_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.setSpanKind(Span.Kind.CLIENT);

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveKindEqualTo(Span.Kind.PRODUCER));
    }

    @Test
    void should_fail_when_kind_equal() {
        SimpleSpan span = new SimpleSpan();
        span.setSpanKind(Span.Kind.CLIENT);

        thenThrownBy(() -> assertThat(span).doesNotHaveKindEqualTo(Span.Kind.CLIENT))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_event_with_name_present() {
        SimpleSpan span = new SimpleSpan().event("foo");

        thenNoException().isThrownBy(() -> assertThat(span).hasEventWithNameEqualTo("foo"));
    }

    @Test
    void should_fail_when_event_with_name_missing() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).hasEventWithNameEqualTo("foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_event_with_name_missing() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveEventWithNameEqualTo("foo"));
    }

    @Test
    void should_fail_when_event_with_name_present() {
        SimpleSpan span = new SimpleSpan().event("foo");

        thenThrownBy(() -> assertThat(span).doesNotHaveEventWithNameEqualTo("foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_ip_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenNoException().isThrownBy(() -> assertThat(span).hasIpEqualTo("1.2.3.4"));
    }

    @Test
    void should_fail_when_ip_not_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenThrownBy(() -> assertThat(span).hasIpEqualTo("foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_ip_not_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveIpEqualTo("foo"));
    }

    @Test
    void should_fail_when_ip_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenThrownBy(() -> assertThat(span).doesNotHaveIpEqualTo("1.2.3.4")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_ip_blank() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).hasIpThatIsBlank());
    }

    @Test
    void should_fail_when_ip_not_blank() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenThrownBy(() -> assertThat(span).hasIpThatIsBlank()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_ip_not_blank() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenNoException().isThrownBy(() -> assertThat(span).hasIpThatIsNotBlank());
    }

    @Test
    void should_fail_when_ip_blank() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).hasIpThatIsNotBlank()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_port_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenNoException().isThrownBy(() -> assertThat(span).hasPortEqualTo(1234));
    }

    @Test
    void should_fail_when_port_not_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenThrownBy(() -> assertThat(span).hasPortEqualTo(2345)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_port_not_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHavePortEqualTo(2345));
    }

    @Test
    void should_fail_when_port_equal() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenThrownBy(() -> assertThat(span).doesNotHavePortEqualTo(1234)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_port_is_not_set() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).hasPortThatIsNotSet());
    }

    @Test
    void should_fail_when_port_is_set() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenThrownBy(() -> assertThat(span).hasPortThatIsNotSet()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_port_is_set() {
        SimpleSpan span = new SimpleSpan().remoteIpAndPort("1.2.3.4", 1234);

        thenNoException().isThrownBy(() -> assertThat(span).hasPortThatIsSet());
    }

    @Test
    void should_fail_when_port_is_not_set() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).hasPortThatIsSet()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_fail_when_link_does_not_have_a_trace_context() {
        SimpleSpan span = new SimpleSpan().addLink(new Link(Mockito.mock(TraceContext.class)));

        thenThrownBy(() -> assertThat(span).hasLink(new Link(new SimpleTraceContext())))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_link_has_a_trace_context() {
        SimpleTraceContext context = new SimpleTraceContext();
        SimpleSpan span = new SimpleSpan().addLink(new Link(context));

        thenNoException().isThrownBy(() -> assertThat(span).hasLink(new Link(context)));
    }

    @Test
    void should_fail_when_link_has_a_trace_context() {
        SimpleTraceContext context = new SimpleTraceContext();
        SimpleSpan span = new SimpleSpan().addLink(new Link(context));

        thenThrownBy(() -> assertThat(span).doesNotHaveLink(new Link(context))).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_link_does_not_have_a_trace_context() {
        SimpleSpan span = new SimpleSpan().addLink(new Link(TraceContext.NOOP));

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveLink(new Link(new SimpleTraceContext())));
    }

    @Test
    void should_fail_when_assertion_on_links_fail() {
        SimpleSpan span = new SimpleSpan()
            .addLink(new Link(Mockito.mock(TraceContext.class), Collections.singletonMap("foo", "bar")));

        thenThrownBy(() -> assertThat(span).hasLink(link -> Assertions.assertThat(link).isNull()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_assertion_on_links_passes() {
        SimpleTraceContext context = new SimpleTraceContext();
        SimpleSpan span = new SimpleSpan().addLink(new Link(context, Collections.singletonMap("foo", "bar")));

        thenNoException().isThrownBy(() -> assertThat(span).hasLink(link -> Assertions.assertThat(link).isNotNull()));
    }

    @Test
    void should_fail_when_assertion_on_links_does_not_fail() {
        SimpleSpan span = new SimpleSpan()
            .addLink(new Link(Mockito.mock(TraceContext.class), Collections.singletonMap("foo", "bar")));

        thenThrownBy(() -> assertThat(span).doesNotHaveLink(link -> Assertions.assertThat(link).isNotNull()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_assertion_on_links_fails() {
        SimpleTraceContext context = new SimpleTraceContext();
        SimpleSpan span = new SimpleSpan().addLink(new Link(context, Collections.singletonMap("foo", "bar")));

        thenNoException()
            .isThrownBy(() -> assertThat(span).doesNotHaveLink(link -> Assertions.assertThat(link).isNull()));
    }

    @Test
    void should_not_fail_when_spanId_is_equal() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).hasSpanIdEqualTo(span.getSpanId()));
    }

    @Test
    void should_fail_when_spanId_is_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setSpanId("1");

        thenThrownBy(() -> assertThat(span).hasSpanIdEqualTo("2")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_fail_when_parentId_is_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setParentId("1");

        thenThrownBy(() -> assertThat(span).hasParentIdEqualTo("2")).isInstanceOf(AssertionError.class);
    }


    @Test
    void should_not_fail_when_parentId_is_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setParentId("1");

        thenNoException().isThrownBy(() -> assertThat(span).hasParentIdEqualTo("1"));
    }

    @Test
    void should_not_fail_when_parentId_is_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setSpanId("1");

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveSpanIdEqualTo("2"));
    }

    @Test
    void should_fail_when_parentId_is_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setParentId("1");

        thenThrownBy(() -> assertThat(span).doesNotHaveParentIdEqualTo(span.getParentId()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_spanId_is_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setSpanId("1");

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveSpanIdEqualTo("2"));
    }

    @Test
    void should_fail_when_spanId_is_equal() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).doesNotHaveSpanIdEqualTo(span.getSpanId()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_traceId_is_equal() {
        SimpleSpan span = new SimpleSpan();

        thenNoException().isThrownBy(() -> assertThat(span).hasTraceIdEqualTo(span.getSpanId()));
    }

    @Test
    void should_fail_when_traceId_is_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setTraceId("1");

        thenThrownBy(() -> assertThat(span).hasTraceIdEqualTo("2")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_traceId_is_not_equal() {
        SimpleSpan span = new SimpleSpan();
        span.context().setTraceId("1");

        thenNoException().isThrownBy(() -> assertThat(span).doesNotHaveTraceIdEqualTo("2"));
    }

    @Test
    void should_fail_when_traceId_is_equal() {
        SimpleSpan span = new SimpleSpan();

        thenThrownBy(() -> assertThat(span).doesNotHaveTraceIdEqualTo(span.getSpanId()))
            .isInstanceOf(AssertionError.class);
    }

}
