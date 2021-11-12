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

package io.micrometer.tracing.reporter.wavefront;

import java.util.Collection;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * A {@link SpanExporter} that sends spans to Wavefront.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class WavefrontOtelSpanHandler implements SpanExporter {

    private final WavefrontSpringObservabilitySpanHandler spanHandler;

    /**
     * @param spanHandler wavefront span handler
     */
    public WavefrontOtelSpanHandler(WavefrontSpringObservabilitySpanHandler spanHandler) {
        this.spanHandler = spanHandler;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        spans.forEach(spanData -> spanHandler.end(traceContext(spanData), OtelFinishedSpan.fromOtel(spanData)));
        return CompletableResultCode.ofSuccess();
    }

    private TraceContext traceContext(SpanData spanData) {
        return new TraceContext() {
            @Override
            public String traceId() {
                return spanData.getTraceId();
            }

            @Override
            public String parentId() {
                return spanData.getParentSpanId();
            }

            @Override
            public String spanId() {
                return spanData.getSpanId();
            }

            @Override
            public Boolean sampled() {
                return spanData.getSpanContext().isSampled();
            }
        };
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        spanHandler.close();
        return CompletableResultCode.ofSuccess();
    }

}
