/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.otel.propagation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TextMapPropagator} implementation that can read / write from multiple
 * {@link TextMapPropagator} implementations.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CompositeTextMapPropagator implements TextMapPropagator {

    private static final Logger log = LoggerFactory.getLogger(CompositeTextMapPropagator.class);

    private final Map<org.springframework.cloud.sleuth.otel.propagation.PropagationType, TextMapPropagator> mapping = new HashMap<>();

    private final List<org.springframework.cloud.sleuth.otel.propagation.PropagationType> types;

    public CompositeTextMapPropagator(PropagationSupplier beanFactory, List<org.springframework.cloud.sleuth.otel.propagation.PropagationType> types) {
        this.types = types;
        if (isOnClasspath(awsClass())) {
            this.mapping.put(org.springframework.cloud.sleuth.otel.propagation.PropagationType.AWS, beanFactory.getProvider(AwsXrayPropagator.class)
                    .getIfAvailable(AwsXrayPropagator::getInstance));
        }
        if (isOnClasspath(b3Class())) {
            this.mapping.put(org.springframework.cloud.sleuth.otel.propagation.PropagationType.B3, beanFactory.getProvider(B3Propagator.class)
                    .getIfAvailable(B3Propagator::injectingSingleHeader));
        }
        if (isOnClasspath(jaegerClass())) {
            this.mapping.put(org.springframework.cloud.sleuth.otel.propagation.PropagationType.JAEGER,
                    beanFactory.getProvider(JaegerPropagator.class).getIfAvailable(JaegerPropagator::getInstance));
        }
        if (isOnClasspath(otClass())) {
            this.mapping.put(org.springframework.cloud.sleuth.otel.propagation.PropagationType.OT_TRACER, beanFactory.getProvider(OtTracePropagator.class)
                    .getIfAvailable(OtTracePropagator::getInstance));
        }
        this.mapping.put(org.springframework.cloud.sleuth.otel.propagation.PropagationType.W3C, TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));
        this.mapping.put(org.springframework.cloud.sleuth.otel.propagation.PropagationType.CUSTOM, NoopTextMapPropagator.INSTANCE);
        if (log.isDebugEnabled()) {
            log.debug("Registered the following context propagation types " + this.mapping.keySet());
        }
    }

    String otClass() {
        return "io.opentelemetry.extension.trace.propagation.OtTracePropagator";
    }

    String jaegerClass() {
        return "io.opentelemetry.extension.trace.propagation.JaegerPropagator";
    }

    String b3Class() {
        return "io.opentelemetry.extension.trace.propagation.B3Propagator";
    }

    String awsClass() {
        return "io.opentelemetry.extension.aws.AwsXrayPropagator";
    }

    private boolean isOnClasspath(String clazz) {
        try {
            Class.forName(clazz);
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public List<String> fields() {
        return this.types.stream().map(key -> this.mapping.getOrDefault(key, NoopTextMapPropagator.INSTANCE))
                .flatMap(p -> p.fields().stream()).collect(Collectors.toList());
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        this.types.stream().map(key -> this.mapping.getOrDefault(key, NoopTextMapPropagator.INSTANCE))
                .forEach(p -> p.inject(context, carrier, setter));
    }

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
        for (org.springframework.cloud.sleuth.otel.propagation.PropagationType type : this.types) {
            TextMapPropagator propagator = this.mapping.get(type);
            if (propagator == null || propagator == NoopTextMapPropagator.INSTANCE) {
                continue;
            }
            Context extractedContext = propagator.extract(context, carrier, getter);
            Span span = Span.fromContextOrNull(extractedContext);
            Baggage baggage = Baggage.fromContextOrNull(extractedContext);
            if (span != null || baggage != null) {
                return extractedContext;
            }
        }
        return context;
    }

    interface PropagationSupplier {

        <T> ObjectProvider<T> getProvider(Class<T> clazz);

        interface ObjectProvider<T> {

            T getIfAvailable(Supplier<T> supplier);
        }
    }

    private static final class NoopTextMapPropagator implements TextMapPropagator {

        private static final NoopTextMapPropagator INSTANCE = new NoopTextMapPropagator();

        @Override
        public List<String> fields() {
            return Collections.emptyList();
        }

        @Override
        public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        }

        @Override
        public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
            return context;
        }

    }

}
