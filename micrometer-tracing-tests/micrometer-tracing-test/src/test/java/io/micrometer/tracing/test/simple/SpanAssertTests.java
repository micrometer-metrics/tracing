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
    void should_not_throw_exception_when_name_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenThrownBy(() -> assertThat(span).hasNameEqualTo("bar")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_jump_to_and_back_from_throwable_assert() {
        SimpleSpan span = new SimpleSpan().name("foo").error(new RuntimeException("bar"));

        thenNoException().isThrownBy(() -> assertThat(span)
                .hasNameEqualTo("foo")
                .thenThrowable()
                .hasMessage("bar")
                .backToSpan()
                .hasNameEqualTo("foo"));
    }

}
