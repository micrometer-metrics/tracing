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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.micrometer.common.docs.TagKey;
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
            failWithMessage("Spans should have same trace ids but found %s trace ids. Found following spans \n%s", traceIds, spansAsString());
        }
        return this;
    }

    private String spansAsString() {
        return this.actual.stream().map(Object::toString).collect(Collectors.joining("\n"));
    }

    public SpansAssert hasASpanWithName(String name) {
        isNotEmpty();
        extractSpanWithName(name);
        return this;
    }

    private FinishedSpan extractSpanWithName(String name) {
        return this.actual.stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with name <%s> but found none. Found following spans \n%s", name, spansAsString());
            return new AssertionError();
        });
    }

    private FinishedSpan extractSpanWithNameIgnoreCase(String name) {
        return this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName())).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with name (ignore case) <%s> but found none. Found following spans \n%s", name, spansAsString());
            return new AssertionError();
        });
    }

    public SpansAssert hasASpanWithNameIgnoreCase(String name) {
        isNotEmpty();
        this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName())).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with name (ignoring case) <%s> but found none. Found following spans \n%s", name, spansAsString());
            return new AssertionError();
        });
        return this;
    }

    public SpansAssert forAllSpansWithNameEqualTo(String name, Consumer<SpanAssert> spanConsumer) {
        isNotEmpty();
        hasASpanWithName(name);
        this.actual.stream().filter(f -> name.equals(f.getName())).forEach(f -> spanConsumer.accept(SpanAssert.then(f)));
        return this;
    }

    public SpansAssert forAllSpansWithNameEqualToIgnoreCase(String name, Consumer<SpanAssert> spanConsumer) {
        isNotEmpty();
        hasASpanWithNameIgnoreCase(name);
        this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName())).forEach(f -> spanConsumer.accept(SpanAssert.then(f)));
        return this;
    }

    public SpansAssertReturningAssert assertThatASpanWithNameEqualTo(String name) {
        isNotEmpty();
        FinishedSpan span = extractSpanWithName(name);
        return new SpansAssertReturningAssert(this, span);
    }

    public SpansAssertReturningAssert thenASpanWithNameEqualTo(String name) {
        return assertThatASpanWithNameEqualTo(name);
    }

    public SpansAssertReturningAssert assertThatASpanWithNameEqualToIgnoreCase(String name) {
        isNotEmpty();
        FinishedSpan span = extractSpanWithNameIgnoreCase(name);
        return new SpansAssertReturningAssert(this, span);
    }

    public SpansAssertReturningAssert thenASpanWithNameEqualToIgnoreCase(String name) {
        return assertThatASpanWithNameEqualToIgnoreCase(name);
    }

    public SpansAssert hasNumberOfSpansEqualTo(int expectedNumberOfSpans) {
        isNotEmpty();
        if (this.actual.size() != expectedNumberOfSpans) {
            failWithMessage("There should be <%s> spans but there were <%s>. Found following spans \n%s", expectedNumberOfSpans, this.actual.size(), spansAsString());
        }
        return this;
    }

    public SpansAssert hasNumberOfSpansWithNameEqualTo(String spanName, int expectedNumberOfSpans) {
        isNotEmpty();
        long spansWithNameSize = this.actual.stream().filter(f -> spanName.equals(f.getName())).count();
        if (spansWithNameSize != expectedNumberOfSpans) {
            failWithMessage("There should be <%s> spans with name <%s> but there were <%s>. Found following spans \n%s", expectedNumberOfSpans, spanName, spansWithNameSize, spansAsString());
        }
        return this;
    }

    public SpansAssert hasNumberOfSpansWithNameEqualToIgnoreCase(String spanName, int expectedNumberOfSpans) {
        isNotEmpty();
        long spansWithNameSize = this.actual.stream().filter(f -> spanName.equalsIgnoreCase(f.getName())).count();
        if (spansWithNameSize != expectedNumberOfSpans) {
            failWithMessage("There should be <%s> spans with name (ignoring case) <%s> but there were <%s>. Found following spans \n%s", expectedNumberOfSpans, spanName, spansWithNameSize, spansAsString());
        }
        return this;
    }

    public SpansAssert hasASpanWithRemoteServiceName(String remoteServiceName) {
        isNotEmpty();
        this.actual.stream().filter(f -> remoteServiceName.equals(f.getRemoteServiceName())).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with remote service name <%s> but found none. Found following spans \n%s", remoteServiceName, spansAsString());
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
            failWithMessage("There should be at least one span with tag key <%s> and value <%s> but found none. Found following spans \n%s", key, value, spansAsString());
            return new AssertionError();
        });
        return this;
    }

    public SpansAssert hasASpanWithATagKey(String key) {
        isNotEmpty();
        this.actual.stream().filter(f -> f.getTags().containsKey(key)).findFirst().orElseThrow(() -> {
            failWithMessage("There should be at least one span with tag key <%s> but found none. Found following spans \n%s", key, spansAsString());
            return new AssertionError();
        });
        return this;
    }

    public SpansAssert hasASpanWithATag(TagKey tagKey, String value) {
        return hasASpanWithATag(tagKey.getKey(), value);
    }

    public SpansAssert hasASpanWithATagKey(TagKey tagKey) {
        return hasASpanWithATagKey(tagKey.getKey());
    }

    public static class SpansAssertReturningAssert extends SpanAssert<SpansAssert.SpansAssertReturningAssert> {

        private final SpansAssert spansAssert;

        public SpansAssertReturningAssert(SpansAssert spansAssert, FinishedSpan span) {
            super(span);
            this.spansAssert = spansAssert;
        }

        public SpansAssert backToSpans() {
            return this.spansAssert;
        }
    }
}
