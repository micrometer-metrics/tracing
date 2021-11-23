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

import java.time.Duration;

import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.SpanNameUtil;

/**
 * TracingRecordingListener that uses the Tracing API to record events.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultTracingRecordingHandler implements TracingRecordingHandler {

    private final Tracer tracer;

    /**
     * Creates a new instance of {@link DefaultTracingRecordingHandler}.
     *
     * @param tracer the tracer to use to record events
     */
    public DefaultTracingRecordingHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onStart(Timer.Sample sample, Timer.HandlerContext context) {
        Span parentSpan = getTracingContext(context).getSpan();
        Span childSpan = parentSpan != null ? getTracer().nextSpan(parentSpan)
                : getTracer().nextSpan();
        // childSpan.name(sample.getHighCardinalityName())
        childSpan.start();
        getTracingContext(context).setSpan(childSpan);
    }

    @Override
    public void onStop(Timer.Sample sample, Timer.HandlerContext context, Timer timer,
            Duration duration) {
        Span span = getTracingContext(context).getSpan();
        span.name(SpanNameUtil.toLowerHyphen(timer.getId().getName()));
        tagSpan(context, span);
        span.end();
    }

    @Override
    public void onError(Timer.Sample sample, Timer.HandlerContext context,
            Throwable throwable) {
        Span span = getTracingContext(context).getSpan();
        span.error(throwable);
    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

}
