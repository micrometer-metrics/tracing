/*
 * Copyright 2021-2021 the original author or authors.
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

package io.micrometer.tracing.handler;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.tracing.context.HttpClientHandlerContext;
import io.micrometer.core.instrument.transport.http.HttpClientRequest;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.http.HttpClientHandler;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * TracingRecordingListener that uses the Tracing API to record events for HTTP client
 * side.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class HttpClientTracingRecordingHandler extends
        HttpTracingRecordingHandler<HttpClientHandlerContext, HttpClientRequest, HttpClientResponse>
        implements TracingRecordingHandler<HttpClientHandlerContext> {

    /**
     * Creates a new instance of {@link HttpClientTracingRecordingHandler}.
     *
     * @param tracer tracer
     * @param handler http client handler
     */
    public HttpClientTracingRecordingHandler(Tracer tracer, HttpClientHandler handler) {
        super(tracer, handler::handleSend, handler::handleReceive);
    }

    /**
     *
     * Creates a new instance of {@link HttpClientTracingRecordingHandler}.
     *
     * @param tracer tracer
     * @param startFunction  function that creates a span
     * @param stopConsumer lambda to be applied on the span upon receiving the response
     */
    public HttpClientTracingRecordingHandler(Tracer tracer, Function<HttpClientRequest, Span> startFunction,
                                             BiConsumer<HttpClientResponse, Span> stopConsumer) {
        super(tracer, startFunction, stopConsumer);
    }

    @Override
    public boolean supportsContext(Timer.HandlerContext context) {
        return context instanceof HttpClientHandlerContext;
    }

    @Override
    HttpClientRequest getRequest(HttpClientHandlerContext ctx) {
        return ctx.getRequest();
    }

    @Override
    public String getSpanName(HttpClientHandlerContext ctx, Meter.Id id) {
        return getRequest(ctx).method();
    }

    @Override
    HttpClientResponse getResponse(HttpClientHandlerContext ctx) {
        return ctx.getResponse();
    }

}
