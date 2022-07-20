/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.tracing.internal;

import io.micrometer.tracing.SpanName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.observability.tracing.internal.DefaultSpanNamer;

import static org.assertj.core.api.BDDAssertions.then;

class DefaultSpanNamerTests {

    @Test
    void should_override_default_name_with_one_from_annotation() {
        then(new DefaultSpanNamer().name(new WithAnnotation(), "default")).isEqualTo("foo");
    }

    @Test
    void should_override_default_name_with_one_from_overridden_to_string() {
        then(new DefaultSpanNamer().name(new WithToString(), "default")).isEqualTo("foo");
    }

    @Test
    void should_override_default_name_with_default_when_default_to_string() {
        then(new DefaultSpanNamer().name(new WithDefaultToString(), "foo")).isEqualTo("foo");
    }

    @SpanName("foo")
    static class WithAnnotation {

    }

    static class WithToString {

        @Override
        public String toString() {
            return "foo";
        }

    }

    static class WithDefaultToString {

    }

}
