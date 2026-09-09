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
import io.opentelemetry.context.propagation.TextMapSetter;
import org.assertj.core.api.BDDAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static java.util.Collections.emptyList;

class BaggageTextMapPropagatorTests {

    private static final List<String> REMOTE_FIELDS = Arrays.asList("foo", "foo2");

    private BaggageTextMapPropagator baggageTextMapPropagator;

    private TextMapGetter<Map<String, String>> textMapGetter;

    @BeforeEach
    void setup() {
        baggageTextMapPropagator = new BaggageTextMapPropagator(REMOTE_FIELDS,
                new OtelBaggageManager(new OtelCurrentTraceContext(), REMOTE_FIELDS, emptyList()));
        textMapGetter = textMapGetter(REMOTE_FIELDS);
    }

    @Test
    @SuppressWarnings("NullAway")
    void should_append_baggage_to_existing_one() {
        Baggage baggage = Baggage.empty().toBuilder().put("foo", "undesired").put("lorem", "ipsum").build();
        Context parent = Context.root().with(baggage);

        Map<String, String> carrier = new HashMap<>();
        carrier.put("foo", "bar");
        carrier.put("foo2", "bar2");

        Context extracted = baggageTextMapPropagator.extract(parent, carrier, textMapGetter);

        Map<String, BaggageEntry> extractedBaggage = Baggage.fromContext(extracted).asMap();
        BDDAssertions.then(extractedBaggage).containsOnlyKeys("foo", "foo2", "lorem");

        BDDAssertions.then(extractedBaggage.get("foo").getValue()).isEqualTo("bar");
        BDDAssertions.then(extractedBaggage.get("foo2").getValue()).isEqualTo("bar2");
        BDDAssertions.then(extractedBaggage.get("lorem").getValue()).isEqualTo("ipsum");
    }

    @Test
    void should_not_mix_baggage_with_current_baggage() {
        Baggage currentBaggage = Baggage.empty()
            .toBuilder()
            .put("current-key", "undesired")
            .put("foo", "undesired")
            .build();
        Context currentContext = Context.root().with(currentBaggage);

        Context extracted = currentContext.wrapSupplier(() -> {
            Map<String, String> carrier = new HashMap<>();
            carrier.put("foo", "bar");

            return baggageTextMapPropagator.extract(Context.root(), carrier, textMapGetter(REMOTE_FIELDS));
        }).get();

        Map<String, BaggageEntry> extractedBaggage = Baggage.fromContext(extracted).asMap();
        BDDAssertions.then(extractedBaggage).doesNotContainKey("current-key");
        BDDAssertions.then(Objects.requireNonNull(extractedBaggage.get("foo")).getValue()).isEqualTo("bar");
    }

    @Test
    void should_return_remote_fields_as_fields() {
        BDDAssertions.then(baggageTextMapPropagator.fields()).containsExactlyElementsOf(REMOTE_FIELDS);
    }

    @Test
    void should_inject_matching_baggage_entries_into_carrier() {
        Baggage baggage = Baggage.builder().put("foo", "bar").put("foo2", "bar2").build();

        Map<String, String> carrier = injectWithCurrentBaggage(baggageTextMapPropagator, baggage);

        BDDAssertions.then(carrier)
            .containsOnly(BDDAssertions.entry("foo", "bar"), BDDAssertions.entry("foo2", "bar2"));
    }

    @Test
    void should_inject_nothing_when_no_baggage_key_matches_a_remote_field() {
        Baggage baggage = Baggage.builder().put("unrelated", "value").build();

        Map<String, String> carrier = injectWithCurrentBaggage(baggageTextMapPropagator, baggage);

        BDDAssertions.then(carrier).isEmpty();
    }

    @Test
    void should_inject_nothing_when_no_remote_fields_are_configured() {
        BaggageTextMapPropagator propagator = new BaggageTextMapPropagator(emptyList(),
                new OtelBaggageManager(new OtelCurrentTraceContext(), emptyList(), emptyList()));
        Baggage baggage = Baggage.builder().put("foo", "bar").build();

        Map<String, String> carrier = injectWithCurrentBaggage(propagator, baggage);

        BDDAssertions.then(carrier).isEmpty();
    }

    @Test
    void should_match_remote_fields_against_baggage_keys_ignoring_case() {
        List<String> remoteFields = Collections.singletonList("Foo");
        BaggageTextMapPropagator propagator = new BaggageTextMapPropagator(remoteFields,
                new OtelBaggageManager(new OtelCurrentTraceContext(), remoteFields, emptyList()));
        Baggage baggage = Baggage.builder().put("foo", "bar").build();

        Map<String, String> carrier = injectWithCurrentBaggage(propagator, baggage);

        BDDAssertions.then(carrier).containsOnly(BDDAssertions.entry("foo", "bar"));
    }

    @Test
    void should_inject_using_the_baggage_key_casing_not_the_remote_field_casing() {
        List<String> remoteFields = Collections.singletonList("foo");
        BaggageTextMapPropagator propagator = new BaggageTextMapPropagator(remoteFields,
                new OtelBaggageManager(new OtelCurrentTraceContext(), remoteFields, emptyList()));
        Baggage baggage = Baggage.builder().put("FOO", "bar").build();

        Map<String, String> carrier = injectWithCurrentBaggage(propagator, baggage);

        BDDAssertions.then(carrier).containsOnly(BDDAssertions.entry("FOO", "bar"));
    }

    @Test
    void should_leave_baggage_unchanged_when_carrier_has_no_matching_fields() {
        Baggage baggage = Baggage.builder().put("lorem", "ipsum").build();
        Context parent = Context.root().with(baggage);

        Map<String, String> carrier = new HashMap<>();
        carrier.put("unrelated", "value");

        Context extracted = baggageTextMapPropagator.extract(parent, carrier, textMapGetter);

        BDDAssertions.then(Baggage.fromContext(extracted).asMap()).containsOnlyKeys("lorem");
        BDDAssertions.then(Objects.requireNonNull(Baggage.fromContext(extracted).asMap().get("lorem")).getValue())
            .isEqualTo("ipsum");
    }

    @Test
    void should_not_attach_baggage_when_nothing_was_extracted() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("unrelated", "value");

        Context extracted = baggageTextMapPropagator.extract(Context.root(), carrier, textMapGetter);

        BDDAssertions.then(extracted).isSameAs(Context.root());
        BDDAssertions.then(Baggage.fromContextOrNull(extracted)).isNull();
    }

    @Test
    void should_tolerate_duplicate_remote_fields() {
        List<String> remoteFields = Arrays.asList("foo", "foo");
        BaggageTextMapPropagator propagator = new BaggageTextMapPropagator(remoteFields,
                new OtelBaggageManager(new OtelCurrentTraceContext(), remoteFields, emptyList()));

        Map<String, String> carrier = new HashMap<>();
        carrier.put("foo", "bar");

        Context extracted = propagator.extract(Context.root(), carrier, textMapGetter(remoteFields));

        BDDAssertions.then(Objects.requireNonNull(Baggage.fromContext(extracted).asMap().get("foo")).getValue())
            .isEqualTo("bar");
    }

    @Test
    void should_extract_nothing_when_carrier_is_null() {
        Context extracted = baggageTextMapPropagator.extract(Context.root(), null, textMapGetter);

        BDDAssertions.then(Baggage.fromContext(extracted).isEmpty()).isTrue();
    }

    private Map<String, String> injectWithCurrentBaggage(BaggageTextMapPropagator propagator, Baggage baggage) {
        TextMapSetter<Map<String, String>> setter = (target, key, value) -> {
            if (target != null) {
                target.put(key, value);
            }
        };
        return Context.root().with(baggage).wrapSupplier(() -> {
            Map<String, String> carrier = new HashMap<>();
            propagator.inject(Context.root(), carrier, setter);
            return carrier;
        }).get();
    }

    private TextMapGetter<Map<String, String>> textMapGetter(final List<String> remoteFields) {
        return new TextMapGetter<Map<String, String>>() {
            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return remoteFields;
            }

            @Override
            public @Nullable String get(@Nullable Map<String, String> carrier, String key) {
                return carrier == null ? null : carrier.get(key);
            }
        };
    }

}
