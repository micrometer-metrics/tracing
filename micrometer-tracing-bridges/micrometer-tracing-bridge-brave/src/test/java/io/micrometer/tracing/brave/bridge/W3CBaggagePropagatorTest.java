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
import brave.baggage.*;
import brave.context.slf4j.MDCScopeDecorator;
import brave.internal.baggage.BaggageFields;
import brave.propagation.*;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test taken from OpenTelemetry.
 */
class W3CBaggagePropagatorTest {

    W3CBaggagePropagator propagator = new W3CBaggagePropagator(new BraveBaggageManager(), Collections.emptyList());

    @Test
    void fields() {
        assertThat(propagator.keys()).containsExactly("baggage");
    }

    @Test
    void extract_noBaggageHeader() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        assertThat(contextWithBaggage).isEqualTo(contextWithBraveBaggageFields(context));
    }

    private TraceContextOrSamplingFlags contextWithBraveBaggageFields(TraceContextOrSamplingFlags context) {
        return context.toBuilder().addExtra(new BraveBaggageFields(Collections.emptyList())).build();
    }

    @Test
    void extract_emptyBaggageHeader() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "");

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        assertThat(contextWithBaggage).isEqualTo(contextWithBraveBaggageFields(context));
    }

    @Test
    void extract_singleEntry() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key=value");

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        Map<String, String> baggageEntries = baggageEntries(contextWithBaggage);
        assertThat(baggageEntries).hasSize(1).containsEntry("key", "value");
    }

    private TraceContextOrSamplingFlags context() {
        return TraceContextOrSamplingFlags
            .newBuilder(TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build())
            .build();
    }

    @Test
    void extract_multiEntry() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key1=value1,key2=value2");

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        Map<String, String> baggageEntries = baggageEntries(contextWithBaggage);
        assertThat(baggageEntries).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", "value2");
    }

    @Test
    void extract_duplicateKeys() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key=value1,key=value2");

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        Map<String, String> baggageEntries = baggageEntries(contextWithBaggage);
        assertThat(baggageEntries).hasSize(1).containsEntry("key", "value2");
    }

    @Test
    void extract_fullComplexities() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage",
                "key1= value1; metadata-key = value; othermetadata, " + "key2 =value2 , key3 =\tvalue3 ; ");

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        Map<String, String> baggageEntries = baggageEntries(contextWithBaggage);
        assertThat(baggageEntries).hasSize(3)
            .containsEntry("key1", "value1")
            .containsEntry("key2", "value2")
            .containsEntry("key3", "value3");
    }

    private Map<String, String> baggageEntries(TraceContextOrSamplingFlags flags) {
        if (flags.context().extra().isEmpty() || !(flags.context().extra().get(0) instanceof BraveBaggageFields)) {
            throw new AssertionError("Extra doesn't contain BraveBaggageFields as first entry");
        }
        BraveBaggageFields fields = (BraveBaggageFields) flags.context().extra().get(0);
        return fields.getEntries()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().name(), AbstractMap.SimpleEntry::getValue, (o, o2) -> o2));
    }

    /**
     * It would be cool if we could replace this with a fuzzer to generate tons of crud
     * data, to make sure we don't blow up with it.
     */
    @Test
    @Disabled("We don't support additional data")
    void extract_invalidHeader() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key1= v;alsdf;-asdflkjasdf===asdlfkjadsf ,,a sdf9asdf-alue1; metadata-key = "
                + "value; othermetadata, key2 =value2 , key3 =\tvalue3 ; ");

        TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

        Map<String, String> baggageEntries = baggageEntries(contextWithBaggage);
        assertThat(baggageEntries).isEmpty();
    }

    @Test
    void inject_noBaggage() {
        TraceContextOrSamplingFlags context = context();
        Map<String, String> carrier = new HashMap<>();

        propagator.injector((Propagation.Setter<Map<String, String>, String>) Map::put)
            .inject(context.context(), carrier);

        assertThat(carrier).isEmpty();
    }

    @Test
    void inject() {
        TraceContextOrSamplingFlags.Builder builder = context().toBuilder();
        BaggageField nometa = BaggageField.create("nometa");
        BaggageField meta = BaggageField.create("meta");
        builder.addExtra(BaggageFields.newFactory(Arrays.asList(nometa, meta), 10).create());
        TraceContextOrSamplingFlags context = builder.build();
        nometa.updateValue(context, "nometa-value");
        meta.updateValue(context, "meta-value;somemetadata; someother=foo");
        Map<String, String> carrier = new HashMap<>();

        propagator.injector((Propagation.Setter<Map<String, String>, String>) Map::put)
            .inject(context.context(), carrier);

        assertThat(carrier).containsExactlyInAnyOrderEntriesOf(
                singletonMap("baggage", "nometa=nometa-value,meta=meta-value;somemetadata; someother=foo"));
    }

    @Test
    void works_with_scopes_and_observations() {
        // Baggage
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key=value,key2=value2");
        carrier.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        // carrier.put("key", "value");
        // carrier.put("key2", "value2");
        // carrier.put("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");

        // Brave with W3C
        BraveBaggageManager braveBaggageManager = new BraveBaggageManager();
        ThreadLocalCurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(correlationScopeDecorator(mdcCorrelationScopeDecoratorBuilder()))
            .build();
        Tracing tracing = Tracing.newBuilder()
            .propagationFactory(micrometerTracingPropagationWithBaggage(w3cPropagationFactory(braveBaggageManager)))
            // .propagationFactory(micrometerTracingPropagationWithBaggage(b3PropagationFactory()))
            .currentTraceContext(currentTraceContext)
            .build();
        Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
                braveBaggageManager);
        BravePropagator bravePropagator = new BravePropagator(tracing);

        // Observation
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig()
            .observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                    new PropagatingReceiverTracingObservationHandler<>(tracer, bravePropagator),
                    new DefaultTracingObservationHandler(tracer)));

        try {
            ReceiverContext<Map<String, String>> receiverContext = new ReceiverContext<>((c, key) -> c.get(key));
            receiverContext.setCarrier(carrier);
            Observation parent = Observation.start("foo", () -> receiverContext, registry);
            parent.scoped(() -> {
                assertThat(MDC.getCopyOfContextMap()).containsEntry("key", "value")
                    .containsEntry("key2", "value2")
                    .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
                Observation child = Observation.start("bar", registry);
                child.scoped(() -> {
                    assertThat(MDC.getCopyOfContextMap()).containsEntry("key", "value")
                        .containsEntry("key2", "value2")
                        .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
                });
            });
        }
        finally {
            tracing.close();
        }

    }

    @Test
    void works_with_scopes_and_observations_and_ignores_baggage() {
        // Baggage
        Map<String, String> carrier = new HashMap<>();
        carrier.put("baggage", "key=value,key2=value2");
        carrier.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        // carrier.put("key", "value");
        // carrier.put("key2", "value2");
        // carrier.put("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");

        // Brave with W3C
        ThreadLocalCurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(correlationScopeDecorator(mdcCorrelationScopeDecoratorBuilder()))
            .build();
        Tracing tracing = Tracing.newBuilder()
            .propagationFactory(micrometerTracingPropagationWithBaggage(w3cPropagationFactoryWithoutBaggage()))
            // .propagationFactory(micrometerTracingPropagationWithBaggage(b3PropagationFactory()))
            .currentTraceContext(currentTraceContext)
            .build();
        Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()));
        BravePropagator bravePropagator = new BravePropagator(tracing);

        // Observation
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig()
            .observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                    new PropagatingReceiverTracingObservationHandler<>(tracer, bravePropagator),
                    new DefaultTracingObservationHandler(tracer)));

        try {
            ReceiverContext<Map<String, String>> receiverContext = new ReceiverContext<>((c, key) -> c.get(key));
            receiverContext.setCarrier(carrier);
            Observation parent = Observation.start("foo", () -> receiverContext, registry);
            parent.scoped(() -> {
                assertThat(MDC.getCopyOfContextMap()).doesNotContainEntry("key", "value")
                    .doesNotContainEntry("key2", "value2")
                    .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
                Observation child = Observation.start("bar", registry);
                child.scoped(() -> {
                    assertThat(MDC.getCopyOfContextMap()).doesNotContainEntry("key", "value")
                        .doesNotContainEntry("key2", "value2")
                        .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
                });
            });
        }
        finally {
            tracing.close();
        }
    }

    private BaggagePropagation.FactoryBuilder w3cPropagationFactory(BraveBaggageManager baggageManager) {
        return BaggagePropagation.newFactoryBuilder(new W3CPropagation(baggageManager, Collections.emptyList()));
    }

    private BaggagePropagation.FactoryBuilder w3cPropagationFactoryWithoutBaggage() {
        return BaggagePropagation.newFactoryBuilder(new W3CPropagation());
    }

    private BaggagePropagation.FactoryBuilder b3PropagationFactory() {
        return BaggagePropagation.newFactoryBuilder(
                B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build());
    }

    private Propagation.Factory micrometerTracingPropagationWithBaggage(
            BaggagePropagation.FactoryBuilder factoryBuilder) {
        List<String> remoteFields = Arrays.asList("key", "key2", "tracestate");
        for (String fieldName : remoteFields) {
            factoryBuilder.add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create(fieldName)));
        }
        return factoryBuilder.build();
    }

    private CorrelationScopeDecorator.Builder mdcCorrelationScopeDecoratorBuilder() {
        return MDCScopeDecorator.newBuilder();
    }

    private CurrentTraceContext.ScopeDecorator correlationScopeDecorator(CorrelationScopeDecorator.Builder builder) {
        List<String> correlationFields = Arrays.asList("key", "key2");
        for (String field : correlationFields) {
            builder.add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageField.create(field))
                .flushOnUpdate()
                .build());
        }
        return builder.build();
    }

}
