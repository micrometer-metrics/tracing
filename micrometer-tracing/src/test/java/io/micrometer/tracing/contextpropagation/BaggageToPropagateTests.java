/**
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.tracing.contextpropagation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class BaggageToPropagateTests {

    @Test
    void should_throw_an_exception_when_even_number_of_args() {
        thenThrownBy(() -> new BaggageToPropagate("foo")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You need an even number of baggage entries. You have [1] entries");
    }

    @Test
    void should_set_baggage_to_propagate_entries_with_a_map() {
        Map<String, String> map = new HashMap<>();
        map.put("foo", "bar");

        then(new BaggageToPropagate(map).getBaggage()).containsExactlyEntriesOf(map);
    }

    @Test
    void should_set_baggage_to_propagate_entries_with_varargs() {
        Map<String, String> map = new HashMap<>();
        map.put("foo", "bar");
        map.put("baz", "bar2");

        then(new BaggageToPropagate("foo", "bar", "baz", "bar2").getBaggage()).containsExactlyEntriesOf(map);
    }

}
