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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.springframework.cloud.sleuth.exporter.SpanFilter;
import org.springframework.cloud.sleuth.exporter.SpanReporter;

/**
 * Composes multiple {@link SpanFilter} into a single {@link SpanExporter}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CompositeSpanExporter implements io.opentelemetry.sdk.trace.export.SpanExporter {

	private final io.opentelemetry.sdk.trace.export.SpanExporter delegate;

	private final List<SpanFilter> filters;

	private final List<SpanReporter> reporters;

	public CompositeSpanExporter(SpanExporter delegate, List<SpanFilter> filters, List<SpanReporter> reporters) {
		this.delegate = delegate;
		this.filters = filters;
		this.reporters = reporters;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		return this.delegate.export(spans.stream().filter(this::shouldProcess).map(spanData -> {
			this.reporters.forEach(reporter -> reporter.report(OtelFinishedSpan.fromOtel(spanData)));
			return spanData;
		}).collect(Collectors.toList()));
	}

	private boolean shouldProcess(SpanData span) {
		for (SpanFilter filter : this.filters) {
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
