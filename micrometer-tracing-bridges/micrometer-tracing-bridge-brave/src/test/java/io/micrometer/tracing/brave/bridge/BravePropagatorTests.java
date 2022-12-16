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

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.StrictCurrentTraceContext;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class BravePropagatorTests {

    CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();

    Tracing tracing = Tracing.newBuilder()
            .propagationFactory(micrometerTracingPropagationWithBaggage(b3PropagationFactory()))
            .currentTraceContext(currentTraceContext).build();

    BraveBaggageManager braveBaggageManager = new BraveBaggageManager();

    Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
            braveBaggageManager);

    BravePropagator bravePropagator = new BravePropagator(tracing);

    @Test
    void should_propagate_context_with_trace_and_baggage() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("b3", "00-3e425f2373d89640bde06e8285e7bf88-9a5fdefae3abb440-00");
        carrier.put("foo", "bar");
        Span.Builder extract = bravePropagator.extract(carrier, Map::get);

        Span span = extract.start();

        BaggageInScope baggage = braveBaggageManager.getBaggage(span.context(), "foo").makeCurrent();
        try {
            BDDAssertions.then(baggage.get(span.context())).isEqualTo("bar");
        }
        finally {
            baggage.close();
        }
    }

    @Test
    void should_propagate_context_with_baggage_only() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("foo", "bar");
        Span.Builder extract = bravePropagator.extract(carrier, Map::get);

        Span span = extract.start();

        BaggageInScope baggage = braveBaggageManager.getBaggage(span.context(), "foo").makeCurrent();
        try {
            BDDAssertions.then(baggage.get(span.context())).isEqualTo("bar");
        }
        finally {
            baggage.close();
        }
    }

    private BaggagePropagation.FactoryBuilder b3PropagationFactory() {
        return BaggagePropagation.newFactoryBuilder(
                B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build());
    }

    private Propagation.Factory micrometerTracingPropagationWithBaggage(
            BaggagePropagation.FactoryBuilder factoryBuilder) {
        return factoryBuilder.add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("foo")))
                .build();
    }

}
