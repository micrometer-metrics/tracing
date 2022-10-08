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
package io.micrometer.tracing.otel.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import io.micrometer.tracing.exporter.SpanFilter;
import io.micrometer.tracing.exporter.SpanReporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Wraps the {@link SpanExporter} delegate with additional predicate, reporting and
 * filtering logic.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CompositeSpanExporter implements io.opentelemetry.sdk.trace.export.SpanExporter {

    private final io.opentelemetry.sdk.trace.export.SpanExporter delegate;

    private final List<SpanExportingPredicate> predicates;

    private final List<SpanReporter> reporters;

    private final List<SpanFilter> spanFilters;

    /**
     * Creates a new instance of {@link CompositeSpanExporter}.
     * @param delegate a {@link SpanExporter} delegate
     * @param predicates predicates that decide which spans should be exported
     * @param reporters reporters that export spans
     * @param spanFilters filters that mutate spans before reporting them
     */
    public CompositeSpanExporter(SpanExporter delegate, List<SpanExportingPredicate> predicates,
            List<SpanReporter> reporters, List<SpanFilter> spanFilters) {
        this.delegate = delegate;
        this.predicates = predicates == null ? Collections.emptyList() : predicates;
        this.reporters = reporters == null ? Collections.emptyList() : reporters;
        this.spanFilters = spanFilters == null ? Collections.emptyList() : spanFilters;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return this.delegate.export(spans.stream().filter(this::shouldProcess).map(spanData -> {
            FinishedSpan finishedSpan = OtelFinishedSpan.fromOtel(spanData);
            for (SpanFilter spanFilter : spanFilters) {
                finishedSpan = spanFilter.map(finishedSpan);
            }
            return OtelFinishedSpan.toOtel(finishedSpan);
        }).peek(spanData -> this.reporters.forEach(reporter -> reporter.report(OtelFinishedSpan.fromOtel(spanData))))
                .collect(Collectors.toList()));
    }

    private boolean shouldProcess(SpanData span) {
        for (SpanExportingPredicate filter : this.predicates) {
            if (!filter.isExportable(OtelFinishedSpan.fromOtel(span))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CompletableResultCode flush() {
        return this.delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return this.delegate.shutdown();
    }

}
