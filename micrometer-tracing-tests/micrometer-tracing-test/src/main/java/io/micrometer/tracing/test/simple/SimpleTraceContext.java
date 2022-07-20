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
 * A test implementation of a trace context.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleTraceContext implements TraceContext {

    private String traceId = "";

    private String parentId = "";

    private String spanId = "";

    private Boolean sampled = false;

    @Override
    public String traceId() {
        return this.traceId;
    }

    @Override
    public String parentId() {
        return this.parentId;
    }

    @Override
    public String spanId() {
        return this.spanId;
    }

    @Override
    public Boolean sampled() {
        return this.sampled;
    }

    /**
     * Sets the trace id.
     * @param traceId trace id
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Sets the parent span id.
     * @param parentId parent span id
     */
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    /**
     * Sets the span id.
     * @param spanId span id
     */
    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    /**
     * Sets the sampling decision.
     * @param sampled sampled, or not?
     */
    public void setSampled(Boolean sampled) {
        this.sampled = sampled;
    }

}
