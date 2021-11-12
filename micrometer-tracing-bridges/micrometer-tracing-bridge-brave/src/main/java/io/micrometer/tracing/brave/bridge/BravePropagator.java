/*
 * Copyright 2013-2021 the original author or authors.
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

package io.micrometer.tracing.brave.bridge;

import java.util.List;

import brave.Tracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.propagation.Propagator;

/**
 * Brave implementation of a {@link Propagator}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BravePropagator implements Propagator {

    private final Tracing tracing;

    public BravePropagator(Tracing tracing) {
        this.tracing = tracing;
    }

    @Override
    public List<String> fields() {
        return this.tracing.propagation().keys();
    }

    @Override
    public <C> void inject(TraceContext traceContext, C carrier, Setter<C> setter) {
        this.tracing.propagation().injector(setter::set).inject(BraveTraceContext.toBrave(traceContext), carrier);
    }

    @Override
    public <C> Span.Builder extract(C carrier, Getter<C> getter) {
        TraceContextOrSamplingFlags extract = this.tracing.propagation().extractor(getter::get).extract(carrier);
        if (extract.samplingFlags() == SamplingFlags.EMPTY) {
            return new BraveSpanBuilder(this.tracing.tracer());
        }
        return BraveSpanBuilder.toBuilder(this.tracing.tracer(), extract);
    }

}
