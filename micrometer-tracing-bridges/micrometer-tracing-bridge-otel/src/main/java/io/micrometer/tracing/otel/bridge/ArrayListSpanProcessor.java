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

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Stores spans in a queue.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @deprecated in favour of {@code opentelemetry-sdk-testing} artifact's
 */
@Deprecated
public class ArrayListSpanProcessor implements SpanProcessor, SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(ArrayListSpanProcessor.class);

    private final Queue<SpanData> spans = new ConcurrentLinkedQueue<>();

    @Override
    public void onStart(Context parent, ReadWriteSpan span) {
        logWarn();
    }

    private static void logWarn() {
        log.warn(
                "ArrayListSpanProcessor is deprecated and scheduled for removal in 1.7.0. Please use InMemorySpanExporter");
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        logWarn();
        this.spans.add(span.toSpanData());
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        logWarn();
        this.spans.addAll(spans.stream().filter(f -> !this.spans.contains(f)).collect(Collectors.toList()));
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public void close() {
        shutdown().join(10, SECONDS);
    }

    /**
     * Returns the first collected span.
     * @return the first span
     */
    public SpanData takeLocalSpan() {
        logWarn();
        return this.spans.poll();
    }

    /**
     * Returns collected spans.
     * @return collected spans
     */
    public Queue<SpanData> spans() {
        logWarn();
        return this.spans;
    }

    /**
     * Clears the stored spans.
     */
    public void clear() {
        logWarn();
        this.spans.clear();
    }

    @Override
    public String toString() {
        return "ArrayListSpanProcessor{" + "spans=" + spans + '}';
    }

}
