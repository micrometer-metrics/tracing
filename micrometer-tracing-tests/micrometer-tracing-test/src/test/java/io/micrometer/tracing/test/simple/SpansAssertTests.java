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

import java.util.Collections;

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
    void should_not_throw_exception_when_name_incorrect() {
        SimpleSpan span = new SimpleSpan().name("foo");

        thenThrownBy(() -> assertThat(Collections.singletonList(span)).hasASpanWithName("bar")).isInstanceOf(AssertionError.class);
    }
}
