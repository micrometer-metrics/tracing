/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.tracing.test.granularity;

import io.micrometer.observation.Level;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.ObservationLevel;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

abstract class AbstractGranularityTests {

    static final Logger log = LoggerFactory.getLogger(AbstractGranularityTests.class);

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    abstract Tracer getTracer();

    abstract List<FinishedSpan> finishedSpans();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(getTracer()));
        observationRegistry.observationConfig().observationLevel(Parent.class.getCanonicalName(), Level.ALL);
        observationRegistry.observationConfig().observationLevel(Child.class.getCanonicalName(), Level.INFO);
        observationRegistry.observationConfig().observationLevel(Grandchild.class.getCanonicalName(), Level.ALL);
    }

    @AfterEach
    void close() {
        then(getTracer().currentSpan()).isNull();
        then(observationRegistry.getCurrentObservationScope()).isNull();
    }

    @Test
    void parentChildRelationshipIsMaintained() {
        Observation.createNotStarted("parent", ObservationLevel.info(Parent.class), observationRegistry).observe(() -> {
            log.info("Parent [" + getTracer().currentSpan() + "]");
            Observation.createNotStarted("child", ObservationLevel.trace(Child.class), observationRegistry)
                .observe(() -> {
                    log.info("Child [" + getTracer().currentSpan() + "]");
                    Observation
                        .createNotStarted("grandchild", ObservationLevel.info(Grandchild.class), observationRegistry)
                        .observe(() -> log.info("Grandchild [" + getTracer().currentSpan() + "]"));
                });
        });

        List<FinishedSpan> finishedSpans = finishedSpans();
        SpansAssert.then(finishedSpans).haveSameTraceId().hasSize(2);
        FinishedSpan grandchild = finishedSpans.get(0);
        FinishedSpan parent = finishedSpans.get(1);
        BDDAssertions.then(grandchild.getParentId()).isEqualTo(parent.getSpanId());
    }

    static class Parent {

    }

    static class Child {

    }

    static class Grandchild {

    }

}
