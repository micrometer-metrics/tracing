/*
 * Copyright 2026 the original author or authors.
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
package io.micrometer.tracing.brave.bridge;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.tck.BaggageCompatibilityKit;
import org.junit.jupiter.api.AfterEach;

class BraveBaggageCompatibilityTests extends BaggageCompatibilityKit {

    private final StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

    private final Tracing tracing = Tracing.newBuilder()
        .currentTraceContext(this.currentTraceContext)
        .supportsJoin(false)
        .traceId128Bit(true)
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("foo")))
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("bar")))
            .build())
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();

    private final Tracer tracer = new BraveTracer(this.tracing.tracer(),
            new BraveCurrentTraceContext(this.currentTraceContext), new BraveBaggageManager());

    @Override
    protected Tracer getTracer() {
        return this.tracer;
    }

    @Override
    protected Propagator getPropagator() {
        return new BravePropagator(this.tracing);
    }

    @AfterEach
    void close() {
        try {
            this.currentTraceContext.close();
        }
        finally {
            this.tracing.close();
        }
    }

}
