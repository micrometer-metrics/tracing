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

package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of the trace context builder.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleTraceContextBuilder implements TraceContext.Builder {

    @Override
    public TraceContext.Builder traceId(String traceId) {
        return this;
    }

    @Override
    public TraceContext.Builder parentId(String parentId) {
        return this;
    }

    @Override
    public TraceContext.Builder spanId(String spanId) {
        return this;
    }

    @Override
    public TraceContext.Builder sampled(Boolean sampled) {
        return this;
    }

    @Override
    public TraceContext build() {
        return new SimpleTraceContext();
    }

}
