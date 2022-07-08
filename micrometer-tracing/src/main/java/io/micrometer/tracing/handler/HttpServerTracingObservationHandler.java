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

import java.util.function.BiConsumer;
import java.util.function.Function;

import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.http.HttpResponse;
import io.micrometer.observation.transport.http.HttpServerRequest;
import io.micrometer.observation.transport.http.HttpServerResponse;
import io.micrometer.observation.transport.http.context.HttpServerContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.http.HttpServerHandler;

/**
 * TracingRecordingListener that uses the Tracing API to record events for HTTP server
 * side.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class HttpServerTracingObservationHandler extends
        HttpTracingObservationHandler<HttpServerContext, HttpServerRequest, HttpServerResponse>
        implements TracingObservationHandler<HttpServerContext> {

    /**
     * Creates a new instance of {@link HttpServerTracingObservationHandler}.
     *
     * @param tracer tracer
     * @param handler http server handler
     */
    public HttpServerTracingObservationHandler(Tracer tracer, HttpServerHandler handler) {
        super(tracer, handler::handleReceive, handler::handleSend);
    }

    /**
     *
     * Creates a new instance of {@link HttpServerTracingObservationHandler}.
     *
     * @param tracer tracer
     * @param startFunction  function that creates a span
     * @param stopConsumer lambda to be applied on the span upon receiving the response
     */
    public HttpServerTracingObservationHandler(Tracer tracer, Function<HttpServerRequest, Span> startFunction,
            BiConsumer<HttpServerResponse, Span> stopConsumer) {
        super(tracer, startFunction, stopConsumer);
    }

    @Override
    HttpServerRequest getRequest(HttpServerContext ctx) {
        return ctx.getRequest();
    }

    @Override
    public String getSpanName(HttpServerContext ctx) {
        if (ctx.getResponse() != null) {
            return spanNameFromRoute(ctx.getResponse());
        }
        return ctx.getRequest().method();
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof HttpServerContext;
    }

    // taken from Brave
    private String spanNameFromRoute(HttpResponse response) {
        int statusCode = response.statusCode();
        String method = response.method();
        if (method == null) {
            return null; // don't undo a valid name elsewhere
        }
        String route = response.route();
        if (route == null) {
            return null; // don't undo a valid name elsewhere
        }
        if (!"".equals(route)) {
            return method + " " + route;
        }
        return catchAllName(method, statusCode);
    }

    // taken from Brave
    private String catchAllName(String method, int statusCode) {
        switch (statusCode) {
        // from https://tools.ietf.org/html/rfc7231#section-6.4
        case 301:
        case 302:
        case 303:
        case 305:
        case 306:
        case 307:
            return method + " redirected";
        case 404:
            return method + " not_found";
        default:
            return null;
        }
    }

    @Override
    HttpServerResponse getResponse(HttpServerContext ctx) {
        return ctx.getResponse();
    }

}
