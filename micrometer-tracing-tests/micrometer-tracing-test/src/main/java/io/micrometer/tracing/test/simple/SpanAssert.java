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

import java.util.List;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.util.StringUtils;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;

/**
 * Assertion methods for {@code SimpleSpan}s.
 * <p>
 * To create a new instance of this class, invoke {@link SpanAssert#assertThat(SimpleSpan)}
 * or {@link SpanAssert#then(SimpleSpan)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SpanAssert extends AbstractAssert<SpanAssert, SimpleSpan> {

    protected SpanAssert(SimpleSpan actual) {
        super(actual, SpanAssert.class);
    }

    /**
     * Creates the assert object for {@link SimpleSpan}.
     *
     * @param actual tracer to assert against
     * @return meter registry assertions
     */
    public static SpanAssert assertThat(SimpleSpan actual) {
        return new SpanAssert(actual);
    }

    /**
     * Creates the assert object for {@link SimpleSpan}.
     *
     * @param actual tracer to assert against
     * @return meter registry assertions
     */
    public static SpanAssert then(SimpleSpan actual) {
        return new SpanAssert(actual);
    }

    public SpanAssert hasNoTags() {
        isNotNull();
        Map<String, String> tags = this.actual.getTags();
        if (!tags.isEmpty()) {
            failWithMessage("Span should have no tags but has <%s>", tags);
        }
        return this;
    }

    public SpanAssert hasTagWithKey(String key) {
        isNotNull();
        if (!this.actual.getTags().containsKey(key)) {
            failWithMessage("Span should have a tag with key <%s> but it's not there. List of all keys <%s>", key, this.actual.getTags().keySet());
        }
        return this;
    }

    public SpanAssert hasTag(String key, String value) {
        isNotNull();
        hasTagWithKey(key);
        Map<String, String> tags = this.actual.getTags();
        String tagValue = tags.get(key);
        if (!tagValue.equals(value)) {
            failWithMessage("Span should have a tag with key <%s> and value <%s>. The key is correct but the value is <%s>", key, value, tagValue);
        }
        return this;
    }

    public SpanAssert doesNotHaveTagWithKey(String key) {
        isNotNull();
        if (this.actual.getTags().containsKey(key)) {
            failWithMessage("Span should not have a tag with key <%s>", key, this.actual.getTags().keySet());
        }
        return this;
    }

    public SpanAssert doesNotHaveTag(String key, String value) {
        isNotNull();
        doesNotHaveTagWithKey(key);
        Map<String, String> tags = this.actual.getTags();
        String tagValue = tags.get(key);
        if (tagValue.equals(value)) {
            failWithMessage("Span should not have a tag with key <%s> and value <%s>", key, value);
        }
        return this;
    }

    public SpanAssert isStarted() {
        isNotNull();
        if (!this.actual.isStarted()) {
            failWithMessage("Span should be started");
        }
        return this;
    }

    public SpanAssert isNotStarted() {
        isNotNull();
        if (this.actual.isStarted()) {
            failWithMessage("Span should not be started");
        }
        return this;
    }

    public SpanAssert isEnded() {
        isNotNull();
        if (!this.actual.isEnded()) {
            failWithMessage("Span should be ended");
        }
        return this;
    }

    public SpanAssert isNotEnded() {
        isNotNull();
        if (this.actual.isEnded()) {
            failWithMessage("Span should not be ended");
        }
        return this;
    }

    public SpanAssert isAbandoned() {
        isNotNull();
        if (!this.actual.isAbandoned()) {
            failWithMessage("Span should be abandoned");
        }
        return this;
    }

    public SpanAssert isNotAbandoned() {
        isNotNull();
        if (this.actual.isAbandoned()) {
            failWithMessage("Span should not be abandoned");
        }
        return this;
    }

    public SpanAssertReturningAssert assertThatThrowable() {
        return new SpanAssertReturningAssert(actual.getThrowable(), this);
    }

    public SpanAssertReturningAssert thenThrowable() {
        return assertThatThrowable();
    }

    public SpanAssert hasRemoteServiceNameEqualTo(String remoteServiceName) {
        isNotNull();
        if (!remoteServiceName.equals(this.actual.getRemoteServiceName())) {
            failWithMessage("Span should have remote service name equal to <%s> but has <%s>", remoteServiceName, this.actual.getRemoteServiceName());
        }
        return this;
    }

    public SpanAssert doesNotHaveRemoteServiceNameEqualTo(String remoteServiceName) {
        isNotNull();
        if (remoteServiceName.equals(this.actual.getRemoteServiceName())) {
            failWithMessage("Span should not have remote service name equal to <%s>", remoteServiceName);
        }
        return this;
    }

    public SpanAssert hasSpanWithKindEqualTo(Span.Kind kind) {
        isNotNull();
        if (!kind.equals(this.actual.getSpanKind())) {
            failWithMessage("Span should have span kind equal to <%s> but has <%s>", kind, this.actual.getSpanKind());
        }
        return this;
    }

    public SpanAssert doesNotHaveSpanWithKindEqualTo(Span.Kind kind) {
        isNotNull();
        if (kind.equals(this.actual.getSpanKind())) {
            failWithMessage("Span should not have span kind equal to <%s>", kind);
        }
        return this;
    }

    public SpanAssert hasEventWithNameEqualTo(String eventName) {
        isNotNull();
        List<String> eventNames = this.actual.getEventNames();
        if (!eventNames.contains(eventName)) {
            failWithMessage("Span should have an event with name <%s> but has <%s>", eventName, eventNames);
        }
        return this;
    }

    public SpanAssert doesNotHaveEventWithNameEqualTo(String eventName) {
        isNotNull();
        List<String> eventNames = this.actual.getEventNames();
        if (eventNames.contains(eventName)) {
            failWithMessage("Span should not have an event with name <%s>", eventName);
        }
        return this;
    }

    public SpanAssert hasNameEqualTo(String spanName) {
        isNotNull();
        if (!this.actual.getName().equals(spanName)) {
            failWithMessage("Span should have a name <%s> but has <%s>", spanName, this.actual.getName());
        }
        return this;
    }

    public SpanAssert doesNotHaveNameEqualTo(String spanName) {
        isNotNull();
        if (!this.actual.getName().equals(spanName)) {
            failWithMessage("Span should not have a name <%s>", spanName, this.actual.getName());
        }
        return this;
    }

    public SpanAssert hasIpEqualTo(String ip) {
        isNotNull();
        if (!this.actual.getIp().equals(ip)) {
            failWithMessage("Span should have ip equal to <%s> but has <%s>", ip, this.actual.getIp());
        }
        return this;
    }

    public SpanAssert doesNotHaveIpEqualTo(String ip) {
        isNotNull();
        if (this.actual.getIp().equals(ip)) {
            failWithMessage("Span should not have ip equal to <%s>", ip, this.actual.getIp());
        }
        return this;
    }

    public SpanAssert hasIpThatIsNotBlank() {
        isNotNull();
        if (StringUtils.isBlank(this.actual.getIp())) {
            failWithMessage("Span should have ip that is not blank");
        }
        return this;
    }

    public SpanAssert hasIpThatIsBlank() {
        isNotNull();
        if (StringUtils.isNotBlank(this.actual.getIp())) {
            failWithMessage("Span should have ip that is blank");
        }
        return this;
    }

    public SpanAssert hasPortThatIsNotSet() {
        isNotNull();
        if (this.actual.getPort() != 0) {
            failWithMessage("Span should have port that is not set but was set to <%s>", this.actual.getPort());
        }
        return this;
    }

    public SpanAssert hasPortThatIsSet() {
        isNotNull();
        if (this.actual.getPort() == 0) {
            failWithMessage("Span should have port that is set but wasn't");
        }
        return this;
    }

    public SpanAssert isNoOp() {
        isNotNull();
        if (!this.actual.isNoop()) {
            failWithMessage("Span should be noop");
        }
        return this;
    }

    public SpanAssert isNotNoOp() {
        isNotNull();
        if (this.actual.isNoop()) {
            failWithMessage("Span should not be noop");
        }
        return this;
    }

    public static class SpanAssertReturningAssert extends AbstractThrowableAssert<SpanAssertReturningAssert, Throwable> {

        private final SpanAssert spanAssert;

        public SpanAssertReturningAssert(Throwable throwable, SpanAssert spanAssert) {
            super(throwable, SpanAssertReturningAssert.class);
            this.spanAssert = spanAssert;
        }

        public SpanAssert backToSpan() {
            return this.spanAssert;
        }
    }
}
