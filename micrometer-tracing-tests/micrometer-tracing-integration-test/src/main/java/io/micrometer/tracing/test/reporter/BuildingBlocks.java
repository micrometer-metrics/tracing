/**
 * Copyright 2022 the original author or authors.
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
package io.micrometer.tracing.test.reporter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.propagation.Propagator;

import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Building blocks for reporters and tracers.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings("rawtypes")
public interface BuildingBlocks {

    /**
     * Returns a {@link Tracer}.
     * @return tracer
     */
    Tracer getTracer();

    /**
     * Returns a {@link Propagator}.
     * @return propagator
     */
    Propagator getPropagator();

    /**
     * Returns a collection of default {@link ObservationHandler} customizers.
     * @return customizers
     */
    BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> getCustomizers();

    /**
     * Returns a list of finished spans.
     * @return finished spans
     */
    List<FinishedSpan> getFinishedSpans();

}
