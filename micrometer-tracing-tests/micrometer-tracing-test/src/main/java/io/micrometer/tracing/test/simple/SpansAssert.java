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

import io.micrometer.common.docs.KeyName;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.assertj.core.api.CollectionAssert;

/**
 * Assertion methods for {@code SimpleSpan}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link SpansAssert#assertThat(Collection)} or {@link SpansAssert#then(Collection)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SpansAssert extends CollectionAssert<FinishedSpan> {

	/**
	 * Creates a new instance of {@link SpansAssert}.
	 * @param actual actual object to assert
	 */
	protected SpansAssert(Collection<FinishedSpan> actual) {
		super(actual);
	}

	/**
	 * Creates the assert object for a collection of {@link FinishedSpan}.
	 * @param actual span to assert against
	 * @return span collection assertions
	 */
	public static SpansAssert assertThat(Collection<FinishedSpan> actual) {
		return new SpansAssert(actual);
	}

	/**
	 * Creates the assert object for a collection of {@link FinishedSpan}.
	 * @param actual span to assert against
	 * @return span collection assertions
	 */
	public static SpansAssert then(Collection<FinishedSpan> actual) {
		return new SpansAssert(actual);
	}

	/**
	 * Verifies that all spans have the same trace id.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpan)).haveSameTraceId();
	 *
	 * // assertions fail
	 * assertThat(Arrays.asList(finishedSpanWithTraceIdX, finishedSpanWithTraceIdY)).haveSameTraceId();</code></pre>
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if spans do not have the same trace id.
	 *
	 * @since 1.0.0
	 */
	public SpansAssert haveSameTraceId() {
		isNotEmpty();
		List<String> traceIds = this.actual.stream().map(FinishedSpan::getTraceId).distinct()
				.collect(Collectors.toList());
		if (traceIds.size() != 1) {
			failWithMessage("Spans should have same trace ids but found %s trace ids. Found following spans \n%s",
					traceIds, spansAsString());
		}
		return this;
	}

	private String spansAsString() {
		return this.actual.stream().map(Object::toString).collect(Collectors.joining("\n"));
	}

	/**
	 * Verifies that there is a span with a given name.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasASpanWithName("foo");
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithNameBar)).hasASpanWithName("foo");</code></pre>
	 * @param name searched span name
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithName(String name) {
		isNotEmpty();
		extractSpanWithName(name);
		return this;
	}

	/**
	 * Verifies that there is a span with a given name and also given assertion is met.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasASpanWithName("foo", spanAssert -> spanAssert.isStarted());
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(notFinishedSpanWithNameFoo)).hasASpanWithName("foo", spanAssert -> spanAssert.isStarted());</code></pre>
	 * @param name searched span name
	 * @param spanConsumer assertion to be executed for each span
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name
	 * @throws AssertionError if the span assertion is not met
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithName(String name, Consumer<SpanAssert> spanConsumer) {
		isNotEmpty();
		atLeastOneSpanPassesTheAssertion(name, spanConsumer);
		return this;
	}

	private void atLeastOneSpanPassesTheAssertion(String name, Consumer<SpanAssert> spanConsumer) {
		FinishedSpan finishedSpan = this.actual.stream().filter(f -> name.equals(f.getName())).filter(f -> {
			try {
				spanConsumer.accept(SpanAssert.assertThat(f));
				return true;
			}
			catch (AssertionError e) {
				return false;
			}
		}).findFirst().orElse(null);
		if (finishedSpan == null) {
			failWithMessage(
					"Not a single span with name <%s> was found or has passed the assertion. Found following spans %s",
					name, spansAsString());
		}
	}

	/**
	 * Verifies that there is a span with a given name (ignoring case) and also given
	 * assertion is met.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasASpanWithNameIgnoreCase("FoO", spanAssert -> spanAssert.isStarted());
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(notFinishedSpanWithNameFoo)).hasASpanWithNameIgnoreCase("FoO", spanAssert -> spanAssert.isStarted());</code></pre>
	 * @param name searched span name
	 * @param spanConsumer assertion to be executed for each span
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name
	 * @throws AssertionError if the span assertion is not met
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithNameIgnoreCase(String name, Consumer<SpanAssert> spanConsumer) {
		isNotEmpty();
		atLeastOneSpanPassesTheAssertionIgnoreCase(name, spanConsumer);
		return this;
	}

	private void atLeastOneSpanPassesTheAssertionIgnoreCase(String name, Consumer<SpanAssert> spanConsumer) {
		FinishedSpan finishedSpan = this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName())).filter(f -> {
			try {
				spanConsumer.accept(SpanAssert.assertThat(f));
				return true;
			}
			catch (AssertionError e) {
				return false;
			}
		}).findFirst().orElse(null);
		if (finishedSpan == null) {
			failWithMessage(
					"Not a single span with name <%s> was found (ignoring case) or has passed the assertion. Found following spans %s",
					name, spansAsString());
		}
	}

	private FinishedSpan extractSpanWithName(String name) {
		return this.actual.stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow(() -> {
			failWithMessage(
					"There should be at least one span with name <%s> but found none. Found following spans \n%s", name,
					spansAsString());
			return new AssertionError();
		});
	}

	private FinishedSpan extractSpanWithNameIgnoreCase(String name) {
		return this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName())).findFirst().orElseThrow(() -> {
			failWithMessage(
					"There should be at least one span with name (ignore case) <%s> but found none. Found following spans \n%s",
					name, spansAsString());
			return new AssertionError();
		});
	}

	/**
	 * Verifies that there is a span with a given name (ignoring case).
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasASpanWithNameIgnoreCase("FoO");
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithNameBar)).hasASpanWithNameIgnoreCase("FoO");</code></pre>
	 * @param name searched span name
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name (ignoring case)
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithNameIgnoreCase(String name) {
		isNotEmpty();
		this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName())).findFirst().orElseThrow(() -> {
			failWithMessage(
					"There should be at least one span with name (ignoring case) <%s> but found none. Found following spans \n%s",
					name, spansAsString());
			return new AssertionError();
		});
		return this;
	}

	/**
	 * Provides verification for all spans having the given name.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).forAllSpansWithNameEqualTo("foo", spanAssert -> spanAssert.isStarted());
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(nonStartedSpanWithNameFoo)).forAllSpansWithNameEqualTo("foo", spanAssert -> spanAssert.isStarted());</code></pre>
	 * @param name searched span name
	 * @param spanConsumer assertion to be executed for each span
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name
	 * @throws AssertionError if there is a span with the given name but the additional
	 * assertion is not successful
	 *
	 * @since 1.0.0
	 */
	public SpansAssert forAllSpansWithNameEqualTo(String name, Consumer<SpanAssert> spanConsumer) {
		isNotEmpty();
		hasASpanWithName(name);
		this.actual.stream().filter(f -> name.equals(f.getName()))
				.forEach(f -> spanConsumer.accept(SpanAssert.then(f)));
		return this;
	}

	/**
	 * Provides verification for all spans having the given name (ignoring case).
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).forAllSpansWithNameEqualTo("foo", spanAssert -> spanAssert.isStarted());
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(nonStartedSpanWithNameFoo)).forAllSpansWithNameEqualTo("foo", spanAssert -> spanAssert.isStarted());</code></pre>
	 * @param name searched span name (ignoring case)
	 * @param spanConsumer assertion to be executed for each span
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name (ignoring case)
	 * @throws AssertionError if there is a span with the given name (ignoring case) but
	 * the additional assertion is not successful
	 *
	 * @since 1.0.0
	 */
	public SpansAssert forAllSpansWithNameEqualToIgnoreCase(String name, Consumer<SpanAssert> spanConsumer) {
		isNotEmpty();
		hasASpanWithNameIgnoreCase(name);
		this.actual.stream().filter(f -> name.equalsIgnoreCase(f.getName()))
				.forEach(f -> spanConsumer.accept(SpanAssert.then(f)));
		return this;
	}

	/**
	 * Provides verification for the first span having the given name.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).assertThatASpanWithNameEqualTo("foo").isStarted();
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(notStartedSpanWithNameFoo)).assertThatASpanWithNameEqualTo("foo").isStarted();</code></pre>
	 * @param name searched span name
	 * @return {@link SpansAssertReturningAssert} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name
	 *
	 * @since 1.0.0
	 */
	public SpansAssertReturningAssert assertThatASpanWithNameEqualTo(String name) {
		isNotEmpty();
		FinishedSpan span = extractSpanWithName(name);
		return new SpansAssertReturningAssert(this, span);
	}

	/**
	 * Provides verification for the first span having the given name.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).thenASpanWithNameEqualTo("foo").isStarted();
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(notStartedSpanWithNameFoo)).thenASpanWithNameEqualTo("foo").isStarted();</code></pre>
	 * @param name searched span name
	 * @return {@link SpansAssertReturningAssert} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name
	 *
	 * @since 1.0.0
	 */
	public SpansAssertReturningAssert thenASpanWithNameEqualTo(String name) {
		return assertThatASpanWithNameEqualTo(name);
	}

	/**
	 * Provides verification for the first span having the given name (ignoring case).
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).assertThatASpanWithNameEqualToIgnoreCase("FoO").isStarted();
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(notStartedSpanWithNameFoo)).assertThatASpanWithNameEqualToIgnoreCase("FoO").isStarted();</code></pre>
	 * @param name searched span name (ignoring case)
	 * @return {@link SpansAssertReturningAssert} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name (ignoring case)
	 *
	 * @since 1.0.0
	 */
	public SpansAssertReturningAssert assertThatASpanWithNameEqualToIgnoreCase(String name) {
		isNotEmpty();
		FinishedSpan span = extractSpanWithNameIgnoreCase(name);
		return new SpansAssertReturningAssert(this, span);
	}

	/**
	 * Provides verification for the first span having the given name (ignoring case).
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).thenASpanWithNameEqualToIgnoreCase("FoO").isStarted();
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(notStartedSpanWithNameFoo)).thenASpanWithNameEqualToIgnoreCase("FoO").isStarted();</code></pre>
	 * @param name searched span name (ignoring case)
	 * @return {@link SpansAssertReturningAssert} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with the given name (ignoring case)
	 *
	 * @since 1.0.0
	 */
	public SpansAssertReturningAssert thenASpanWithNameEqualToIgnoreCase(String name) {
		return assertThatASpanWithNameEqualToIgnoreCase(name);
	}

	/**
	 * Verifies that there is a proper number of spans.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpan)).hasNumberOfSpansEqualTo(1);
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpan)).hasNumberOfSpansEqualTo(2);</code></pre>
	 * @param expectedNumberOfSpans expected number of spans
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if the number of spans is different from the desired one
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasNumberOfSpansEqualTo(int expectedNumberOfSpans) {
		isNotEmpty();
		if (this.actual.size() != expectedNumberOfSpans) {
			failWithMessage("There should be <%s> spans but there were <%s>. Found following spans \n%s",
					expectedNumberOfSpans, this.actual.size(), spansAsString());
		}
		return this;
	}

	/**
	 * Verifies that there is a proper number of spans with the given name.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasNumberOfSpansWithNameEqualTo("foo", 1);
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasNumberOfSpansWithNameEqualTo("foo", 2);</code></pre>
	 * @param spanName span name
	 * @param expectedNumberOfSpans expected number of spans with the given name
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if the number of properly named spans is different from the
	 * desired one
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasNumberOfSpansWithNameEqualTo(String spanName, int expectedNumberOfSpans) {
		isNotEmpty();
		long spansWithNameSize = this.actual.stream().filter(f -> spanName.equals(f.getName())).count();
		if (spansWithNameSize != expectedNumberOfSpans) {
			failWithMessage("There should be <%s> spans with name <%s> but there were <%s>. Found following spans \n%s",
					expectedNumberOfSpans, spanName, spansWithNameSize, spansAsString());
		}
		return this;
	}

	/**
	 * Verifies that there is a proper number of spans with the given name (ignoring
	 * case).
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasNumberOfSpansWithNameEqualToIgnoreCase(1);
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithNameFoo)).hasNumberOfSpansWithNameEqualToIgnoreCase(2);</code></pre>
	 * @param spanName span name
	 * @param expectedNumberOfSpans expected number of spans with the given name (ignoring
	 * case)
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if the number of properly named spans is different from the
	 * desired one
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasNumberOfSpansWithNameEqualToIgnoreCase(String spanName, int expectedNumberOfSpans) {
		isNotEmpty();
		long spansWithNameSize = this.actual.stream().filter(f -> spanName.equalsIgnoreCase(f.getName())).count();
		if (spansWithNameSize != expectedNumberOfSpans) {
			failWithMessage(
					"There should be <%s> spans with name (ignoring case) <%s> but there were <%s>. Found following spans \n%s",
					expectedNumberOfSpans, spanName, spansWithNameSize, spansAsString());
		}
		return this;
	}

	/**
	 * Verifies that there is a span with a given remote service name.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithRemoteServiceNameFoo)).hasASpanWithRemoteServiceName("FoO");
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithRemoteServiceNameBar)).hasASpanWithRemoteServiceName("FoO");</code></pre>
	 * @param remoteServiceName searched remote service name
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with given remote service name
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithRemoteServiceName(String remoteServiceName) {
		isNotEmpty();
		this.actual.stream().filter(f -> remoteServiceName.equals(f.getRemoteServiceName())).findFirst()
				.orElseThrow(() -> {
					failWithMessage(
							"There should be at least one span with remote service name <%s> but found none. Found following spans \n%s",
							remoteServiceName, spansAsString());
					return new AssertionError();
				});
		return this;
	}

	/**
	 * Verifies that there is a span with a tag.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithTagFooBar)).hasASpanWithATag("foo", "bar");
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithTagBazBar)).hasASpanWithATag("foo", "bar");</code></pre>
	 * @param key expected tag key
	 * @param value expected tag value
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with given tag key and value
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithATag(String key, String value) {
		isNotEmpty();
		this.actual.stream().filter(f -> {
			String tag = f.getTags().get(key);
			return value.equals(tag);
		}).findFirst().orElseThrow(() -> {
			failWithMessage(
					"There should be at least one span with tag key <%s> and value <%s> but found none. Found following spans \n%s",
					key, value, spansAsString());
			return new AssertionError();
		});
		return this;
	}

	/**
	 * Verifies that there is a span with a tag key.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithTagFooBar)).hasASpanWithATagKey("foo");
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithTagBazBar)).hasASpanWithATagKey("foo");</code></pre>
	 * @param key expected tag key
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with given tag key
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithATagKey(String key) {
		isNotEmpty();
		this.actual.stream().filter(f -> f.getTags().containsKey(key)).findFirst().orElseThrow(() -> {
			failWithMessage(
					"There should be at least one span with tag key <%s> but found none. Found following spans \n%s",
					key, spansAsString());
			return new AssertionError();
		});
		return this;
	}

	/**
	 * Verifies that there is a span with a tag.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithTagFooBar)).hasASpanWithATag(SomeKeyName.FOO, "bar");
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithTagBazBar)).hasASpanWithATag(SomeKeyName.FOO, "baz");</code></pre>
	 * @param key expected tag key
	 * @param value expected tag value
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with given tag key
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithATag(KeyName key, String value) {
		return hasASpanWithATag(key.getKeyName(), value);
	}

	/**
	 * Verifies that there is a span with a tag key.
	 * <p>
	 * Examples: <pre><code class='java'> // assertions succeed
	 * assertThat(Collections.singletonList(finishedSpanWithTagFooBar)).hasASpanWithATagKey(SomeKeyName.FOO);
	 *
	 * // assertions fail
	 * assertThat(Collections.singletonList(finishedSpanWithTagBazBar)).hasASpanWithATagKey(SomeKeyName.FOO);</code></pre>
	 * @param key expected tag key
	 * @return {@code this} assertion object.
	 * @throws AssertionError if the actual value is {@code null}.
	 * @throws AssertionError if there is no span with given tag key
	 *
	 * @since 1.0.0
	 */
	public SpansAssert hasASpanWithATagKey(KeyName key) {
		return hasASpanWithATagKey(key.getKeyName());
	}

	/**
	 * Syntactic sugar that extends {@link SpanAssert} methods with an option to go back
	 * to {@link SpansAssert}.
	 *
	 * @since 1.0.0
	 */
	public static class SpansAssertReturningAssert extends SpanAssert<SpansAssert.SpansAssertReturningAssert> {

		private final SpansAssert spansAssert;

		/**
		 * Creates a new instance of {@link SpansAssertReturningAssert}.
		 * @param spansAssert spans assertion
		 * @param span span to assert
		 */
		public SpansAssertReturningAssert(SpansAssert spansAssert, FinishedSpan span) {
			super(span);
			this.spansAssert = spansAssert;
		}

		/**
		 * Goes back to the {@link SpansAssert}.
		 * @return {@link SpansAssert} assertion
		 */
		public SpansAssert backToSpans() {
			return this.spansAssert;
		}

	}

}
