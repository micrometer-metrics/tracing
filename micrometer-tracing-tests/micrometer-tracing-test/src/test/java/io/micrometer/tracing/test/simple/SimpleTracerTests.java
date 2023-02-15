/**
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.test.simple;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ContextAccessor;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.BDDAssertions.thenNoException;

class SimpleTracerTests {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SimpleTracerTests.class);

    @Test
    void should_not_break_on_scopes() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(new SimpleTracer()));

        ContextRegistry.getInstance().registerContextAccessor(new ContextAccessor<Observation, Observation>() {
            @Override
            public Class<? extends Observation> readableType() {
                return Observation.class;
            }

            @Override
            public void readValues(Observation sourceContext, Predicate<Object> keyPredicate,
                    Map<Object, Object> readValues) {
                readValues.put(ObservationThreadLocalAccessor.KEY, sourceContext);
            }

            @Override
            public <T> T readValue(Observation sourceContext, Object key) {
                return (T) sourceContext;
            }

            @Override
            public Class<? extends Observation> writeableType() {
                return Observation.class;
            }

            @Override
            public Observation writeValues(Map<Object, Object> valuesToWrite, Observation targetContext) {
                return (Observation) valuesToWrite.get(ObservationThreadLocalAccessor.KEY);
            }
        });

        Observation obs0 = Observation.createNotStarted("observation-0", registry);
        Observation obs1 = Observation.createNotStarted("observation-1", registry);

        thenNoException().isThrownBy(() -> {
            try (Observation.Scope scope = obs0.start().openScope()) {
                try (Observation.Scope scope2 = obs1.start().openScope()) {
                    try (ContextSnapshot.Scope scope3 = ContextSnapshot.setAllThreadLocalsFrom(obs1)) {
                        log.info("hello from here");
                    }
                }
            }
        });
    }

}
