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
package io.micrometer.tracing.brave.contextpropagation;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.CorrelationFlushScopeArrayReader;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.test.tck.ScopeCompatibilityKit;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.BDDAssertions.then;

@NullMarked
class BraveScopeTests extends ScopeCompatibilityKit {

    CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.newBuilder()
            .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageField.create("foo"))
                .flushOnUpdate()
                .build())
            .build())
        .build();

    Tracing tracing = Tracing.newBuilder()
        .currentTraceContext(this.currentTraceContext)
        .supportsJoin(false)
        .traceId128Bit(true)
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("foo")))
            .build())
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();

    brave.Tracer braveTracer = this.tracing.tracer();

    Tracer tracer = new BraveTracer(this.braveTracer, new BraveCurrentTraceContext(this.currentTraceContext),
            new BraveBaggageManager());

    @Override
    protected Tracer getTracer() {
        return this.tracer;
    }

    @AfterEach
    void cleanup() {
        try {
            then(CorrelationFlushScopeArrayReader.size()).isZero();
        }
        finally {
            this.tracing.close();
        }
    }

}
