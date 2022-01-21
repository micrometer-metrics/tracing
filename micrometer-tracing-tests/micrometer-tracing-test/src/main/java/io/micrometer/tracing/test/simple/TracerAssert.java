/*
 * Copyright 2021 VMware, Inc.
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

import java.util.Collection;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractCollectionAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;

/**
 * Assertion methods for {@code SimpleTracer}s.
 * <p>
 * To create a new instance of this class, invoke {@link TracerAssert#assertThat(SimpleTracer)}
 * or {@link TracerAssert#then(SimpleTracer)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class TracerAssert extends AbstractAssert<TracerAssert, SimpleTracer> {

    protected TracerAssert(SimpleTracer actual) {
        super(actual, TracerAssert.class);
    }

    /**
     * Creates the assert object for {@link SimpleTracer}.
     *
     * @param actual tracer to assert against
     * @return meter registry assertions
     */
    public static TracerAssert assertThat(SimpleTracer actual) {
        return new TracerAssert(actual);
    }

    /**
     * Creates the assert object for {@link SimpleTracer}.
     *
     * @param actual tracer to assert against
     * @return meter registry assertions
     */
    public static TracerAssert then(SimpleTracer actual) {
        return new TracerAssert(actual);
    }

    public SpanAssert onlySpan() {
        isNotNull();
        return SpanAssert.assertThat(this.actual.onlySpan());
    }

    public SpanAssert lastSpan() {
        isNotNull();
        return SpanAssert.assertThat(this.actual.lastSpan());
    }

    public AbstractCollectionAssert<?, Collection<? extends SimpleSpan>, SimpleSpan, ObjectAssert<SimpleSpan>> reportedSpans() {
        return Assertions.assertThat(this.actual.getSpans());
    }
}
