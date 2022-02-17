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

package io.micrometer.tracing.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class StringUtilsTests {

    @Test
    void isBlank() {
        then(StringUtils.isBlank("")).isTrue();
        then(StringUtils.isBlank(" ")).isTrue();
        then(StringUtils.isBlank(null)).isTrue();
        then(StringUtils.isBlank("a")).isFalse();
    }

    @Test
    void isNotBlank() {
        then(StringUtils.isNotBlank("")).isFalse();
        then(StringUtils.isNotBlank(" ")).isFalse();
        then(StringUtils.isNotBlank(null)).isFalse();
        then(StringUtils.isNotBlank("a")).isTrue();
    }

    @Test
    void isEmpty() {
        then(StringUtils.isEmpty("")).isTrue();
        then(StringUtils.isEmpty(" ")).isFalse();
        then(StringUtils.isEmpty(null)).isTrue();
        then(StringUtils.isEmpty("a")).isFalse();
    }

    @Test
    void isNotEmpty() {
        then(StringUtils.isNotEmpty("")).isFalse();
        then(StringUtils.isNotEmpty(" ")).isTrue();
        then(StringUtils.isNotEmpty(null)).isFalse();
        then(StringUtils.isNotEmpty("a")).isTrue();
    }
}
