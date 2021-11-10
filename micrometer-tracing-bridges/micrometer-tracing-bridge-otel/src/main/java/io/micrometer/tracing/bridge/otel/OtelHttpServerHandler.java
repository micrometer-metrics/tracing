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

import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.util.StringUtils;

/**
 * OpenTelemetry implementation of a {@link HttpServerHandler}.
 *
 * @author Marcin Grzejszczak
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 */
public class OtelHttpServerHandler implements HttpServerHandler {

	private static final Log log = LogFactory.getLog(OtelHttpClientHandler.class);

	private static final ContextKey<HttpServerRequest> REQUEST_CONTEXT_KEY = ContextKey
			.named(OtelHttpServerHandler.class.getName() + ".request");

	private final HttpRequestParser httpServerRequestParser;

	private final HttpResponseParser httpServerResponseParser;

	private final Pattern pattern;

	private final Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter;

	public OtelHttpServerHandler(OpenTelemetry openTelemetry, HttpRequestParser httpServerRequestParser,
			HttpResponseParser httpServerResponseParser, SkipPatternProvider skipPatternProvider,
			HttpServerAttributesExtractor<HttpServerRequest, HttpServerResponse> httpAttributesExtractor) {
		this.httpServerRequestParser = httpServerRequestParser;
		this.httpServerResponseParser = httpServerResponseParser;
		this.pattern = skipPatternProvider.skipPattern();
		this.instrumenter = Instrumenter
				.<HttpServerRequest, HttpServerResponse>newBuilder(openTelemetry, "org.springframework.cloud.sleuth",
						HttpSpanNameExtractor.create(httpAttributesExtractor))
				.setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
				.addAttributesExtractor(new HttpRequestNetServerAttributesExtractor())
				.addAttributesExtractor(httpAttributesExtractor).addAttributesExtractor(new PathAttributeExtractor())
				.newServerInstrumenter(getGetter());
	}

	@Override
	public Span handleReceive(HttpServerRequest request) {
		String url = request.path();
		boolean shouldSkip = !StringUtils.isEmpty(url) && this.pattern.matcher(url).matches();
		if (shouldSkip) {
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		Context parentContext = Context.current();
		if (instrumenter.shouldStart(parentContext, request)) {
			Context context = instrumenter.start(parentContext, request);
			return span(context, request);
		}
		else {
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
	}

	private Span span(Context context, HttpServerRequest request) {
		io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContext(context);
		Span result = OtelSpan.fromOtel(span, context.with(REQUEST_CONTEXT_KEY, request));
		if (this.httpServerRequestParser != null) {
			this.httpServerRequestParser.parse(request, result.context(), result);
		}
		return result;
	}

	@Override
	public void handleSend(HttpServerResponse response, Span span) {
		OtelSpan otelSpanWrapper = (OtelSpan) span;
		if (!otelSpanWrapper.delegate.getSpanContext().isValid()) {
			if (log.isDebugEnabled()) {
				log.debug("Not doing anything because the span is invalid");
			}
			return;
		}

		if (this.httpServerResponseParser != null) {
			this.httpServerResponseParser.parse(response, span.context(), span);
		}
		OtelTraceContext traceContext = otelSpanWrapper.context();
		Context otelContext = traceContext.context();
		// response.getRequest() too often returns null
		instrumenter.end(otelContext, otelContext.get(REQUEST_CONTEXT_KEY), response, response.error());
	}

	private TextMapGetter<HttpServerRequest> getGetter() {
		return new TextMapGetter<HttpServerRequest>() {
			@Override
			public Iterable<String> keys(HttpServerRequest carrier) {
				return carrier.headerNames();
			}

			@Override
			public String get(HttpServerRequest carrier, String key) {
				return carrier.header(key);
			}
		};
	}

}
