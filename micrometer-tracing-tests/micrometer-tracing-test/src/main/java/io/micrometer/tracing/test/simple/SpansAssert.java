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
import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.tracing.exporter.FinishedSpan;
import org.assertj.core.api.CollectionAssert;

/**
 * Assertion methods for {@code SimpleSpan}s.
 * <p>
 * To create a new instance of this class, invoke {@link SpansAssert#assertThat(Collection)}
 * or {@link SpansAssert#then(Collection)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SpansAssert extends CollectionAssert<FinishedSpan> {

    protected SpansAssert(Collection<FinishedSpan> actual) {
        super(actual);
    }

    /**
     * Creates the assert object for a collection of {@link FinishedSpan}.
     *
     * @param actual span to assert against
     * @return span collection assertions
     */
    public static SpansAssert assertThat(Collection<FinishedSpan> actual) {
        return new SpansAssert(actual);
    }

    /**
     * Creates the assert object for a collection of {@link FinishedSpan}.
     *
     * @param actual span to assert against
     * @return span collection assertions
     */
    public static SpansAssert then(Collection<FinishedSpan> actual) {
        return new SpansAssert(actual);
    }

    public SpansAssert haveSameTraceId() {
        isNotEmpty();
        List<String> traceIds = this.actual.stream().map(FinishedSpan::getTraceId).distinct().collect(Collectors.toList());
        if (traceIds.size() != 1) {
            failWithMessage("Spans should have same trace ids but found {} trace ids. Found following spans {}", traceIds, this.actual);
        }
        return this;
    }

    public SpansAssert hasASpanWithName(String name) {
        isNotEmpty();
        this.actual.stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with name <{}> but found none. Found following spans {}", name, this.actual);
            return new AssertionError();
        });
        return this;
    }

    public SpansAssert hasASpanWithRemoteServiceName(String remoteServiceName) {
        isNotEmpty();
        this.actual.stream().filter(f -> remoteServiceName.equals(f.getRemoteServiceName())).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with remote service name <{}> but found none. Found following spans {}", remoteServiceName, this.actual);
            return new AssertionError();
        });
        return this;
    }

    public SpansAssert hasASpanWithATag(String key, String value) {
        isNotEmpty();
        this.actual.stream().filter(f -> {
            String tag = f.getTags().get(key);
            return value.equals(tag);
        }).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with tag key <{}> and value <{}> but found none. Found following spans {}", key, value, this.actual);
            return new AssertionError();
        });
        return this;
    }

    public SpansAssert hasASpanWithATagKey(String key) {
        isNotEmpty();
        this.actual.stream().filter(f -> f.getTags().containsKey(key)).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with tag key <{}> but found none. Found following spans {}", key, this.actual);
            return new AssertionError();
        });
        return this;
    }

}
