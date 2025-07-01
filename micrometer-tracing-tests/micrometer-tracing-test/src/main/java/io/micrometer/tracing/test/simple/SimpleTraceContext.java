/**
 * Copyright 2023 the original author or authors.
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
import io.micrometer.tracing.internal.EncodingUtils;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A test implementation of a trace context.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleTraceContext implements TraceContext {

    private static final Random random = new Random();

    private volatile String traceId = "";

    private volatile String parentId = "";

    private volatile String spanId = "";

    private volatile @Nullable Boolean sampled = false;

    private Map<String, String> baggageFromParent = new ConcurrentHashMap<>();

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
    public @Nullable Boolean sampled() {
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
    public void setSampled(@Nullable Boolean sampled) {
        this.sampled = sampled;
    }

    String generateId() {
        return EncodingUtils.fromLong(random.nextLong());
    }

    @Override
    public String toString() {
        return "SimpleTraceContext{" + "traceId='" + traceId + '\'' + ", parentId='" + parentId + '\'' + ", spanId='"
                + spanId + '\'' + ", sampled=" + sampled + '}';
    }

    void addParentBaggage(Map<String, String> baggageFromParent) {
        this.baggageFromParent.putAll(baggageFromParent);
    }

    Map<String, String> baggageFromParent() {
        return this.baggageFromParent;
    }

}
