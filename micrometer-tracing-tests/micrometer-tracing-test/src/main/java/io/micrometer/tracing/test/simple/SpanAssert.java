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
import java.util.stream.Collectors;

import io.micrometer.common.docs.TagKey;
import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;

/**
 * Assertion methods for {@code SimpleSpan}s.
 * <p>
 * To create a new instance of this class, invoke {@link SpanAssert#assertThat(FinishedSpan)}
 * or {@link SpanAssert#then(FinishedSpan)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SpanAssert<SELF extends SpanAssert<SELF>> extends AbstractAssert<SELF, FinishedSpan> {

    protected SpanAssert(FinishedSpan actual) {
        super(actual, SpanAssert.class);
    }

    /**
     * Creates the assert object for {@link FinishedSpan}.
     *
     * @param actual span to assert against
     * @return span assertions
     */
    public static SpanAssert assertThat(FinishedSpan actual) {
        return new SpanAssert(actual);
    }

    /**
     * Creates the assert object for {@link FinishedSpan}.
     *
     * @param actual span to assert against
     * @return span assertions
     */
    public static SpanAssert then(FinishedSpan actual) {
        return new SpanAssert(actual);
    }

    public SELF hasNoTags() {
        isNotNull();
        Map<String, String> tags = this.actual.getTags();
        if (!tags.isEmpty()) {
            failWithMessage("Span should have no tags but has <%s>", tags);
        }
        return (SELF) this;
    }

    public SELF hasTagWithKey(String key) {
        isNotNull();
        if (!this.actual.getTags().containsKey(key)) {
            failWithMessage("Span should have a tag with key <%s> but it's not there. List of all keys <%s>", key, this.actual.getTags().keySet());
        }
        return (SELF) this;
    }

    public SELF hasTagWithKey(TagKey key) {
        return hasTagWithKey(key.getKey());
    }

    public SELF hasTag(String key, String value) {
        isNotNull();
        hasTagWithKey(key);
        Map<String, String> tags = this.actual.getTags();
        String tagValue = tags.get(key);
        if (!tagValue.equals(value)) {
            failWithMessage("Span should have a tag with key <%s> and value <%s>. The key is correct but the value is <%s>", key, value, tagValue);
        }
        return (SELF) this;
    }

    public SELF hasTag(TagKey key, String value) {
        return hasTag(key.getKey(), value);
    }

    public SELF doesNotHaveTagWithKey(String key) {
        isNotNull();
        if (this.actual.getTags().containsKey(key)) {
            failWithMessage("Span should not have a tag with key <%s>", key, this.actual.getTags().keySet());
        }
        return (SELF) this;
    }

    public SELF doesNotHaveTagWithKey(TagKey key) {
        return doesNotHaveTagWithKey(key.getKey());
    }

    public SELF doesNotHaveTag(String key, String value) {
        isNotNull();
        doesNotHaveTagWithKey(key);
        Map<String, String> tags = this.actual.getTags();
        String tagValue = tags.get(key);
        if (value.equals(tagValue)) {
            failWithMessage("Span should not have a tag with key <%s> and value <%s>", key, value);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveTag(TagKey key, String value) {
        return doesNotHaveTag(key.getKey(), value);
    }

    public SELF isStarted() {
        isNotNull();
        if (this.actual.getStartTimestamp() == 0) {
            failWithMessage("Span should be started");
        }
        return (SELF) this;
    }

    public SELF isNotStarted() {
        isNotNull();
        if (this.actual.getStartTimestamp() != 0) {
            failWithMessage("Span should not be started");
        }
        return (SELF) this;
    }

    public SELF isEnded() {
        isNotNull();
        if (this.actual.getEndTimestamp() == 0) {
            failWithMessage("Span should be ended");
        }
        return (SELF) this;
    }

    public SELF isNotEnded() {
        isNotNull();
        if (this.actual.getEndTimestamp() != 0) {
            failWithMessage("Span should not be ended");
        }
        return (SELF) this;
    }

    public SpanAssertReturningAssert assertThatThrowable() {
        return new SpanAssertReturningAssert(actual.getError(), this);
    }

    public SpanAssertReturningAssert thenThrowable() {
        return assertThatThrowable();
    }

    public SELF hasRemoteServiceNameEqualTo(String remoteServiceName) {
        isNotNull();
        if (!remoteServiceName.equals(this.actual.getRemoteServiceName())) {
            failWithMessage("Span should have remote service name equal to <%s> but has <%s>", remoteServiceName, this.actual.getRemoteServiceName());
        }
        return (SELF) this;
    }

    public SELF doesNotHaveRemoteServiceNameEqualTo(String remoteServiceName) {
        isNotNull();
        if (remoteServiceName.equals(this.actual.getRemoteServiceName())) {
            failWithMessage("Span should not have remote service name equal to <%s>", remoteServiceName);
        }
        return (SELF) this;
    }

    public SELF hasKindEqualTo(Span.Kind kind) {
        isNotNull();
        if (!kind.equals(this.actual.getKind())) {
            failWithMessage("Span should have span kind equal to <%s> but has <%s>", kind, this.actual.getKind());
        }
        return (SELF) this;
    }

    public SELF doesNotHaveKindEqualTo(Span.Kind kind) {
        isNotNull();
        if (kind.equals(this.actual.getKind())) {
            failWithMessage("Span should not have span kind equal to <%s>", kind);
        }
        return (SELF) this;
    }

    public SELF hasEventWithNameEqualTo(String eventName) {
        isNotNull();
        List<String> eventNames = eventNames();
        if (!eventNames.contains(eventName)) {
            failWithMessage("Span should have an event with name <%s> but has <%s>", eventName, eventNames);
        }
        return (SELF) this;
    }

    private List<String> eventNames() {
        return this.actual.getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList());
    }

    public SELF doesNotHaveEventWithNameEqualTo(String eventName) {
        isNotNull();
        List<String> eventNames = eventNames();
        if (eventNames.contains(eventName)) {
            failWithMessage("Span should not have an event with name <%s>", eventName);
        }
        return (SELF) this;
    }

    public SELF hasNameEqualTo(String spanName) {
        isNotNull();
        if (!this.actual.getName().equals(spanName)) {
            failWithMessage("Span should have a name <%s> but has <%s>", spanName, this.actual.getName());
        }
        return (SELF) this;
    }

    public SELF doesNotHaveNameEqualTo(String spanName) {
        isNotNull();
        if (!this.actual.getName().equals(spanName)) {
            failWithMessage("Span should not have a name <%s>", spanName, this.actual.getName());
        }
        return (SELF) this;
    }

    public SELF hasIpEqualTo(String ip) {
        isNotNull();
        if (!this.actual.getRemoteIp().equals(ip)) {
            failWithMessage("Span should have ip equal to <%s> but has <%s>", ip, this.actual.getRemoteIp());
        }
        return (SELF) this;
    }

    public SELF doesNotHaveIpEqualTo(String ip) {
        isNotNull();
        if (this.actual.getRemoteIp().equals(ip)) {
            failWithMessage("Span should not have ip equal to <%s>", ip, this.actual.getRemoteIp());
        }
        return (SELF) this;
    }

    public SELF hasIpThatIsNotBlank() {
        isNotNull();
        if (StringUtils.isBlank(this.actual.getRemoteIp())) {
            failWithMessage("Span should have ip that is not blank");
        }
        return (SELF) this;
    }

    public SELF hasIpThatIsBlank() {
        isNotNull();
        if (StringUtils.isNotBlank(this.actual.getRemoteIp())) {
            failWithMessage("Span should have ip that is blank");
        }
        return (SELF) this;
    }

    public SELF hasPortEqualTo(int port) {
        isNotNull();
        if (this.actual.getRemotePort() != port) {
            failWithMessage("Span should have port equal to <%s> but has <%s>", port, this.actual.getRemotePort());
        }
        return (SELF) this;
    }

    public SELF doesNotHavePortEqualTo(int port) {
        isNotNull();
        if (this.actual.getRemotePort() == port) {
            failWithMessage("Span should not have port equal to <%s>", port, this.actual.getRemotePort());
        }
        return (SELF) this;
    }

    public SELF hasPortThatIsNotSet() {
        isNotNull();
        if (this.actual.getRemotePort() != 0) {
            failWithMessage("Span should have port that is not set but was set to <%s>", this.actual.getRemotePort());
        }
        return (SELF) this;
    }

    public SELF hasPortThatIsSet() {
        isNotNull();
        if (this.actual.getRemotePort() == 0) {
            failWithMessage("Span should have port that is set but wasn't");
        }
        return (SELF) this;
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
