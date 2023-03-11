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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.http.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;

import java.util.regex.Pattern;

/**
 * OpenTelemetry implementation of a {@link HttpServerHandler}.
 *
 * @author Marcin Grzejszczak
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 */
public class OtelHttpServerHandler implements HttpServerHandler {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(OtelHttpClientHandler.class);

    private static final ContextKey<HttpServerRequest> REQUEST_CONTEXT_KEY = ContextKey
        .named(OtelHttpServerHandler.class.getName() + ".request");

    private final HttpRequestParser httpServerRequestParser;

    private final HttpResponseParser httpServerResponseParser;

    private final Pattern pattern;

    private final Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter;

    /**
     * Creates a new instance of {@link OtelHttpServerHandler}.
     * @param openTelemetry open telemetry
     * @param httpServerRequestParser http client request parser
     * @param httpServerResponseParser http client response parser
     * @param skipPattern skip pattern
     * @param httpAttributesExtractor http attributes extractor
     */
    public OtelHttpServerHandler(OpenTelemetry openTelemetry, @Nullable HttpRequestParser httpServerRequestParser,
            @Nullable HttpResponseParser httpServerResponseParser, Pattern skipPattern,
            HttpServerAttributesGetter<HttpServerRequest, HttpServerResponse> httpAttributesExtractor) {
        this.httpServerRequestParser = httpServerRequestParser;
        this.httpServerResponseParser = httpServerResponseParser;
        this.pattern = skipPattern;
        this.instrumenter = Instrumenter
            .<HttpServerRequest, HttpServerResponse>builder(openTelemetry, "io.micrometer.tracing",
                    HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(HttpServerAttributesExtractor.create(httpAttributesExtractor,
                    new HttpRequestNetServerAttributesExtractor()))
            .addAttributesExtractor(new PathAttributeExtractor())
            .buildServerInstrumenter(getGetter());
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
