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
import java.util.function.Supplier;

import brave.propagation.Propagation;
import io.micrometer.tracing.brave.propagation.PropagationFactorySupplier;
import io.micrometer.tracing.brave.propagation.PropagationType;

/**
 * Merges various propagation factories into a composite.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class CompositePropagationFactorySupplier implements PropagationFactorySupplier {

    private final Supplier<BraveBaggageManager> baggageManagerSupplier;

    private final Supplier<Propagation.Factory> factorySupplier;

    private final List<String> localFields;

    private final List<PropagationType> types;

    /**
     * Creates a new instance of {@link CompositePropagationFactorySupplier}.
     * @param baggageManagerSupplier bean factory
     * @param factorySupplier factory supplier
     * @param localFields local fields to be set in context
     * @param types supported propagation types
     */
    public CompositePropagationFactorySupplier(Supplier<BraveBaggageManager> baggageManagerSupplier,
            Supplier<Propagation.Factory> factorySupplier, List<String> localFields, List<PropagationType> types) {
        this.baggageManagerSupplier = baggageManagerSupplier;
        this.factorySupplier = factorySupplier;
        this.localFields = localFields;
        this.types = types;
    }

    @Override
    public Propagation.Factory get() {
        return new CompositePropagationFactory(this.factorySupplier, braveBaggageManager(), this.localFields,
                this.types);
    }

    private BraveBaggageManager braveBaggageManager() {
        BraveBaggageManager baggageManager = baggageManagerSupplier.get();
        if (baggageManager != null) {
            return baggageManager;
        }
        return new BraveBaggageManager();
    }

}
