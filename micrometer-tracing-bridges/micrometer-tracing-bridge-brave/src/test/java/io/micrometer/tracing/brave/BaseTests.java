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

package io.micrometer.tracing.brave;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Test class to be embedded in the docs. They use Tracing's API with Brave as tracer
 * implementation.
 *
 * @author Marcin Grzejszczak
 */
class BaseTests {

	TestSpanHandler spans = new TestSpanHandler();

	StrictCurrentTraceContext braveCurrentTraceContext = StrictCurrentTraceContext.create();

	BraveCurrentTraceContext bridgeContext = new BraveCurrentTraceContext(this.braveCurrentTraceContext);

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.braveCurrentTraceContext)
			.sampler(Sampler.ALWAYS_SAMPLE).addSpanHandler(this.spans).build();

	brave.Tracer braveTracer = this.tracing.tracer();

	Tracer tracer = new BraveTracer(this.braveTracer, this.bridgeContext, new BraveBaggageManager());

	@BeforeEach
	void setup() {
		this.spans.clear();
	}

	@AfterEach
	void close() {
		this.tracing.close();
		this.braveCurrentTraceContext.close();
	}

	@Test
	void should_create_a_span_with_tracer() {
		String taxValue = "10";

		// tag::manual_span_creation[]
		// Start a span. If there was a span present in this thread it will become
		// the `newSpan`'s parent.
		Span newSpan = this.tracer.nextSpan().name("calculateTax");
		try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
			// ...
			// You can tag a span
			newSpan.tag("taxValue", taxValue);
			// ...
			// You can log an event on a span
			newSpan.event("taxCalculated");
		}
		finally {
			// Once done remember to end the span. This will allow collecting
			// the span to send it to a distributed tracing system e.g. Zipkin
			newSpan.end();
		}
		// end::manual_span_creation[]

		then(this.spans).hasSize(1);
		then(this.spans.get(0).name()).isEqualTo("calculateTax");
		then(this.spans.get(0).tags()).containsEntry("taxValue", "10");
		then(this.spans.get(0).annotations()).hasSize(1);
	}

	@Test
	void should_continue_a_span_with_tracer() throws Exception {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String taxValue = "10";
		// tag::manual_span_continuation[]
		Span spanFromThreadX = this.tracer.nextSpan().name("calculateTax");
		try (Tracer.SpanInScope ws = this.tracer.withSpan(spanFromThreadX.start())) {
			executorService.submit(() -> {
				// Pass the span from thread X
				Span continuedSpan = spanFromThreadX;
				// ...
				// You can tag a span
				continuedSpan.tag("taxValue", taxValue);
				// ...
				// You can log an event on a span
				continuedSpan.event("taxCalculated");
			}).get();
		}
		finally {
			spanFromThreadX.end();
		}
		// end::manual_span_continuation[]

		BDDAssertions.then(spans).hasSize(1);
		BDDAssertions.then(spans.get(0).name()).isEqualTo("calculateTax");
		BDDAssertions.then(spans.get(0).tags()).containsEntry("taxValue", "10");
		BDDAssertions.then(spans.get(0).annotations()).hasSize(1);
		executorService.shutdown();
	}

	@Test
	void should_start_a_span_with_explicit_parent() throws Exception {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		String commissionValue = "10";
		Span initialSpan = this.tracer.nextSpan().name("calculateTax").start();

		executorService.submit(() -> {
			// tag::manual_span_joining[]
			// let's assume that we're in a thread Y and we've received
			// the `initialSpan` from thread X. `initialSpan` will be the parent
			// of the `newSpan`
			Span newSpan = null;
			try (Tracer.SpanInScope ws = this.tracer.withSpan(initialSpan)) {
				newSpan = this.tracer.nextSpan().name("calculateCommission");
				// ...
				// You can tag a span
				newSpan.tag("commissionValue", commissionValue);
				// ...
				// You can log an event on a span
				newSpan.event("commissionCalculated");
			}
			finally {
				// Once done remember to end the span. This will allow collecting
				// the span to send it to e.g. Zipkin. The tags and events set on the
				// newSpan will not be present on the parent
				if (newSpan != null) {
					newSpan.end();
				}
			}
			// end::manual_span_joining[]
		}).get();

		Optional<MutableSpan> calculateTax = spans.spans().stream()
				.filter(span -> span.name().equals("calculateCommission")).findFirst();
		BDDAssertions.then(calculateTax).isPresent();
		BDDAssertions.then(calculateTax.get().tags()).containsEntry("commissionValue", "10");
		BDDAssertions.then(calculateTax.get().annotations()).hasSize(1);
		executorService.shutdown();
	}

}
