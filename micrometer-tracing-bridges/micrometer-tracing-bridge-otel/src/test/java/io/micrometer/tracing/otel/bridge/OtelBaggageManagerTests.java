/*
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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

class OtelBaggageManagerTests {

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build())
        .build();

    io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

    OtelBaggageManager otelBaggageManager = new OtelBaggageManager(new OtelCurrentTraceContext(),
            Collections.emptyList(), Collections.emptyList());

    @ParameterizedTest
    @ValueSource(strings = { "locale", "LOCALE" })
    void should_preserve_implicit_lookup_selection_for_differently_cased_keys(String currentName) {
        OtelTraceContext target = traceContext(
                io.opentelemetry.api.baggage.Baggage.builder().put("locale", "fr-FR").build());
        Context current = target.context()
            .with(io.opentelemetry.api.baggage.Baggage.builder().put(currentName, "en-US").build());
        try (Scope scope = current.makeCurrent()) {
            Baggage implicit = this.otelBaggageManager.getBaggage("LoCaLe");
            BDDAssertions.then(implicit.name()).isEqualTo(currentName);
            BDDAssertions.then(implicit.get()).isEqualTo("LOCALE".equals(currentName) ? "en-US" : "fr-FR");
            Baggage explicit = Objects.requireNonNull(this.otelBaggageManager.getBaggage(target, "LoCaLe"));
            BDDAssertions.then(explicit.name()).isEqualTo("locale");
            BDDAssertions.then(explicit.get()).isEqualTo("fr-FR");
        }
    }

    @Test
    void should_use_retained_values_for_implicit_and_null_context_bulk_reads() {
        OtelTraceContext target = traceContext(
                io.opentelemetry.api.baggage.Baggage.builder().put("locale", "fr-FR").build());
        Context current = target.context()
            .with(io.opentelemetry.api.baggage.Baggage.builder()
                .put("locale", "en-US")
                .put("request.channel", "web")
                .build());
        try (Scope scope = current.makeCurrent()) {
            BDDAssertions.then(this.otelBaggageManager.getAllBaggage())
                .containsEntry("locale", "fr-FR")
                .containsEntry("request.channel", "web");
            BDDAssertions.then(this.otelBaggageManager.getAllBaggage(null))
                .isEqualTo(this.otelBaggageManager.getAllBaggage());
        }
    }

    @Test
    void should_preserve_differently_cased_keys_in_bulk_reads() {
        OtelTraceContext target = traceContext(
                io.opentelemetry.api.baggage.Baggage.builder().put("Locale", "fr-FR").build());
        Context current = Context.root()
            .with(io.opentelemetry.api.baggage.Baggage.builder().put("locale", "en-US").build());
        try (Scope scope = current.makeCurrent()) {
            BDDAssertions.then(this.otelBaggageManager.getAllBaggage(target))
                .hasSize(2)
                .containsEntry("Locale", "fr-FR")
                .containsEntry("locale", "en-US");
        }
    }

    @Test
    void should_prefer_retained_lookup_then_fall_back_to_current_baggage_and_preserve_metadata() {
        BaggageEntryMetadata metadata = BaggageEntryMetadata.create("propagation=unlimited");
        OtelTraceContext target = traceContext(
                io.opentelemetry.api.baggage.Baggage.builder().put("Locale", "fr-FR", metadata).build());
        Context current = Context.root()
            .with(io.opentelemetry.api.baggage.Baggage.builder()
                .put("locale", "en-US")
                .put("request.channel", "web", metadata)
                .build());
        try (Scope scope = current.makeCurrent()) {
            Baggage retained = Objects.requireNonNull(this.otelBaggageManager.getBaggage(target, "LOCALE"));
            BDDAssertions.then(retained.name()).isEqualTo("Locale");
            BDDAssertions.then(retained.get()).isEqualTo("fr-FR");
            BDDAssertions.then(this.otelBaggageManager.getBaggage(target, "missing")).isNull();
            try (BaggageInScope baggage = retained.makeCurrent()) {
                BDDAssertions
                    .then(Objects.requireNonNull(io.opentelemetry.api.baggage.Baggage.current().asMap().get("Locale"))
                        .getMetadata())
                    .isEqualTo(metadata);
            }
            Baggage fallback = Objects.requireNonNull(this.otelBaggageManager.getBaggage(target, "REQUEST.CHANNEL"));
            BDDAssertions.then(fallback.get()).isEqualTo("web");
            try (BaggageInScope baggage = fallback.makeCurrent()) {
                BDDAssertions
                    .then(Objects
                        .requireNonNull(io.opentelemetry.api.baggage.Baggage.current().asMap().get("request.channel"))
                        .getMetadata())
                    .isEqualTo(metadata);
            }
            BDDAssertions.then(Context.current()).isSameAs(current);
        }
    }

    @Test
    void should_return_independent_mutable_maps_for_empty_and_populated_baggage() {
        try (Scope scope = Context.root().makeCurrent()) {
            Map<String, String> empty = this.otelBaggageManager.getAllBaggage();
            empty.put("locale", "fr-FR");
            BDDAssertions.then(this.otelBaggageManager.getAllBaggage()).isEmpty();
            OtelTraceContext target = traceContext(
                    io.opentelemetry.api.baggage.Baggage.builder().put("locale", "en-US").build());
            Map<String, String> values = this.otelBaggageManager.getAllBaggage(target);
            values.put("locale", "fr-FR");
            BDDAssertions.then(this.otelBaggageManager.getAllBaggage(target)).containsEntry("locale", "en-US");
        }
    }

    private static OtelTraceContext traceContext(io.opentelemetry.api.baggage.Baggage baggage) {
        Span span = Span.wrap(SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7",
                TraceFlags.getSampled(), TraceState.getDefault()));
        return new OtelTraceContext(Context.root().with(baggage), span.getSpanContext(), span);
    }

    @Test
    void should_return_null_baggage_when_there_is_no_baggage_attached_to_a_trace_context() {
        Baggage baggage = otelBaggageManager
            .getBaggage(OtelTraceContext.fromOtel(otelTracer.spanBuilder("foo").startSpan().getSpanContext()), "foo");

        BDDAssertions.then(baggage).isNull();
    }

    @Test
    void should_return_baggage_when_there_is_baggage_attached_to_a_trace_context() {
        Context context = Context.current()
            .with(io.opentelemetry.api.baggage.Baggage.builder().put("foo", "bar").build());

        try (Scope scope = context.makeCurrent()) {
            Span span = otelTracer.spanBuilder("foo").startSpan();
            TraceContext traceContext = OtelTraceContext.fromOtel(span.getSpanContext());
            Baggage baggage = otelBaggageManager.getBaggage(traceContext, "foo");
            try (BaggageInScope baggageInScope = Objects.requireNonNull(baggage).makeCurrent()) {
                BDDAssertions.then(baggage).isNotNull();
                BDDAssertions.then(baggage.get()).isEqualTo("bar");
            }
        }
    }

    @Test
    void should_prefer_trace_context_baggage_over_current_context_baggage() {
        Span span = otelTracer.spanBuilder("foo").startSpan();
        Context traceContextContext = Context.root()
            .with(io.opentelemetry.api.baggage.Baggage.builder()
                .put("conflict", "fromTraceContext")
                .put("onlyTraceContext", "t")
                .build());
        OtelTraceContext traceContext = new OtelTraceContext(traceContextContext, span.getSpanContext(), span);

        Context current = Context.root()
            .with(io.opentelemetry.api.baggage.Baggage.builder()
                .put("conflict", "fromCurrentContext")
                .put("onlyCurrentContext", "c")
                .build());

        try (Scope scope = current.makeCurrent()) {
            Map<String, String> allBaggage = otelBaggageManager.getAllBaggage(traceContext);

            BDDAssertions.then(allBaggage)
                .containsEntry("onlyTraceContext", "t")
                .containsEntry("onlyCurrentContext", "c")
                .containsEntry("conflict", "fromTraceContext");
        }
    }

    @Test
    void should_return_baggage_with_null_value_when_there_is_no_baggage() {
        Baggage baggage = otelBaggageManager.getBaggage("foo");

        BDDAssertions.then(baggage).isNotNull();
        BDDAssertions.then(baggage.get()).isNull();
    }

    @Test
    void should_return_baggage_with_when_there_is_baggage() {
        Context context = Context.current()
            .with(io.opentelemetry.api.baggage.Baggage.builder().put("foo", "bar").build());

        try (Scope scope = context.makeCurrent()) {
            Span span = otelTracer.spanBuilder("foo").startSpan();
            Baggage baggage = otelBaggageManager.getBaggage("foo");
            try (BaggageInScope baggageInScope = baggage.makeCurrent()) {
                BDDAssertions.then(baggage).isNotNull();
                BDDAssertions.then(baggage.get()).isEqualTo("bar");
            }
        }
    }

}
