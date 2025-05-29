/**
 * Copyright 2025 the original author or authors.
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
package io.micrometer.tracing.contextpropagation.reactor;

import io.micrometer.tracing.contextpropagation.BaggageToPropagate;
import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

class ReactorBaggageTests {

    @Test
    void should_append_single_baggage_entry_to_context() {
        Context updatedContext = ReactorBaggage.append("foo", "bar").apply(Context.empty());

        BaggageToPropagate baggage = updatedContext.get(ObservationAwareBaggageThreadLocalAccessor.KEY);
        then(baggage).isNotNull();
        then(baggage.getBaggage()).hasSize(1);
        then(baggage.getBaggage()).containsEntry("foo", "bar");
    }

    @Test
    void should_append_multiple_baggage_entries_to_context() {
        Map<String, String> baggageEntries = new HashMap<>();
        baggageEntries.put("foo", "bar");
        baggageEntries.put("baz", "bar2");

        Context updatedContext = ReactorBaggage.append(baggageEntries).apply(Context.empty());

        BaggageToPropagate baggage = updatedContext.get(ObservationAwareBaggageThreadLocalAccessor.KEY);
        then(baggage).isNotNull();
        then(baggage.getBaggage()).hasSize(2);
        then(baggage.getBaggage()).containsExactlyInAnyOrderEntriesOf(baggageEntries);
    }

    @Test
    void should_merge_existing_baggage_with_new_entries() {
        Map<String, String> initialBaggage = new HashMap<>();
        initialBaggage.put("foo", "bar");
        initialBaggage.put("baz", "bar2");
        Context context = Context.of(ObservationAwareBaggageThreadLocalAccessor.KEY,
                new BaggageToPropagate(initialBaggage));

        Map<String, String> newBaggage = new HashMap<>();
        newBaggage.put("foo", "bar1");

        Context updatedContext = ReactorBaggage.append(newBaggage).apply(context);

        BaggageToPropagate baggage = updatedContext.get(ObservationAwareBaggageThreadLocalAccessor.KEY);
        then(baggage).isNotNull();
        then(baggage.getBaggage()).hasSize(2);
        then(baggage.getBaggage()).containsEntry("foo", "bar1").containsEntry("baz", "bar2");
    }

    @Test
    void should_merge_existing_baggage_with_single_entry() {
        Map<String, String> initialBaggage = new HashMap<>();
        initialBaggage.put("foo", "bar");
        initialBaggage.put("baz", "bar2");
        Context context = Context.of(ObservationAwareBaggageThreadLocalAccessor.KEY,
                new BaggageToPropagate(initialBaggage));

        Context updatedContext = ReactorBaggage.append("foo", "bar1").apply(context);

        BaggageToPropagate baggage = updatedContext.get(ObservationAwareBaggageThreadLocalAccessor.KEY);
        then(baggage).isNotNull();
        then(baggage.getBaggage()).hasSize(2);
        then(baggage.getBaggage()).containsEntry("foo", "bar1").containsEntry("baz", "bar2");
    }

}
