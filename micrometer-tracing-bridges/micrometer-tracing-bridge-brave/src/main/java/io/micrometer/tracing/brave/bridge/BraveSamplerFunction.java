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

import brave.sampler.SamplerFunctions;
import io.micrometer.core.instrument.transport.http.HttpRequest;
import io.micrometer.tracing.SamplerFunction;

/**
 * Brave implementation of a {@link SamplerFunction}.
 *
 * @param <T> type of the input, for example a request or method
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
public final class BraveSamplerFunction<T> implements SamplerFunction<T> {

    final brave.sampler.SamplerFunction<T> samplerFunction;

    public BraveSamplerFunction(brave.sampler.SamplerFunction<T> samplerFunction) {
        this.samplerFunction = samplerFunction;
    }

    static <T, V> brave.sampler.SamplerFunction<V> toBrave(SamplerFunction<T> samplerFunction, Class<T> input,
            Class<V> braveInput) {
        if (input.equals(HttpRequest.class) && braveInput.equals(brave.http.HttpRequest.class)) {
            return arg -> samplerFunction.trySample((T) BraveHttpRequest.fromBrave((brave.http.HttpRequest) arg));
        }
        return SamplerFunctions.deferDecision();
    }

    public static brave.sampler.SamplerFunction<brave.http.HttpRequest> toHttpBrave(
            SamplerFunction<HttpRequest> samplerFunction) {
        return arg -> samplerFunction.trySample(BraveHttpRequest.fromBrave(arg));
    }

    @Override
    public Boolean trySample(T arg) {
        return this.samplerFunction.trySample(arg);
    }

}
