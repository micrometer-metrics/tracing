/*
 * Copyright 2013-2020 the original author or authors.
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

package io.micrometer.tracing.otel.propagation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeTextMapPropagatorTest {

    @Test
    void extract_onlyBaggage() {
        CompositeTextMapPropagator compositeTextMapPropagator = new CompositeTextMapPropagator(
                new CompositeTextMapPropagator.PropagationSupplier() {
                    @Override
                    public <T> ObjectProvider<T> getProvider(Class<T> clazz) {
                        return Supplier::get;
                    }
                }, Collections.singletonList(PropagationType.W3C));

        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key=value");
        Context result = compositeTextMapPropagator.extract(Context.root(), carrier, new MapGetter());

        assertThat(Baggage.fromContextOrNull(result)).isNotNull();
        assertThat(Baggage.fromContext(result)).isEqualTo(Baggage.builder().put("key", "value").build());
    }

    @Test
    void should_map_propagaotr_string_class_names_to_actual_classes() {
        CompositeTextMapPropagator propagator = new CompositeTextMapPropagator(
                new CompositeTextMapPropagator.PropagationSupplier() {
                    @Override
                    public <T> ObjectProvider<T> getProvider(Class<T> clazz) {
                        return Supplier::get;
                    }
                }, Collections.emptyList());

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(propagator.awsClass()).isEqualTo(AwsXrayPropagator.class.getName());
        softly.assertThat(propagator.b3Class()).isEqualTo(B3Propagator.class.getName());
        softly.assertThat(propagator.jaegerClass()).isEqualTo(JaegerPropagator.class.getName());
        softly.assertThat(propagator.otClass()).isEqualTo(OtTracePropagator.class.getName());
        softly.assertAll();
    }

    private static class MapGetter implements TextMapGetter<Map<String, String>> {

        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }

    }

}
