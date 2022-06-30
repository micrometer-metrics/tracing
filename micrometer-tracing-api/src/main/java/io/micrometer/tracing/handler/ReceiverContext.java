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

import io.micrometer.observation.Observation;
import io.micrometer.tracing.propagation.Propagator;

/**
 * Context used when receiving a message / request over the wire.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class ReceiverContext<T> extends Observation.Context {

    private final Propagator.Getter<T> getter;

    private T carrier;

    public ReceiverContext(Propagator.Getter<T> getter) {
        this.getter = getter;
    }

    public T getCarrier() {
        return carrier;
    }

    public void setCarrier(T carrier) {
        this.carrier = carrier;
    }

    public Propagator.Getter<T> getGetter() {
        return getter;
    }
}
