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

package io.micrometer.tracing.otel.bridge;

import io.micrometer.tracing.Span;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class OtelSpanBuilderTests {

	@Test
	void should_set_child_span_when_using_builders() {
		SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
				.setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn()).build();
		OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
				.setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader())).build();
		io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracer("io.micrometer.micrometer-tracing");

		Span.Builder builder = new OtelSpanBuilder(otelTracer.spanBuilder("foo"));
		Span parentSpan = OtelSpan.fromOtel(otelTracer.spanBuilder("bar").startSpan());

		Span child = builder.setParent(parentSpan.context()).start();

		then(child.context().traceId()).isEqualTo(parentSpan.context().traceId());
		then(child.context().parentId()).isEqualTo(parentSpan.context().spanId());
	}

}
