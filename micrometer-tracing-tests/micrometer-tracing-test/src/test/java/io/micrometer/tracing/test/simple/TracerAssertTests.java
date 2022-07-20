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

import org.junit.jupiter.api.Test;

import static io.micrometer.tracing.test.simple.TracerAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class TracerAssertTests {

    @Test
    void should_not_throw_exception_when_name_correct() {
        SimpleTracer simpleTracer = new SimpleTracer();

        simpleTracer.nextSpan().name("foo").start().end();

        thenNoException().isThrownBy(() -> assertThat(simpleTracer).onlySpan().hasNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_name_incorrect() {
        SimpleTracer simpleTracer = new SimpleTracer();

        simpleTracer.nextSpan().name("foo").start().end();

        thenThrownBy(() -> assertThat(simpleTracer).lastSpan().hasNameEqualTo("bar"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_name_correct_using_last_span() {
        SimpleTracer simpleTracer = new SimpleTracer();

        simpleTracer.nextSpan().name("foo").start().end();

        thenNoException().isThrownBy(() -> assertThat(simpleTracer).onlySpan().hasNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_name_incorrect_using_last_span() {
        SimpleTracer simpleTracer = new SimpleTracer();

        simpleTracer.nextSpan().name("foo").start().end();

        thenThrownBy(() -> assertThat(simpleTracer).lastSpan().hasNameEqualTo("bar"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_size_of_reported_spans_is_correct() {
        SimpleTracer simpleTracer = new SimpleTracer();

        simpleTracer.nextSpan().name("foo").start().end();

        thenNoException().isThrownBy(() -> assertThat(simpleTracer).reportedSpans().hasSize(1));
    }

    @Test
    void should_throw_exception_when_size_reported_spans_is_invalid() {
        SimpleTracer simpleTracer = new SimpleTracer();

        simpleTracer.nextSpan().name("foo").start().end();

        thenThrownBy(() -> assertThat(simpleTracer).reportedSpans().hasSize(2)).isInstanceOf(AssertionError.class);
    }

}
