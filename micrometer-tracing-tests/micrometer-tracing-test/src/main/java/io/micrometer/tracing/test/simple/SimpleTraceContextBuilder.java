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
package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of the trace context builder.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleTraceContextBuilder implements TraceContext.Builder {

    private String traceId;

    private String parentId;

    private String spanId;

    private Boolean sampled;

    @Override
    public TraceContext.Builder traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    @Override
    public TraceContext.Builder parentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    @Override
    public TraceContext.Builder spanId(String spanId) {
        this.spanId = spanId;
        return this;
    }

    @Override
    public TraceContext.Builder sampled(Boolean sampled) {
        this.sampled = sampled;
        return this;
    }

    @Override
    public TraceContext build() {
        SimpleTraceContext traceContext = new SimpleTraceContext();
        if (this.traceId != null) {
            traceContext.setTraceId(this.traceId);
        }
        if (this.spanId != null) {
            traceContext.setSpanId(this.spanId);
        }
        if (this.parentId != null) {
            traceContext.setParentId(this.parentId);
        }
        if (this.sampled != null) {
            traceContext.setSampled(this.sampled);
        }
        return traceContext;
    }

}
