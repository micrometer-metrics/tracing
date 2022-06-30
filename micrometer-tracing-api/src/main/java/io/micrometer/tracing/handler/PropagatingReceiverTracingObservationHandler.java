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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * A {@link TracingObservationHandler} called when receiving occurred - e.g. of messages or http requests.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public abstract class PropagatingReceiverTracingObservationHandler<T> implements TracingObservationHandler<ReceiverContext<T>> {

    private final Tracer tracer;

    private final Propagator propagator;

    /**
     * Creates a new instance of {@link PropagatingReceiverTracingObservationHandler}.
     *
     * @param tracer     the tracer to use to record events
     * @param propagator the mechanism to propagate tracing information from the carrier
     */
    public PropagatingReceiverTracingObservationHandler(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void onStart(ReceiverContext<T> context) {
        Span.Builder extractedSpan = this.propagator.extract(context.getCarrier(), context.getGetter());
        getTracingContext(context).setSpan(customizeExtractedSpan(extractedSpan).start());
    }

    /**
     * Customizes the extracted span (e.g. you can set the {@link Span.Kind} via {@link Span.Builder#kind(Span.Kind)}).
     * @param builder span builder
     * @return span builder
     */
    public abstract Span.Builder customizeExtractedSpan(Span.Builder builder);

    @Override
    public void onError(ReceiverContext<T> context) {
        context.getError().ifPresent(throwable -> getRequiredSpan(context).error(throwable));
    }

    @Override
    public void onStop(ReceiverContext<T> context) {
        Span span = getRequiredSpan(context);
        tagSpan(context, span);
        customizeReceiverSpan(context, span);
        span.end();
    }

    /**
     * Allows to customize the receiver span before reporting it.
     * @param context context
     * @param span span to customize
     */
    public void customizeReceiverSpan(ReceiverContext<T> context, Span span) {

    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

}
