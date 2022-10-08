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

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assertion methods for {@code SimpleSpan}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link SpanAssert#assertThat(FinishedSpan)} or {@link SpanAssert#then(FinishedSpan)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class SpanAssert<SELF extends SpanAssert<SELF>> extends AbstractAssert<SELF, FinishedSpan> {

    /**
     * Creates a new instance of {@link SpanAssert}.
     * @param actual actual object to assert
     */
    protected SpanAssert(FinishedSpan actual) {
        super(actual, SpanAssert.class);
    }

    /**
     * Creates the assert object for {@link FinishedSpan}.
     * @param actual span to assert against
     * @return span assertions
     */
    public static SpanAssert assertThat(FinishedSpan actual) {
        return new SpanAssert(actual);
    }

    /**
     * Creates the assert object for {@link FinishedSpan}.
     * @param actual span to assert against
     * @return span assertions
     */
    public static SpanAssert then(FinishedSpan actual) {
        return new SpanAssert(actual);
    }

    /**
     * Verifies that this span has no tags.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpan).hasNoTags();
     *
     * // assertions fail
     * assertThat(finishedSpanWithTags).hasNoTags();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has tags.
     * @since 1.0.0
     */
    public SELF hasNoTags() {
        isNotNull();
        Map<String, String> tags = this.actual.getTags();
        if (!tags.isEmpty()) {
            failWithMessage("Span should have no tags but has <%s>", tags);
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has a tag with key.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithTagFoo).hasTagWithKey("foo");
     *
     * // assertions fail
     * assertThat(finishedSpanWithNoTags).hasTagWithKey("foo");</code></pre>
     * @param key tag key name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span doesn't have a tag with given key.
     * @since 1.0.0
     */
    public SELF hasTagWithKey(String key) {
        isNotNull();
        if (!this.actual.getTags().containsKey(key)) {
            failWithMessage("Span should have a tag with key <%s> but it's not there. List of all keys <%s>", key,
                    this.actual.getTags().keySet());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has a tag with key.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithTagFoo).hasTagWithKey("foo");
     *
     * // assertions fail
     * assertThat(finishedSpanWithNoTags).hasTagWithKey("foo");</code></pre>
     * @param key tag key name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span doesn't have a tag with given key.
     * @since 1.0.0
     */
    public SELF hasTagWithKey(KeyName key) {
        return hasTagWithKey(key.asString());
    }

    /**
     * Verifies that this span has a tag with key and value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithTag).hasTag("tagKey", "tagValue");
     *
     * // assertions fail
     * assertThat(finishedSpanWithNoTags).hasTag("tagKey", "tagValue");</code></pre>
     * @param key tag key name
     * @param value tag value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span doesn't have a tag with given key and value.
     * @since 1.0.0
     */
    public SELF hasTag(String key, String value) {
        isNotNull();
        hasTagWithKey(key);
        Map<String, String> tags = this.actual.getTags();
        String tagValue = tags.get(key);
        if (!tagValue.equals(value)) {
            failWithMessage(
                    "Span should have a tag with key <%s> and value <%s>. The key is correct but the value is <%s>",
                    key, value, tagValue);
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has a tag with key and value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithTag).hasTag("tagKey", "tagValue");
     *
     * // assertions fail
     * assertThat(finishedSpanWithNoTags).hasTag("tagKey", "tagValue");</code></pre>
     * @param key tag key name
     * @param value tag value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span doesn't have a tag with given key and value.
     * @since 1.0.0
     */
    public SELF hasTag(KeyName key, String value) {
        return hasTag(key.asString(), value);
    }

    /**
     * Verifies that this span does not have a tag with key.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithNoTags).doesNotHaveTagWithKey("foo");
     *
     * // assertions fail
     * assertThat(finishedSpanWithFooTag).doesNotHaveTagWithKey("foo");</code></pre>
     * @param key tag key name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has a tag with given key.
     * @since 1.0.0
     */
    public SELF doesNotHaveTagWithKey(String key) {
        isNotNull();
        if (this.actual.getTags().containsKey(key)) {
            failWithMessage("Span should not have a tag with key <%s>", key, this.actual.getTags().keySet());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span does not have a tag with key.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithNoTags).doesNotHaveTagWithKey("foo");
     *
     * // assertions fail
     * assertThat(finishedSpanWithFooTag).doesNotHaveTagWithKey("foo");</code></pre>
     * @param key tag key name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has a tag with given key.
     * @since 1.0.0
     */
    public SELF doesNotHaveTagWithKey(KeyName key) {
        return doesNotHaveTagWithKey(key.asString());
    }

    /**
     * Verifies that this span does not have a tag with key and value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithNoTags).doesNotHaveTag("tagKey", "tagValue");
     *
     * // assertions fail
     * assertThat(finishedSpanWithFooTag).doesNotHaveTag("foo", "tagValue");</code></pre>
     * @param key tag key name
     * @param value tag value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has a tag with given key and value.
     * @since 1.0.0
     */
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

    /**
     * Verifies that this span does not have a tag with key and value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(finishedSpanWithNoTags).doesNotHaveTag("tagKey", "tagValue");
     *
     * // assertions fail
     * assertThat(finishedSpanWithFooTag).doesNotHaveTag("foo", "tagValue");</code></pre>
     * @param key tag key name
     * @param value tag value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has a tag with given key and value.
     * @since 1.0.0
     */
    public SELF doesNotHaveTag(KeyName key, String value) {
        return doesNotHaveTag(key.asString(), value);
    }

    /**
     * Verifies that this span is started.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(startedSpan).isStarted();
     *
     * // assertions fail
     * assertThat(notStartedSpan).isStarted();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has not been started
     * @since 1.0.0
     */
    public SELF isStarted() {
        isNotNull();
        if (this.actual.getStartTimestamp().getEpochSecond() == 0) {
            failWithMessage("Span should be started");
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span is not started.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(notStartedSpan).isNotStarted();
     *
     * // assertions fail
     * assertThat(startedSpan).isNotStarted();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has been started
     * @since 1.0.0
     */
    public SELF isNotStarted() {
        isNotNull();
        if (this.actual.getStartTimestamp().getEpochSecond() != 0) {
            failWithMessage("Span should not be started");
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span is ended.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(endedSpan).isEnded();
     *
     * // assertions fail
     * assertThat(notEndedSpan).isEnded();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has not been ended
     * @since 1.0.0
     */
    public SELF isEnded() {
        isNotNull();
        if (this.actual.getEndTimestamp().toEpochMilli() == 0) {
            failWithMessage("Span should be ended");
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span is not ended.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(notEndedSpan).isNotEnded();
     *
     * // assertions fail
     * assertThat(endedSpan).isNotEnded();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has been ended
     * @since 1.0.0
     */
    public SELF isNotEnded() {
        isNotNull();
        if (this.actual.getEndTimestamp().toEpochMilli() != 0) {
            failWithMessage("Span should not be ended");
        }
        return (SELF) this;
    }

    /**
     * Syntactic sugar to assert a throwable on a {@link FinishedSpan#getError()}.
     * @return {@link SpanAssertReturningAssert}
     */
    public SpanAssertReturningAssert assertThatThrowable() {
        return new SpanAssertReturningAssert(actual.getError(), this);
    }

    /**
     * Syntactic sugar to assert a throwable on a {@link FinishedSpan#getError()}.
     * @return {@link SpanAssertReturningAssert}
     */
    public SpanAssertReturningAssert thenThrowable() {
        return assertThatThrowable();
    }

    /**
     * Verifies that this span has remote service name equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(remoteServiceNameContainingSpan).hasRemoteServiceNameEqualTo("foo");
     *
     * // assertions fail
     * assertThat(remoteServiceNameMissingSpan).hasRemoteServiceNameEqualTo("foo");</code></pre>
     * @param remoteServiceName remote service name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have remote service name equal to the given
     * value
     * @since 1.0.0
     */
    public SELF hasRemoteServiceNameEqualTo(String remoteServiceName) {
        isNotNull();
        if (!remoteServiceName.equals(this.actual.getRemoteServiceName())) {
            failWithMessage("Span should have remote service name equal to <%s> but has <%s>", remoteServiceName,
                    this.actual.getRemoteServiceName());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span does not have remote service name equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(remoteServiceNameMissingSpan).doesNotHaveRemoteServiceNameEqualTo("foo");
     *
     * // assertions fail
     * assertThat(remoteServiceNameContainingSpan).doesNotHaveRemoteServiceNameEqualTo("foo");</code></pre>
     * @param remoteServiceName remote service name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does has remote service name equal to the given
     * value
     * @since 1.0.0
     */
    public SELF doesNotHaveRemoteServiceNameEqualTo(String remoteServiceName) {
        isNotNull();
        if (remoteServiceName.equals(this.actual.getRemoteServiceName())) {
            failWithMessage("Span should not have remote service name equal to <%s>", remoteServiceName);
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has span kind equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(clientSpan).hasKindEqualTo(Span.Kind.CLIENT);
     *
     * // assertions fail
     * assertThat(serverSpan).hasKindEqualTo(Span.Kind.SERVER);</code></pre>
     * @param kind span kind
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have span kind equal to the given value
     * @since 1.0.0
     */
    public SELF hasKindEqualTo(Span.Kind kind) {
        isNotNull();
        if (!kind.equals(this.actual.getKind())) {
            failWithMessage("Span should have span kind equal to <%s> but has <%s>", kind, this.actual.getKind());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span doesn't have span kind equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(serverSpan).doesNotHaveKindEqualTo(Span.Kind.CLIENT);
     *
     * // assertions fail
     * assertThat(clientSpan).doesNotHaveKindEqualTo(Span.Kind.SERVER);</code></pre>
     * @param kind span kind
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has span kind equal to the given value
     * @since 1.0.0
     */
    public SELF doesNotHaveKindEqualTo(Span.Kind kind) {
        isNotNull();
        if (kind.equals(this.actual.getKind())) {
            failWithMessage("Span should not have span kind equal to <%s>", kind);
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has an event with a given name.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithEventFoo).hasEventWithNameEqualTo("foo");
     *
     * // assertions fail
     * assertThat(spanWithNoEvents).hasEventWithNameEqualTo("foo");</code></pre>
     * @param eventName name of the event
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have span kind equal to the given value
     * @since 1.0.0
     */
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

    /**
     * Verifies that this span does not have an event with a given name.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithNoEvents).doesNotHaveEventWithNameEqualTo("foo");
     *
     * // assertions fail
     * assertThat(spanWithEventFoo).doesNotHaveEventWithNameEqualTo("foo");</code></pre>
     * @param eventName name of the event
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has span kind equal to the given value
     * @since 1.0.0
     */
    public SELF doesNotHaveEventWithNameEqualTo(String eventName) {
        isNotNull();
        List<String> eventNames = eventNames();
        if (eventNames.contains(eventName)) {
            failWithMessage("Span should not have an event with name <%s>", eventName);
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has name equal to the given value
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanNamedFoo).hasNameEqualTo("foo");
     *
     * // assertions fail
     * assertThat(spanNamedBar).hasNameEqualTo("foo");</code></pre>
     * @param spanName span name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have name equal to the given value
     * @since 1.0.0
     */
    public SELF hasNameEqualTo(String spanName) {
        isNotNull();
        if (!this.actual.getName().equals(spanName)) {
            failWithMessage("Span should have a name <%s> but has <%s>", spanName, this.actual.getName());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span does not have name equal to the given value
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanNamedFoo).doesNotHaveNameEqualTo("bar");
     *
     * // assertions fail
     * assertThat(spanNamedBar).doesNotHaveNameEqualTo("bar");</code></pre>
     * @param spanName span name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have name equal to the given value
     * @since 1.0.0
     */
    public SELF doesNotHaveNameEqualTo(String spanName) {
        isNotNull();
        if (this.actual.getName().equals(spanName)) {
            failWithMessage("Span should not have a name <%s>", spanName, this.actual.getName());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has ip equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithIpLocalhost).hasIpEqualTo("127.0.0.1");
     *
     * // assertions fail
     * assertThat(spanWithNoIp).hasIpEqualTo("127.0.0.1");</code></pre>
     * @param ip ip
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have ip equal to the given value
     * @since 1.0.0
     */
    public SELF hasIpEqualTo(String ip) {
        isNotNull();
        if (!this.actual.getRemoteIp().equals(ip)) {
            failWithMessage("Span should have ip equal to <%s> but has <%s>", ip, this.actual.getRemoteIp());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span does not have ip equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithNoIp).doesNotHaveIpEqualTo("127.0.0.1");
     *
     * // assertions fail
     * assertThat(spanWithIpLocalhost).doesNotHaveIpEqualTo("127.0.0.1");</code></pre>
     * @param ip ip
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has ip equal to the given value
     * @since 1.0.0
     */
    public SELF doesNotHaveIpEqualTo(String ip) {
        isNotNull();
        if (this.actual.getRemoteIp().equals(ip)) {
            failWithMessage("Span should not have ip equal to <%s>", ip, this.actual.getRemoteIp());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has ip set.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithIpLocalhost).hasIpThatIsNotBlank();
     *
     * // assertions fail
     * assertThat(spanWithNoIp).hasIpThatIsNotBlank();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have ip set
     * @since 1.0.0
     */
    public SELF hasIpThatIsNotBlank() {
        isNotNull();
        if (StringUtils.isBlank(this.actual.getRemoteIp())) {
            failWithMessage("Span should have ip that is not blank");
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has ip set.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithNoIp).hasIpThatIsBlank();
     *
     * // assertions fail
     * assertThat(spanWithIpLocalhost).hasIpThatIsBlank();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span does not have ip set
     * @since 1.0.0
     */
    public SELF hasIpThatIsBlank() {
        isNotNull();
        if (StringUtils.isNotBlank(this.actual.getRemoteIp())) {
            failWithMessage("Span should have ip that is blank");
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has port equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithPort80).hasPortEqualTo(80);
     *
     * // assertions fail
     * assertThat(spanWithPort80).hasPortEqualTo(7777);</code></pre>
     * @param port port
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has port equal to the given value
     * @since 1.0.0
     */
    public SELF hasPortEqualTo(int port) {
        isNotNull();
        if (this.actual.getRemotePort() != port) {
            failWithMessage("Span should have port equal to <%s> but has <%s>", port, this.actual.getRemotePort());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span doesn't have port equal to the given value.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithPort80).doesNotHavePortEqualTo(7777);
     *
     * // assertions fail
     * assertThat(spanWithPort80).doesNotHavePortEqualTo(80);</code></pre>
     * @param port port
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has port equal to the given value
     * @since 1.0.0
     */
    public SELF doesNotHavePortEqualTo(int port) {
        isNotNull();
        if (this.actual.getRemotePort() == port) {
            failWithMessage("Span should not have port equal to <%s>", port, this.actual.getRemotePort());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span doesn't have a port set.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithNoPort).hasPortThatIsNotSet();
     *
     * // assertions fail
     * assertThat(spanWithPort80).hasPortThatIsNotSet();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has port that was set
     * @since 1.0.0
     */
    public SELF hasPortThatIsNotSet() {
        isNotNull();
        if (this.actual.getRemotePort() != 0) {
            failWithMessage("Span should have port that is not set but was set to <%s>", this.actual.getRemotePort());
        }
        return (SELF) this;
    }

    /**
     * Verifies that this span has a port set.
     * <p>
     * Examples: <pre><code class='java'> // assertions succeed
     * assertThat(spanWithPort80).hasPortThatIsSet();
     *
     * // assertions fail
     * assertThat(spanWithNoPort).hasPortThatIsSet();</code></pre>
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if span has port that wasn't set
     * @since 1.0.0
     */
    public SELF hasPortThatIsSet() {
        isNotNull();
        if (this.actual.getRemotePort() == 0) {
            failWithMessage("Span should have port that is set but wasn't");
        }
        return (SELF) this;
    }

    /**
     * Syntactic sugar that extends {@link AbstractThrowableAssert} methods with an option
     * to go back to {@link SpanAssert}.
     *
     * @since 1.0.0
     */
    public static class SpanAssertReturningAssert
            extends AbstractThrowableAssert<SpanAssertReturningAssert, Throwable> {

        private final SpanAssert spanAssert;

        /**
         * Creates a new instance of {@link SpanAssertReturningAssert}.
         * @param throwable throwable to assert
         * @param spanAssert span assert to go back to
         */
        public SpanAssertReturningAssert(Throwable throwable, SpanAssert spanAssert) {
            super(throwable, SpanAssertReturningAssert.class);
            this.spanAssert = spanAssert;
        }

        /**
         * Goes back to the previous {@link SpanAssert}. Allows better fluent assertions.
         * @return previous {@link SpanAssert}
         */
        public SpanAssert backToSpan() {
            return this.spanAssert;
        }

    }

}
