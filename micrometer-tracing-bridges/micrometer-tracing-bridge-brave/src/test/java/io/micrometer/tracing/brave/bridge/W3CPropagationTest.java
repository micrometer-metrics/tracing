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

import brave.baggage.BaggageField;
import brave.internal.baggage.BaggageFields;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.tracing.internal.EncodingUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static brave.propagation.tracecontext.TraceContextPropagation.TRACEPARENT;
import static brave.propagation.tracecontext.TraceContextPropagation.TRACESTATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Test taken from OpenTelemetry.
 */
class W3CPropagationTest {

    private static final String TRACE_ID_BASE16 = "ff000000000000000000000000000041";

    private static final String SPAN_ID_BASE16 = "ff00000000000041";

    private static final boolean SAMPLED_TRACE_OPTIONS = true;

    private static final String TRACEPARENT_HEADER_SAMPLED = "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01";

    private static final String TRACEPARENT_HEADER_NOT_SAMPLED = "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00";

    private static final Propagation.Getter<Map<String, String>, String> getter = Map::get;

    private static final String TRACESTATE_NOT_DEFAULT_ENCODING_WITH_SPACES = "bar=baz   ,    foo=bar";

    private static final String TRACESTATE_HEADER = "sappp=CwAAmEnGj0gThK52TCXZ270X8nBhc3Nwb3J0LWFwcABQT1NU";

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void inject_NullCarrierUsage(W3CPropagationType propagationType) {
        final Map<String, String> carrier = new LinkedHashMap<>();
        TraceContext traceContext = sampledTraceContext().build();
        propagationType.get().injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, null);
        assertThat(carrier).containsExactly(entry(TRACEPARENT, TRACEPARENT_HEADER_SAMPLED));
    }

    private TraceContext.Builder sampledTraceContext() {
        return sampledTraceContext("ff00000000000000", "0000000000000041", "ff00000000000041");
    }

    private TraceContext.Builder sampledTraceContext(String traceIdHigh, String traceId, String spanId) {
        return TraceContext.newBuilder()
            .sampled(SAMPLED_TRACE_OPTIONS)
            .traceIdHigh(EncodingUtils.longFromBase16String(traceIdHigh))
            .traceId(EncodingUtils.longFromBase16String(traceId))
            .spanId(EncodingUtils.longFromBase16String(spanId));
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void inject_SampledContext(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        TraceContext traceContext = sampledTraceContext().build();
        propagationType.get().injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, carrier);
        assertThat(carrier).containsExactly(entry(TRACEPARENT, TRACEPARENT_HEADER_SAMPLED));
    }

    @Test
    void inject_tracestate() {
        Map<String, String> carrier = new LinkedHashMap<>();
        BaggageField traceStateField = BaggageField.create(TRACESTATE);
        BaggageField mybaggageField = BaggageField.create("mybaggage");
        TraceContext traceContext = sampledTraceContext()
            .addExtra(BaggageFields.newFactory(Arrays.asList(traceStateField, mybaggageField), 2).create())
            .build();
        traceStateField.updateValue(traceContext, TRACESTATE_HEADER);
        mybaggageField.updateValue(traceContext, "mybaggagevalue");
        W3CPropagationType.WITH_BAGGAGE.get()
            .injector((ignored, key, value) -> carrier.put(key, value))
            .inject(traceContext, carrier);
        assertThat(carrier).containsEntry("baggage", "mybaggage=mybaggagevalue")
            .containsEntry("tracestate", "sappp=CwAAmEnGj0gThK52TCXZ270X8nBhc3Nwb3J0LWFwcABQT1NU");
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void inject_NotSampledContext(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        TraceContext traceContext = notSampledTraceContext().build();
        propagationType.get().injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, carrier);
        assertThat(carrier).containsExactly(entry(TRACEPARENT, TRACEPARENT_HEADER_NOT_SAMPLED));
    }

    /**
     * see: gh-1809
     */
    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void inject_traceIdShouldBePaddedWithZeros(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        TraceContext traceContext = sampledTraceContext("0000000000000000", "123456789abcdef0", "123456789abcdef1")
            .build();
        propagationType.get().injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, carrier);
        assertThat(carrier)
            .containsExactly(entry(TRACEPARENT, "00-0000000000000000123456789abcdef0-123456789abcdef1-01"));
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_Nothing(W3CPropagationType propagationType) {
        // Context remains untouched.
        assertThat(propagationType.get().extractor(getter).extract(Collections.emptyMap()))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_SampledContext(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, TRACEPARENT_HEADER_SAMPLED);
        assertThat(propagationType.get().extractor(getter).extract(carrier).context())
            .isEqualTo(sharedTraceContext().build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_NullCarrier(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, TRACEPARENT_HEADER_SAMPLED);
        assertThat(propagationType.get().extractor((request, key) -> carrier.get(key)).extract(null).context())
            .isEqualTo(sharedTraceContext().build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_NotSampledContext(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
        assertThat(propagationType.get().extractor(getter).extract(carrier).context())
            .isEqualTo(notSampledTraceContext().shared(true).build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_NotSampledContext_NextVersion(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, "01-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00-02");
        assertThat(propagationType.get().extractor(getter).extract(carrier).context())
            .isEqualTo(sharedTraceContext().build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_NotSampledContext_EmptyTraceState(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
        carrier.put(TRACESTATE, "");
        assertThat(propagationType.get().extractor(getter).extract(carrier).context())
            .isEqualTo(notSampledTraceContext().shared(true).build());
    }

    private TraceContext.Builder notSampledTraceContext() {
        return sampledTraceContext().sampled(false);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_NotSampledContext_TraceStateWithSpaces(W3CPropagationType propagationType) {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
        carrier.put(TRACESTATE, TRACESTATE_NOT_DEFAULT_ENCODING_WITH_SPACES);
        assertThat(propagationType.get().extractor(getter).extract(carrier).context())
            .isEqualTo(sharedTraceContext().sampled(false).build());
    }

    @Test
    void extract_tracestate_shouldNotBePartOfBaggage() {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put(TRACEPARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
        carrier.put(TRACESTATE, TRACESTATE_HEADER);
        carrier.put("baggage", "mybaggage=mybaggagevalue");

        TraceContext context = W3CPropagationType.WITH_BAGGAGE.get().extractor(getter).extract(carrier).context();

        Map<String, String> baggageEntries = baggageEntries(context);
        assertThat(baggageEntries).doesNotContainKey(TRACESTATE).containsEntry("mybaggage", "mybaggagevalue");
    }

    private Map<String, String> baggageEntries(TraceContext flags) {
        if (flags.extra().isEmpty() || !(flags.extra().get(0) instanceof BraveBaggageFields)) {
            throw new AssertionError("Extra doesn't contain BraveBaggageFields as first entry");
        }
        BraveBaggageFields fields = (BraveBaggageFields) flags.extra().get(0);
        return fields.getEntries()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().name(), AbstractMap.SimpleEntry::getValue, (o, o2) -> o2));
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_EmptyHeader(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new LinkedHashMap<>();
        invalidHeaders.put(TRACEPARENT, "");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTraceId(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new LinkedHashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + "abcdefghijklmnopabcdefghijklmnop" + "-" + SPAN_ID_BASE16 + "-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTraceId_Size(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new LinkedHashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "00-" + SPAN_ID_BASE16 + "-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidSpanId(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + "abcdefghijklmnop" + "-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidSpanId_Size(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "00-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTraceFlags(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-gh");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTraceFlags_Size(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-0100");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTracestate_EntriesDelimiter(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
        invalidHeaders.put(TRACESTATE, "foo=bar;test=test");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders).context())
            .isEqualTo(sharedTraceContext().build());
    }

    private TraceContext.Builder sharedTraceContext() {
        return sampledTraceContext().shared(true);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTracestate_KeyValueDelimiter(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
        invalidHeaders.put(TRACESTATE, "foo=bar,test-test");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders).context())
            .isEqualTo(sharedTraceContext().build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTracestate_OneString(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
        invalidHeaders.put(TRACESTATE, "test-test");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders).context())
            .isEqualTo(sampledTraceContext().shared(true).build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidVersion_ff(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "ff-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_InvalidTraceparent_extraTrailing(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_ValidTraceparent_nextVersion_extraTrailing(W3CPropagationType propagationType) {
        Map<String, String> invalidHeaders = new HashMap<>();
        invalidHeaders.put(TRACEPARENT, "01-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00-01");
        assertThat(propagationType.get().extractor(getter).extract(invalidHeaders).context())
            .isEqualTo(sharedTraceContext().build());
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void fieldsList(W3CPropagationType propagationType) {
        assertThat(propagationType.get().keys()).containsExactly(TRACEPARENT, TRACESTATE);
    }

    @Test
    void headerNames() {
        assertThat(TRACEPARENT).isEqualTo("traceparent");
        assertThat(TRACESTATE).isEqualTo("tracestate");
    }

    @ParameterizedTest
    @EnumSource(W3CPropagationType.class)
    void extract_emptyCarrier(W3CPropagationType propagationType) {
        Map<String, String> emptyHeaders = new HashMap<>();
        assertThat(propagationType.get().extractor(getter).extract(emptyHeaders))
            .isSameAs(TraceContextOrSamplingFlags.EMPTY);
    }

    enum W3CPropagationType implements Supplier<W3CPropagation> {

        WITH_BAGGAGE {
            @Override
            public W3CPropagation get() {
                return new W3CPropagation(new BraveBaggageManager(), new ArrayList<>());
            }
        },

        WITHOUT_BAGGAGE {
            @Override
            public W3CPropagation get() {
                return new W3CPropagation();
            }
        }

    }

}
