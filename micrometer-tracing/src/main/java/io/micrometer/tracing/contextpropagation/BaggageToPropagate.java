/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.tracing.contextpropagation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wrapper around baggage to propagate.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.2
 */
public class BaggageToPropagate {

    private final Map<String, String> baggage;

    /**
     * Creates a new instance of {@link BaggageToPropagate} with this baggage.
     * @param baggage baggage entries
     */
    public BaggageToPropagate(Map<String, String> baggage) {
        this.baggage = new HashMap<>(baggage);
    }

    /**
     * Creates a new instance of {@link BaggageToPropagate} with a single baggage entry.
     * @param baggage array of baggage key, value pairs (e.g. "foo", "foo1", "foo2",
     * "foo3" will result in baggage foo:foo1, foo2:foo3
     */
    public BaggageToPropagate(String... baggage) {
        this.baggage = new HashMap<>();
        if (baggage.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "You need an even number of baggage entries. You have [" + baggage.length + "] entries");
        }
        for (int i = 1; i < baggage.length; i = i + 2) {
            String key = baggage[i - 1];
            String value = baggage[i];
            this.baggage.put(key, value);
        }
    }

    public Map<String, String> getBaggage() {
        return baggage;
    }

    @Override
    public String toString() {
        return "BaggageToPropagate{" + "baggage=" + baggage + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaggageToPropagate that = (BaggageToPropagate) o;
        return Objects.equals(baggage, that.baggage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baggage);
    }

}
