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

import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * A {@link TracingObservationHandler} called when sending occurred - e.g. of messages or http requests.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class PropagatingSenderTracingObservationHandler<T> implements TracingObservationHandler<SenderContext<T>> {
    private final Tracer tracer;

    private final Propagator propagator;

    /**
     * Creates a new instance of {@link PropagatingSenderTracingObservationHandler}.
     *
     * @param tracer     the tracer to use to record events
     * @param propagator the mechanism to propagate tracing information into the carrier
     */
    public PropagatingSenderTracingObservationHandler(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void onStart(SenderContext<T> context) {
        Span childSpan = createSenderSpan(context);
        this.propagator.inject(childSpan.context(), context.getCarrier(), (carrier, key, value) -> context.getSetter().set(carrier, key, value));
        getTracingContext(context).setSpan(childSpan);
    }

    /**
     * Method to be used to create a sender span.
     * @param context context
     * @return sender span
     */
    public Span createSenderSpan(SenderContext<T> context) {
        // TODO: Rewrite this to use parent spans once they are done
        TracingContext tracingContext = getTracingContext(context);
        Span span = tracingContext.getSpan();
        Span senderSpan = span != null ? getTracer().nextSpan(span) : getTracer().nextSpan();
        String name = context.getContextualName() != null ? context.getContextualName() : context.getName();
        senderSpan.name(name);
        return senderSpan;
    }

    @Override
    public void onError(SenderContext<T> context) {
        context.getError().ifPresent(throwable -> getRequiredSpan(context).error(throwable));
    }

    @Override
    public void onStop(SenderContext<T> context) {
        Span span = getRequiredSpan(context);
        tagSpan(context, span);
        customizeSenderSpan(context, span);
        span.end();
    }

    /**
     * Allows to customize the receiver span before reporting it.
     * @param context context
     * @param span span to customize
     */
    public void customizeSenderSpan(SenderContext<T> context, Span span) {

    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof SenderContext;
    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

}
