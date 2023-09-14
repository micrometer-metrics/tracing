/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.docs.tracing;

import io.micrometer.context.ContextRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingAwareMeterObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;

/**
 * Sources for tracing-configuring.adoc
 */
class TracingConfiguringTests {

    @Test
    void handler_configuration() {
        // tag::handler_configuration[]
        Tracer tracer = Tracer.NOOP; // The real tracer will come from your tracer
                                     // implementation (Brave /
        // OTel)
        Propagator propagator = Propagator.NOOP; // The real propagator will come from
                                                 // your tracer implementation (Brave /
                                                 // OTel)
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
            // assuming that micrometer-core is on the classpath
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry))
            // we set up a first matching handler that creates spans - it comes from
            // Micrometer
            // Tracing. We set up spans for sending and receiving data over the wire
            // and a default one
            .observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                    new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
                    new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                    new DefaultTracingObservationHandler(tracer)));

        // Creating and starting a new observation
        // via the `DefaultTracingObservationHandler` that will create a new Span and
        // start it
        Observation observation = Observation.start("my.operation", registry)
            .contextualName("This name is more readable - we can reuse it for e.g. spans")
            .lowCardinalityKeyValue("this.tag", "will end up as a meter tag and a span tag")
            .highCardinalityKeyValue("but.this.tag", "will end up as a span tag only");

        // Put the observation in scope
        // This will result in making the previously created Span, the current Span - it's
        // in ThreadLocal
        try (Observation.Scope scope = observation.openScope()) {
            // Run your code that you want to measure - still the attached Span is the
            // current one
            // This means that e.g. logging frameworks could inject to e.g. MDC tracing
            // information
            yourCodeToMeasure();
        }
        finally {
            // The corresponding Span will no longer be in ThreadLocal due to
            // try-with-resources block (Observation.Scope is an AutoCloseable)
            // Stop the Observation
            // The corresponding Span will be stopped and reported to an external system
            observation.stop();
        }
        // end::handler_configuration[]
    }

    @Test
    void observe() {
        // tag::handler_configuration_observe[]
        ObservationRegistry registry = ObservationRegistry.create();

        Observation.createNotStarted("my.operation", registry)
            .contextualName("This name is more readable - we can reuse it for e.g. spans")
            .lowCardinalityKeyValue("this.tag", "will end up as a meter tag and a span tag")
            .highCardinalityKeyValue("but.this.tag", "will end up as a span tag only")
            .observe(this::yourCodeToMeasure);
        // end::handler_configuration_observe[]
    }

    void yourCodeToMeasure() {

    }

    void example_of_setting_ObservationAwareSpanThreadLocalAccessor() {
        Tracer tracer = null;

        // tag::span_thread_local_accessor[]
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ObservationAwareSpanThreadLocalAccessor(tracer));
        // end::span_thread_local_accessor[]

        ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor.KEY);
    }

    void example_of_exemplars() {
        Tracer tracer = Tracer.NOOP;
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // tag::exemplars[]
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
            // Don't register the DefaultMeterObservationHandler...
            // .observationHandler(new DefaultMeterObservationHandler(meterRegistry))
            // ...instead register the tracing aware version
            .observationHandler(new TracingAwareMeterObservationHandler<>(
                    new DefaultMeterObservationHandler(meterRegistry), tracer));
        // end::exemplars[]
    }

}
