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

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import io.micrometer.tracing.exporter.SpanFilter;
import io.micrometer.tracing.exporter.SpanReporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates a {@link SpanExporter} with additional predicate, reporting and filtering
 * logic.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CompositeSpanExporter implements io.opentelemetry.sdk.trace.export.SpanExporter {

    private final Collection<io.opentelemetry.sdk.trace.export.SpanExporter> exporters;

    private final List<SpanExportingPredicate> predicates;

    private final List<SpanReporter> reporters;

    private final List<SpanFilter> spanFilters;

    /**
     * Creates a new instance of {@link CompositeSpanExporter}.
     * @param exporters {@link SpanExporter} exporters
     * @param predicates predicates that decide which spans should be exported
     * @param reporters reporters that export spans
     * @param spanFilters filters that mutate spans before reporting them
     */
    public CompositeSpanExporter(Collection<io.opentelemetry.sdk.trace.export.SpanExporter> exporters,
            List<SpanExportingPredicate> predicates, List<SpanReporter> reporters, List<SpanFilter> spanFilters) {
        this.exporters = exporters;
        this.predicates = predicates == null ? Collections.emptyList() : predicates;
        this.reporters = reporters == null ? Collections.emptyList() : reporters;
        this.spanFilters = spanFilters == null ? Collections.emptyList() : spanFilters;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> changedSpanData = spans.stream().filter(this::shouldProcess).map(spanData -> {
            FinishedSpan finishedSpan = OtelFinishedSpan.fromOtel(spanData);
            for (SpanFilter spanFilter : spanFilters) {
                finishedSpan = spanFilter.map(finishedSpan);
            }
            return OtelFinishedSpan.toOtel(finishedSpan);
        }).collect(Collectors.toList());
        List<CompletableResultCode> results = new ArrayList<>();
        changedSpanData.forEach(spanData -> {
            this.reporters.forEach(reporter -> {
                try {
                    reporter.report(OtelFinishedSpan.fromOtel(spanData));
                    results.add(CompletableResultCode.ofSuccess());
                }
                catch (Exception ex) {
                    results.add(CompletableResultCode.ofFailure());
                }
            });
        });
        this.exporters.forEach(spanExporter -> results.add(spanExporter.export(changedSpanData)));
        return CompletableResultCode.ofAll(results);
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
        return CompletableResultCode
                .ofAll(this.exporters.stream().map(SpanExporter::flush).collect(Collectors.toList()));
    }

    @Override
    public CompletableResultCode shutdown() {
        List<CompletableResultCode> results = new ArrayList<>();
        for (SpanReporter reporter : this.reporters) {
            try {
                reporter.close();
                results.add(CompletableResultCode.ofSuccess());
            }
            catch (Exception ex) {
                results.add(CompletableResultCode.ofFailure());
            }
        }
        results.addAll(this.exporters.stream().map(SpanExporter::shutdown).collect(Collectors.toList()));
        return CompletableResultCode.ofAll(results);
    }

}
