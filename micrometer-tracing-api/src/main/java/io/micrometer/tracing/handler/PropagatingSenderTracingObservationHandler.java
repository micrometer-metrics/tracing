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
 * A {@link TracingObservationHandler} called when sending occurred - e.g. of messages or http requests.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public abstract class PropagatingSenderTracingObservationHandler<T> implements TracingObservationHandler<SenderContext<T>> {
    private final Tracer tracer;

    private final Propagator propagator;

    private final Propagator.Setter<T> setter;

    /**
     * Creates a new instance of {@link PropagatingSenderTracingObservationHandler}.
     *
     * @param tracer     the tracer to use to record events
     * @param propagator the mechanism to propagate tracing information into the carrier
     * @param setter     logic used to inject tracing information to the carrier
     */
    public PropagatingSenderTracingObservationHandler(Tracer tracer, Propagator propagator, Propagator.Setter<T> setter) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.setter = setter;
    }

    @Override
    public void onStart(SenderContext<T> context) {
        Span childSpan = createSenderSpan(context);
        this.propagator.inject(childSpan.context(), context.getCarrier(), this.setter);
        getTracingContext(context).setSpan(childSpan);
    }

    public abstract Span createSenderSpan(SenderContext<T> context);

    @Override
    public void onError(SenderContext<T> context) {
        context.getError().ifPresent(throwable -> getRequiredSpan(context).error(throwable));
    }

    @Override
    public void onStop(SenderContext<T> context) {
        Span span = getRequiredSpan(context);
        customizeSenderSpan(span);
        span.end();
    }

    public void customizeSenderSpan(Span span) {

    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

}
