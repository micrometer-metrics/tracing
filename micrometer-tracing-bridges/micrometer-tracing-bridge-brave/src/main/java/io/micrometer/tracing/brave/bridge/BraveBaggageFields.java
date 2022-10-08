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
package io.micrometer.tracing.brave.bridge;

import io.micrometer.tracing.BaggageInScope;

import java.util.AbstractMap;
import java.util.List;
import java.util.Objects;

class BraveBaggageFields {

    private final List<AbstractMap.SimpleEntry<BaggageInScope, String>> entries;

    BraveBaggageFields(List<AbstractMap.SimpleEntry<BaggageInScope, String>> entries) {
        this.entries = entries;
    }

    List<AbstractMap.SimpleEntry<BaggageInScope, String>> getEntries() {
        return entries;
    }

    @Override
    public String toString() {
        return "BraveBaggageFields{" + "entries=" + entries + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BraveBaggageFields that = (BraveBaggageFields) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

}
