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

import java.util.Arrays;
import java.util.Collections;

import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Test;

import static io.micrometer.tracing.test.simple.SpansAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class SpansAssertTests {

    @Test
    void should_not_throw_exception_when_name_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenNoException().isThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithName("foo"));
    }

    @Test
    void should_throw_exception_when_name_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithName("bar"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_name_and_assertion_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");
        SimpleSpan span2 = new SimpleSpan().name("baz");

        thenNoException().isThrownBy(
                () -> assertThat(Arrays.asList(span, span2)).hasASpanWithName("baz", AbstractAssert::isNotNull));
    }

    @Test
    void should_throw_exception_when_name_incorrect_but_assertion_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");
        SimpleSpan span2 = new SimpleSpan().name("baz");

        thenThrownBy(() -> assertThat(Arrays.asList(span, span2)).hasASpanWithName("bar", AbstractAssert::isNotNull))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_throw_exception_when_name_correct_but_assertion_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");
        SimpleSpan span2 = new SimpleSpan().name("baz");

        thenThrownBy(() -> assertThat(Arrays.asList(span, span2)).hasASpanWithName("baz",
                spanAssert -> spanAssert.hasNameEqualTo("bar"))).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_name_ignore_case_and_assertion_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");
        SimpleSpan span2 = new SimpleSpan().name("baz");

        thenNoException().isThrownBy(() -> assertThat(Arrays.asList(span, span2)).hasASpanWithNameIgnoreCase("BaZ",
                AbstractAssert::isNotNull));
    }

    @Test
    void should_throw_exception_when_name_ignore_case_incorrect_but_assertion_correct() {
        SimpleSpan span = new SimpleSpan().name("foo");
        SimpleSpan span2 = new SimpleSpan().name("baz");

        thenThrownBy(() -> assertThat(Arrays.asList(span, span2)).hasASpanWithNameIgnoreCase("bar",
                AbstractAssert::isNotNull)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_throw_exception_when_name_ignore_case_correct_but_assertion_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");
        SimpleSpan span2 = new SimpleSpan().name("baz");

        thenThrownBy(() -> assertThat(Arrays.asList(span, span2)).hasASpanWithNameIgnoreCase("BaZ",
                spanAssert -> spanAssert.hasNameEqualTo("bar"))).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_jump_to_and_back_from_span_assert() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenNoException().isThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithName("foo")
                .thenASpanWithNameEqualTo("foo").hasNameEqualTo("foo").backToSpans().hasASpanWithNameIgnoreCase("foo"));
    }

    @Test
    void should_not_throw_exception_when_same_trace_id() {
        SimpleSpan span = new SimpleSpan();
        span.context().setTraceId("a");
        SimpleSpan span2 = new SimpleSpan();
        span2.context().setTraceId("a");

        thenNoException().isThrownBy(() -> assertThat(Arrays.asList(span, span2)).haveSameTraceId());
    }

    @Test
    void should_throw_exception_when_trace_id_not_the_same() {
        SimpleSpan span = new SimpleSpan();
        span.context().setTraceId("a");
        SimpleSpan span2 = new SimpleSpan();
        span2.context().setTraceId("b");

        thenThrownBy(() -> assertThat(Arrays.asList(span, span2)).haveSameTraceId()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_for_all_spans_with_name_are_passing_a_span_assert() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenNoException().isThrownBy(() -> assertThat(Collections.singletonList(span)).forAllSpansWithNameEqualTo("foo",
                SpanAssert::isNotEnded));
    }

    @Test
    void should_throw_exception_when_for_all_spans_with_name_are_failing_at_a_span_assert() {
        SimpleSpan span = new SimpleSpan().name("foo").start();
        span.end();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).forAllSpansWithNameEqualTo("foo",
                SpanAssert::isNotEnded));
    }

    @Test
    void should_not_throw_exception_when_for_all_spans_with_name_ignore_case_are_passing_a_span_assert() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenNoException().isThrownBy(() -> assertThat(Collections.singletonList(span))
                .forAllSpansWithNameEqualToIgnoreCase("FOO", SpanAssert::isNotEnded));
    }

    @Test
    void should_throw_exception_when_for_all_spans_with_name_ignore_case_are_failing_at_a_span_assert() {
        SimpleSpan span = new SimpleSpan().name("foo").start();
        span.end();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).forAllSpansWithNameEqualToIgnoreCase("FOO",
                SpanAssert::isNotEnded));
    }

    @Test
    void should_not_throw_exception_assert_that_a_span_with_name_equal_to() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        assertThat(Collections.singletonList(span)).assertThatASpanWithNameEqualTo("foo").isStarted().backToSpans();

        assertThat(Collections.singletonList(span)).thenASpanWithNameEqualTo("foo").isStarted().backToSpans();
    }

    @Test
    void should_throw_exception_when_assert_that_a_span_with_name_equal_to_fails_on_missing_name() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).assertThatASpanWithNameEqualTo("bar").isStarted()
                .backToSpans());

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).thenASpanWithNameEqualTo("bar").isStarted()
                .backToSpans());
    }

    @Test
    void should_throw_exception_when_assert_that_a_span_with_name_equal_to_fails_on_assertion() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).assertThatASpanWithNameEqualTo("foo").isEnded()
                .backToSpans());

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).thenASpanWithNameEqualTo("foo").isEnded()
                .backToSpans());
    }

    @Test
    void should_not_throw_exception_assert_that_a_span_with_name_equal_to_ignore_case() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        assertThat(Collections.singletonList(span)).assertThatASpanWithNameEqualToIgnoreCase("FOO").isStarted()
                .backToSpans();

        assertThat(Collections.singletonList(span)).thenASpanWithNameEqualToIgnoreCase("FOO").isStarted().backToSpans();
    }

    @Test
    void should_throw_exception_when_assert_that_a_span_with_name_equal_to_ignore_case_fails_on_missing_name() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).assertThatASpanWithNameEqualToIgnoreCase("BAR")
                .isStarted().backToSpans());

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).thenASpanWithNameEqualToIgnoreCase("BAR")
                .isStarted().backToSpans());
    }

    @Test
    void should_throw_exception_when_assert_that_a_span_with_name_equal_to_ignore_case_fails_on_assertion() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).assertThatASpanWithNameEqualToIgnoreCase("FOO")
                .isEnded().backToSpans());

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).thenASpanWithNameEqualToIgnoreCase("FOO")
                .isEnded().backToSpans());
    }

    @Test
    void should_not_throw_exception_when_number_of_spans_is_correct() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        assertThat(Collections.singletonList(span)).hasNumberOfSpansEqualTo(1);
    }

    @Test
    void should_throw_exception_when_number_of_spans_is_wrong() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasNumberOfSpansEqualTo(2));
    }

    @Test
    void should_not_throw_exception_when_number_of_spans_with_name_is_correct() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        assertThat(Collections.singletonList(span)).hasNumberOfSpansWithNameEqualTo("foo", 1);
    }

    @Test
    void should_throw_exception_when_number_of_spans_with_name_is_correct_but_name_is_wrong() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasNumberOfSpansWithNameEqualTo("bar", 1));
    }

    @Test
    void should_throw_exception_when_number_of_spans_with_name_is_wrong() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasNumberOfSpansWithNameEqualTo("bar", 1));
    }

    @Test
    void should_not_throw_exception_when_number_of_spans_with_name_ignore_case_is_correct() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        assertThat(Collections.singletonList(span)).hasNumberOfSpansWithNameEqualToIgnoreCase("foo", 1);
    }

    @Test
    void should_throw_exception_when_number_of_spans_with_name_ignore_case_is_correct_but_name_is_wrong() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(
                () -> assertThat(Collections.singletonList(span)).hasNumberOfSpansWithNameEqualToIgnoreCase("bar", 1));
    }

    @Test
    void should_throw_exception_when_number_of_spans_with_name_ignore_case_is_wrong() {
        SimpleSpan span = new SimpleSpan().name("foo").start();

        thenThrownBy(
                () -> assertThat(Collections.singletonList(span)).hasNumberOfSpansWithNameEqualToIgnoreCase("bar", 1));
    }

    @Test
    void should_not_throw_exception_when_span_has_remote_name() {
        SimpleSpan span = new SimpleSpan().remoteServiceName("foo");

        assertThat(Collections.singletonList(span)).hasASpanWithRemoteServiceName("foo");
    }

    @Test
    void should_throw_exception_when_span_does_not_have_remote_name() {
        SimpleSpan span = new SimpleSpan().remoteServiceName("foo");

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithRemoteServiceName("bar"));
    }

    @Test
    void should_not_throw_exception_when_span_has_tag() {
        SimpleSpan span = new SimpleSpan().tag("foo", "bar");

        assertThat(Collections.singletonList(span)).hasASpanWithATag("foo", "bar").hasASpanWithATagKey("foo")
                .hasASpanWithATag(() -> "foo", "bar").hasASpanWithATagKey(() -> "foo");
    }

    @Test
    void should_throw_exception_when_span_does_not_have_tag() {
        SimpleSpan span = new SimpleSpan().tag("bar", "foo");

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithATag("foo", "bar"));
        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithATagKey("foo"));
        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithATag(() -> "foo", "bar"));
        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithATagKey(() -> "foo"));
    }

}
