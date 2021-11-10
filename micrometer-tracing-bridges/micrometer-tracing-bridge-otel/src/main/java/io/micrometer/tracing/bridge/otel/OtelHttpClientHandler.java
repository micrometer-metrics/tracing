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

package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry implementation of a {@link HttpClientHandler}.
 *
 * @author Marcin Grzejszczak
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 */
public class OtelHttpClientHandler implements HttpClientHandler {

	private static final Log log = LogFactory.getLog(OtelHttpClientHandler.class);

	private static final ContextKey<HttpClientRequest> REQUEST_CONTEXT_KEY = ContextKey
			.named(OtelHttpClientHandler.class.getName() + ".request");

	private final HttpRequestParser httpClientRequestParser;

	private final HttpResponseParser httpClientResponseParser;

	private final SamplerFunction<HttpRequest> samplerFunction;

	private final Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter;

	public OtelHttpClientHandler(OpenTelemetry openTelemetry, @Nullable HttpRequestParser httpClientRequestParser,
			@Nullable HttpResponseParser httpClientResponseParser, SamplerFunction<HttpRequest> samplerFunction,
			HttpClientAttributesExtractor<HttpClientRequest, HttpClientResponse> httpAttributesExtractor) {
		this.httpClientRequestParser = httpClientRequestParser;
		this.httpClientResponseParser = httpClientResponseParser;
		this.samplerFunction = samplerFunction;
		this.instrumenter = Instrumenter
				.<HttpClientRequest, HttpClientResponse>newBuilder(openTelemetry, "org.springframework.cloud.sleuth",
						HttpSpanNameExtractor.create(httpAttributesExtractor))
				.setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
				.addAttributesExtractor(new HttpRequestNetClientAttributesExtractor())
				.addAttributesExtractor(httpAttributesExtractor).addAttributesExtractor(new PathAttributeExtractor())
				.newClientInstrumenter(HttpClientRequest::header);
	}

	@Override
	public Span handleSend(HttpClientRequest request) {
		Context parentContext = Context.current();
		return startSpan(request, parentContext);
	}

	@Override
	public Span handleSend(HttpClientRequest request, TraceContext parent) {
		Context parentContext = OtelTraceContext.toOtelContext(parent);
		return startSpan(request, parentContext);
	}

	private Span startSpan(HttpClientRequest request, Context parentContext) {
		if (Boolean.FALSE.equals(this.samplerFunction.trySample(request))) {
			if (log.isDebugEnabled()) {
				log.debug("Returning an invalid span since url [" + request.path() + "] is on a list of urls to skip");
			}
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		if (instrumenter.shouldStart(parentContext, request)) {
			Context context = instrumenter.start(parentContext, request);
			return span(context, request);
		}
		else {
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
	}

	private Span span(Context context, HttpClientRequest request) {
		io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContext(context);
		Span result = OtelSpan.fromOtel(span, context.with(REQUEST_CONTEXT_KEY, request));
		if (this.httpClientRequestParser != null) {
			this.httpClientRequestParser.parse(request, result.context(), result);
		}
		return result;
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {
		OtelSpan otelSpanWrapper = (OtelSpan) span;
		if (!otelSpanWrapper.delegate.getSpanContext().isValid()) {
			if (log.isDebugEnabled()) {
				log.debug("Not doing anything because the span is invalid");
			}
			return;
		}

		if (this.httpClientResponseParser != null) {
			this.httpClientResponseParser.parse(response, span.context(), span);
		}
		OtelTraceContext traceContext = otelSpanWrapper.context();
		Context otelContext = traceContext.context();
		// TODO this must be otelContext, but OpenTelemetry context handling is not
		// entirely correct here atm
		Context contextToEnd = Context.current().with(otelSpanWrapper.delegate);
		// response.getRequest() too often returns null
		instrumenter.end(contextToEnd, otelContext.get(REQUEST_CONTEXT_KEY), response, response.error());
	}

}
