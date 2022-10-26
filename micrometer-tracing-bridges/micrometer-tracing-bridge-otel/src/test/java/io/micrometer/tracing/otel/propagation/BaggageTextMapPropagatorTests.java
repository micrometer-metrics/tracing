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
package io.micrometer.tracing.otel.propagation;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.util.*;

class BaggageTextMapPropagatorTests {

    @Test
    void should_append_baggage_to_existing_one() {
        Baggage baggage = Baggage.current().toBuilder().put("foo", "bar").put("baz", "baz2").build();
        Context parent = Context.current().with(baggage);
        List<String> remoteFields = Arrays.asList("foo", "foo2");
        BaggageTextMapPropagator baggageTextMapPropagator = new BaggageTextMapPropagator(remoteFields,
                new OtelBaggageManager(new OtelCurrentTraceContext(), remoteFields, Collections.emptyList()));
        Map<String, String> carrier = new HashMap<>();
        carrier.put("foo", "bar");
        carrier.put("foo2", "bar2");

        Context extracted = baggageTextMapPropagator.extract(parent, carrier, new TextMapGetter<Map<String, String>>() {
            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return remoteFields;
            }

            @Override
            public String get(Map<String, String> carrier, String key) {
                return carrier.get(key);
            }
        });

        Map<String, BaggageEntry> newBaggage = Baggage.fromContext(extracted).asMap();
        BDDAssertions.then(newBaggage).containsOnlyKeys("foo", "foo2", "baz");
    }

}
