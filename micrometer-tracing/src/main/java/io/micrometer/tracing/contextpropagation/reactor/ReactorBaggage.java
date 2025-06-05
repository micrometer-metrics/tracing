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
package io.micrometer.tracing.contextpropagation.reactor;

import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import io.micrometer.tracing.contextpropagation.BaggageToPropagate;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Helper class to work with Reactor {@link Context} and {@link BaggageToPropagate}.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.2
 */
public final class ReactorBaggage {

    private ReactorBaggage() {
        throw new IllegalStateException("Can't instantiate a utility class");
    }

    /**
     * Appends a single baggage entry to the existing {@link Function}.
     * @param key baggage key
     * @param value baggage value
     * @return function transforming {@link Context} to append the baggage entry to the
     * existing baggage
     */
    public static Function<Context, Context> append(String key, String value) {
        return context -> append(context, key, value);
    }

    /**
     * Appends baggage entries to the existing {@link Function}.
     * @param baggage baggage entries
     * @return function transforming {@link Context} to append the baggage entries to the
     * existing baggage
     */
    public static Function<Context, Context> append(Map<String, String> baggage) {
        return context -> append(context, baggage);
    }

    private static Context append(Context context, Map<String, String> baggage) {
        BaggageToPropagate baggageToPropagate = context.getOrDefault(ObservationAwareBaggageThreadLocalAccessor.KEY,
                null);
        Map<String, String> mergedBaggage = new HashMap<>();
        if (baggageToPropagate != null) {
            mergedBaggage.putAll(baggageToPropagate.getBaggage());
        }
        mergedBaggage.putAll(baggage);
        BaggageToPropagate merged = new BaggageToPropagate(mergedBaggage);
        return context.put(ObservationAwareBaggageThreadLocalAccessor.KEY, merged);
    }

    private static Context append(Context context, String key, String value) {
        BaggageToPropagate baggageToPropagate = context.getOrDefault(ObservationAwareBaggageThreadLocalAccessor.KEY,
                null);
        Map<String, String> mergedBaggage = new HashMap<>();
        if (baggageToPropagate != null) {
            mergedBaggage.putAll(baggageToPropagate.getBaggage());
        }
        mergedBaggage.put(key, value);
        BaggageToPropagate merged = new BaggageToPropagate(mergedBaggage);
        return context.put(ObservationAwareBaggageThreadLocalAccessor.KEY, merged);
    }

}
