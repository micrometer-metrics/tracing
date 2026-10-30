/*
 * Copyright 2026 the original author or authors.
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

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OtelBaggageInScopeTests {

    private static final Baggage RETAINED_BAGGAGE = Baggage.builder()
        .put("report.format", "pdf")
        .put("locale", "en-US")
        .put("report.id", "report-123")
        .build();

    private static final Baggage CURRENT_BAGGAGE = Baggage.builder()
        .put("report.format", "html")
        .put("locale", "de-DE")
        .put("request.channel", "web")
        .build();

    private static final Baggage MERGED_BAGGAGE = Baggage.builder()
        .put("report.format", "html")
        .put("locale", "fr-FR")
        .put("report.id", "report-123")
        .put("request.channel", "web")
        .build();

    // without a span processor, newer SDK versions only create non-recording spans, and
    // root spans are then all the same invalid span
    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
            .build())
        .build();

    OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();

    Tracer tracer = new OtelTracer(this.openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing"),
            this.currentTraceContext, event -> {
            }, new OtelBaggageManager(this.currentTraceContext, Collections.singletonList("foo"),
                    Collections.emptyList()));

    @AfterEach
    void close() {
        this.openTelemetrySdk.close();
    }

    @Test
    void should_not_store_span_in_scope_in_trace_context_when_creating_baggage_scope_for_it() {
        Span other = this.tracer.nextSpan().name("other").start();
        Span span = this.tracer.nextSpan().name("span").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(other);
                BaggageInScope baggage = this.tracer.createBaggageInScope(span.context(), "foo", "bar")) {
            Context stored = ((OtelTraceContext) span.context()).context();
            then(io.opentelemetry.api.trace.Span.fromContext(stored))
                .isNotSameAs(((OtelTraceContext) other.context()).span);
            then(io.opentelemetry.api.baggage.Baggage.fromContext(stored).getEntryValue("foo")).isEqualTo("bar");
            then(this.tracer.currentSpan()).isEqualTo(other);
            then(this.tracer.getBaggage("foo").get()).isEqualTo("bar");
        }
        span.end();
        other.end();
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_not_store_span_in_scope_in_trace_context_when_setting_baggage_for_it() {
        Span other = this.tracer.nextSpan().name("other").start();
        Span span = this.tracer.nextSpan().name("span").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(other)) {
            this.tracer.createBaggage("foo").set(span.context(), "bar");
            Context stored = ((OtelTraceContext) span.context()).context();
            then(io.opentelemetry.api.trace.Span.fromContext(stored))
                .isNotSameAs(((OtelTraceContext) other.context()).span);
            then(io.opentelemetry.api.baggage.Baggage.fromContext(stored).getEntryValue("foo")).isEqualTo("bar");
        }
        then(this.tracer.getBaggage("foo").get(span.context())).isEqualTo("bar");
        span.end();
        other.end();
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_not_make_span_current_that_was_in_scope_when_baggage_was_set() {
        Span first = this.tracer.nextSpan().name("first").start();
        Span second = this.tracer.nextSpan().name("second").start();
        io.micrometer.tracing.Baggage baggage;
        try (Tracer.SpanInScope ws = this.tracer.withSpan(first)) {
            baggage = this.tracer.createBaggage("foo").set("bar");
        }
        try (Tracer.SpanInScope ws = this.tracer.withSpan(second); BaggageInScope scope = baggage.makeCurrent()) {
            then(this.tracer.currentSpan()).isEqualTo(second);
            then(this.tracer.getBaggage("foo").get()).isEqualTo("bar");
        }
        then(this.tracer.currentSpan()).isNull();
        second.end();
        first.end();
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_merge_current_baggage_over_retained_baggage_and_then_set_entry() {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        try {
            OtelTraceContext context = traceContextWithRetainedBaggage(span);
            try (Scope current = CURRENT_BAGGAGE.makeCurrent()) {
                this.tracer.createBaggage("locale").set(context, "fr-FR");

                then(Baggage.fromContext(context.context()).asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                then(Baggage.current().asMap()).isEqualTo(CURRENT_BAGGAGE.asMap());
            }
            // Setting baggage without opening a scope leaves it stored on the target.
            then(Baggage.fromContext(context.context()).asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_merge_baggage_and_restore_both_contexts_when_explicit_baggage_scope_is_closed() {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        try {
            OtelTraceContext context = traceContextWithRetainedBaggage(span);
            Context retained = context.context();
            try (Scope current = CURRENT_BAGGAGE.makeCurrent()) {
                Context previous = Context.current();
                try (BaggageInScope baggage = this.tracer.createBaggageInScope(context, "locale", "fr-FR")) {
                    then(Baggage.fromContext(context.context()).asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                    then(Baggage.current().asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                }
                then(context.context()).isSameAs(retained);
                then(Context.current()).isSameAs(previous);
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_merge_baggage_and_restore_both_contexts_when_current_span_baggage_scope_is_closed() {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        try {
            OtelTraceContext context = traceContextWithRetainedBaggage(span);
            try (Tracer.SpanInScope spanScope = this.tracer.withSpan(span)) {
                Context retained = context.context();
                try (Scope current = CURRENT_BAGGAGE.makeCurrent()) {
                    Context previous = Context.current();
                    try (BaggageInScope baggage = this.tracer.createBaggageInScope("locale", "fr-FR")) {
                        then(Baggage.fromContext(context.context()).asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                        then(Baggage.current().asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                    }
                    then(context.context()).isSameAs(retained);
                    then(Context.current()).isSameAs(previous);
                }
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_merge_baggage_current_at_activation_and_restore_context_from_before_set() {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        try {
            OtelTraceContext context = traceContextWithRetainedBaggage(span);
            Context retained = context.context();
            io.micrometer.tracing.Baggage baggage;
            try (Scope initial = Baggage.builder().put("report.format", "csv").build().makeCurrent()) {
                baggage = this.tracer.createBaggage("locale").set(context, "fr-FR");
            }
            try (Scope current = CURRENT_BAGGAGE.makeCurrent()) {
                Context previous = Context.current();
                try (BaggageInScope scope = baggage.makeCurrent()) {
                    then(Baggage.fromContext(context.context()).asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                    then(Baggage.current().asMap()).isEqualTo(MERGED_BAGGAGE.asMap());
                }
                then(context.context()).isSameAs(retained);
                then(Context.current()).isSameAs(previous);
            }
        }
        finally {
            span.end();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @SuppressWarnings("deprecation")
    void should_restore_latest_set_value_after_scoping_another_value(boolean explicit) {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        try {
            try (Tracer.SpanInScope spanScope = this.tracer.withSpan(span)) {
                OtelTraceContext context = (OtelTraceContext) span.context();
                io.micrometer.tracing.Baggage baggage = this.tracer.createBaggage("locale", "en-US");
                Context retained = context.context();
                Context current = Context.current();
                try (BaggageInScope scope = explicit ? baggage.makeCurrent(context, "fr-FR")
                        : baggage.makeCurrent("fr-FR")) {
                    then(Baggage.current().getEntryValue("locale")).isEqualTo("fr-FR");
                    then(Baggage.fromContext(context.context()).getEntryValue("locale")).isEqualTo("fr-FR");
                }
                then(context.context()).isSameAs(retained);
                then(Baggage.fromContext(context.context()).getEntryValue("locale")).isEqualTo("en-US");
                then(Context.current()).isSameAs(current);
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_prefer_current_baggage_metadata_when_values_are_equal() {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        try {
            OtelTraceContext context = (OtelTraceContext) span.context();
            context.updateContext(context.context()
                .with(Baggage.builder()
                    .put("report.format", "pdf", BaggageEntryMetadata.create("source=incoming"))
                    .put("report.id", "report-123")
                    .build()));
            Baggage current = Baggage.builder()
                .put("report.format", "pdf", BaggageEntryMetadata.create("source=application"))
                .build();
            Baggage expected = current.toBuilder().put("report.id", "report-123").put("locale", "fr-FR").build();
            Context retained = context.context();
            try (Scope scope = current.makeCurrent()) {
                try (BaggageInScope baggage = this.tracer.createBaggageInScope(context, "locale", "fr-FR")) {
                    then(Baggage.current().asMap()).isEqualTo(expected.asMap());
                    then(Baggage.fromContext(context.context()).asMap()).isEqualTo(expected.asMap());
                }
                then(context.context()).isSameAs(retained);
                then(Baggage.current()).isSameAs(current);
            }
        }
        finally {
            span.end();
        }
    }

    @Test
    void should_preserve_distinct_context_entries_when_both_contexts_have_the_same_baggage() {
        Span span = this.tracer.nextSpan().name("generate-report").start();
        Span other = this.tracer.nextSpan().name("other-report").start();
        try {
            ContextKey<String> requestOrigin = ContextKey.named("request-origin");
            OtelTraceContext context = traceContextWithRetainedBaggage(span);
            context.updateContext(context.context().with(requestOrigin, "incoming"));
            Context retained = context.context();
            try (Tracer.SpanInScope spanScope = this.tracer.withSpan(other);
                    Scope scope = Context.current()
                        .with(RETAINED_BAGGAGE)
                        .with(requestOrigin, "background")
                        .makeCurrent()) {
                Context current = Context.current();
                try (BaggageInScope baggage = this.tracer.createBaggageInScope(context, "locale", "fr-FR")) {
                    then(Baggage.fromContext(context.context()).getEntryValue("locale")).isEqualTo("fr-FR");
                    then(Baggage.current().getEntryValue("locale")).isEqualTo("fr-FR");
                    then(context.context().get(requestOrigin)).isEqualTo("incoming");
                    then(Context.current().get(requestOrigin)).isEqualTo("background");
                    then(this.tracer.currentSpan()).isEqualTo(other);
                }
                then(context.context()).isSameAs(retained);
                then(Context.current()).isSameAs(current);
            }
        }
        finally {
            other.end();
            span.end();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void should_tag_current_span_once_before_attaching_baggage(boolean explicit) {
        Tracer taggingTracer = new OtelTracer(this.openTelemetrySdk.getTracer("baggage-tagging"),
                this.currentTraceContext, event -> {
                }, new OtelBaggageManager(this.currentTraceContext, Collections.singletonList("locale"),
                        Arrays.asList("tenant.id", "LOCALE", "locale")));
        Span span = taggingTracer.nextSpan().name("generate-report").start();
        Span other = taggingTracer.nextSpan().name("handle-request").start();
        io.opentelemetry.api.trace.Span currentSpan = mock(io.opentelemetry.api.trace.Span.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            then(io.opentelemetry.api.trace.Span.current()).isSameAs(currentSpan);
            then(Baggage.current().getEntryValue("locale")).isEqualTo("de-DE");
            return currentSpan;
        }).when(currentSpan).setAttribute("locale", "fr-FR");
        try {
            OtelTraceContext target = (OtelTraceContext) (explicit ? span : other).context();
            try (Tracer.SpanInScope spanScope = taggingTracer.withSpan(other);
                    Scope current = Context.current().with(currentSpan).with(CURRENT_BAGGAGE).makeCurrent()) {
                Context retained = target.context();
                Context previous = Context.current();
                try (BaggageInScope baggage = explicit ? taggingTracer.createBaggageInScope(target, "locale", "fr-FR")
                        : taggingTracer.createBaggageInScope("locale", "fr-FR")) {
                    then(Baggage.current().getEntryValue("locale")).isEqualTo("fr-FR");
                    then(Baggage.fromContext(target.context()).getEntryValue("locale")).isEqualTo("fr-FR");
                    then(io.opentelemetry.api.trace.Span.current()).isSameAs(currentSpan);
                    verify(currentSpan).setAttribute("locale", "fr-FR");
                    then(((ReadableSpan) OtelSpan.toOtel(span)).toSpanData()
                        .getAttributes()
                        .get(AttributeKey.stringKey("locale"))).isNull();
                }
                then(target.context()).isSameAs(retained);
                then(Context.current()).isSameAs(previous);
            }
        }
        finally {
            other.end();
            span.end();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void should_use_tag_fields_configured_when_manager_was_created(boolean explicit) {
        List<String> tagFields = new ArrayList<>(Collections.singletonList("locale"));
        Tracer taggingTracer = new OtelTracer(this.openTelemetrySdk.getTracer("baggage-tagging"),
                this.currentTraceContext, event -> {
                }, new OtelBaggageManager(this.currentTraceContext, Collections.emptyList(), tagFields));
        tagFields.clear();
        tagFields.add("tenant.id");

        Span span = taggingTracer.nextSpan().name("generate-report").start();
        try (Tracer.SpanInScope spanScope = taggingTracer.withSpan(span)) {
            try (BaggageInScope locale = explicit
                    ? taggingTracer.createBaggageInScope(span.context(), "locale", "fr-FR")
                    : taggingTracer.createBaggageInScope("locale", "fr-FR");
                    BaggageInScope tenant = explicit
                            ? taggingTracer.createBaggageInScope(span.context(), "tenant.id", "tenant-123")
                            : taggingTracer.createBaggageInScope("tenant.id", "tenant-123")) {
                then(Baggage.current().getEntryValue("locale")).isEqualTo("fr-FR");
                then(Baggage.current().getEntryValue("tenant.id")).isEqualTo("tenant-123");
                then(((ReadableSpan) OtelSpan.toOtel(span)).toSpanData()
                    .getAttributes()
                    .get(AttributeKey.stringKey("locale"))).isEqualTo("fr-FR");
                then(((ReadableSpan) OtelSpan.toOtel(span)).toSpanData()
                    .getAttributes()
                    .get(AttributeKey.stringKey("tenant.id"))).isNull();
            }
        }
        finally {
            span.end();
        }
    }

    private OtelTraceContext traceContextWithRetainedBaggage(Span span) {
        OtelTraceContext context = (OtelTraceContext) span.context();
        context.updateContext(context.context().with(RETAINED_BAGGAGE));
        return context;
    }

}
