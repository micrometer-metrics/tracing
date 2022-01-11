/*
 * Copyright 2021-2021 the original author or authors.
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

package io.micrometer.tracing.handler;

import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.SpanNameUtil;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * TracingRecordingListener that uses the Tracing API to record events.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultTracingRecordingHandler implements TracingRecordingHandler {

    private final Tracer tracer;

    private final List<TracingRecordingHandlerSpanCustomizer> customizers;

    /**
     * Creates a new instance of {@link DefaultTracingRecordingHandler}.
     *
     * @param tracer the tracer to use to record events
     * @param customizers
     */
    public DefaultTracingRecordingHandler(Tracer tracer, List<TracingRecordingHandlerSpanCustomizer> customizers) {
        this.tracer = tracer;
        this.customizers = customizers;
    }

    @Override
    public void onStart(Timer.Sample sample, Timer.HandlerContext context) {
        Span parentSpan = getTracingContext(context).getSpan();
        Span childSpan = parentSpan != null ? getTracer().nextSpan(parentSpan)
                : getTracer().nextSpan();
        childSpan.start();
        getTracingContext(context).setSpan(childSpan);
    }

    @Override
    public void onStop(Timer.Sample sample, Timer.HandlerContext context, Timer timer,
            Duration duration) {
        Span span = getTracingContext(context).getSpan();
        span.name(SpanNameUtil.toLowerHyphen(timer.getId().getName()));
        tagSpan(context, span);
        customizeSpan(context, customizer -> customizer.customizeSpanOnStop(span, sample, context, timer, duration));
        span.end();
    }

    private void customizeSpan(Timer.HandlerContext context, Consumer<TracingRecordingHandlerSpanCustomizer> consumer) {
        List<TracingRecordingHandlerSpanCustomizer> matchingCustomizers = getMatchingCustomizers(context);
        matchingCustomizers.forEach(consumer);
    }

    @Override
    public void onError(Timer.Sample sample, Timer.HandlerContext context,
            Throwable throwable) {
        Span span = getTracingContext(context).getSpan();
        span.error(throwable);
        customizeSpan(context, customizer -> customizer.customizeSpanOnError(span, sample, context, throwable));
    }

    @Override
    public List<TracingRecordingHandlerSpanCustomizer> getTracingRecordingHandlerSpanCustomizers() {
        return this.customizers;
    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

}
