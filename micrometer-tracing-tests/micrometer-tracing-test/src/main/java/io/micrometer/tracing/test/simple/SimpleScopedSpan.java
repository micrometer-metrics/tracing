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

import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.TraceContext;

/**
 * A test implementation of a scoped span.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleScopedSpan implements ScopedSpan {

    private final SimpleSpan span;

    /**
     * Creates a new instance of {@link SimpleScopedSpan}.
     * @param simpleTracer simple tracer
     */
    public SimpleScopedSpan(SimpleTracer simpleTracer) {
        this.span = simpleTracer.nextSpan().start();
    }

    @Override
    public boolean isNoop() {
        return this.span.isNoop();
    }

    @Override
    public TraceContext context() {
        return new SimpleTraceContext();
    }

    @Override
    public ScopedSpan name(String name) {
        this.span.name(name);
        return this;
    }

    @Override
    public ScopedSpan tag(String key, String value) {
        this.span.tag(key, value);
        return this;
    }

    @Override
    public ScopedSpan event(String value) {
        this.span.event(value);
        return this;
    }

    @Override
    public ScopedSpan error(Throwable throwable) {
        this.span.error(throwable);
        return this;
    }

    @Override
    public void end() {
        this.span.end();
    }

}
