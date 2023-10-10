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
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.http.HttpClientRequest;
import io.micrometer.tracing.http.HttpClientResponse;
import io.micrometer.tracing.http.HttpRequest;
import io.micrometer.tracing.SamplerFunction;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpRequestParser;
import io.micrometer.tracing.http.HttpResponseParser;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;

/**
 * OpenTelemetry implementation of a {@link HttpClientHandler}.
 *
 * @author Marcin Grzejszczak
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
public class OtelHttpClientHandler implements HttpClientHandler {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(OtelHttpClientHandler.class);

    private static final ContextKey<HttpClientRequest> REQUEST_CONTEXT_KEY = ContextKey
        .named(OtelHttpClientHandler.class.getName() + ".request");

    private final HttpRequestParser httpClientRequestParser;

    private final HttpResponseParser httpClientResponseParser;

    private final SamplerFunction<HttpRequest> samplerFunction;

    private final Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter;

    /**
     * Creates a new instance of {@link OtelHttpClientHandler}.
     * @param openTelemetry open telemetry
     * @param httpClientRequestParser http client request parser
     * @param httpClientResponseParser http client response parser
     * @param samplerFunction sampler function
     * @param httpAttributesExtractor http attributes extractor
     * @deprecated use
     * {@link OtelHttpClientHandler#OtelHttpClientHandler(OpenTelemetry, HttpRequestParser, HttpResponseParser, SamplerFunction, HttpClientAttributesGetter, NetClientAttributesGetter)}
     */
    @Deprecated
    public OtelHttpClientHandler(OpenTelemetry openTelemetry, @Nullable HttpRequestParser httpClientRequestParser,
            @Nullable HttpResponseParser httpClientResponseParser, SamplerFunction<HttpRequest> samplerFunction,
            HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> httpAttributesExtractor) {
        this(openTelemetry, httpClientRequestParser, httpClientResponseParser, samplerFunction, httpAttributesExtractor,
                new HttpRequestNetClientAttributesExtractor());
    }

    /**
     * Creates a new instance of {@link OtelHttpClientHandler}.
     * @param openTelemetry open telemetry
     * @param httpClientRequestParser http client request parser
     * @param httpClientResponseParser http client response parser
     * @param samplerFunction sampler function
     * @param httpAttributesExtractor http attributes extractor
     */
    public OtelHttpClientHandler(OpenTelemetry openTelemetry, @Nullable HttpRequestParser httpClientRequestParser,
            @Nullable HttpResponseParser httpClientResponseParser, SamplerFunction<HttpRequest> samplerFunction,
            HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> httpAttributesExtractor,
            NetClientAttributesGetter<HttpClientRequest, HttpClientResponse> netAttributesGetter) {
        this.httpClientRequestParser = httpClientRequestParser;
        this.httpClientResponseParser = httpClientResponseParser;
        this.samplerFunction = samplerFunction;
        this.instrumenter = Instrumenter
            .<HttpClientRequest, HttpClientResponse>builder(openTelemetry, "io.micrometer.tracing",
                    HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(NetClientAttributesExtractor.create(new HttpRequestNetClientAttributesExtractor()))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesExtractor, netAttributesGetter))
            .addAttributesExtractor(new PathAttributeExtractor())
            .buildClientInstrumenter(HttpClientRequest::header);
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
        if (request.remotePort() != 0) {
            result.remoteIpAndPort(request.remoteIp(), request.remotePort());
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
        Context contextToEnd = Context.current().with(otelSpanWrapper.delegate);
        instrumenter.end(contextToEnd, otelContext.get(REQUEST_CONTEXT_KEY), response, response.error());
    }

}
