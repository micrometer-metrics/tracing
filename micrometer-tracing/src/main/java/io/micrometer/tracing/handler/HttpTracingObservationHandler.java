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
import java.util.function.BiFunction;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;
import io.micrometer.observation.transport.http.context.HttpContext;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@SuppressWarnings({"rawtypes", "unchecked"})
abstract class HttpTracingObservationHandler<CTX extends HttpContext, REQ extends HttpRequest, RES extends HttpResponse>
        implements TracingObservationHandler<CTX> {

    private final Tracer tracer;

    private final CurrentTraceContext currentTraceContext;

    private final BiFunction<REQ, Span, Span> startFunction;

    private final BiConsumer<RES, Span> stopConsumer;

    HttpTracingObservationHandler(Tracer tracer, BiFunction<REQ, Span, Span> startFunction,
            BiConsumer<RES, Span> stopConsumer) {
        this.tracer = tracer;
        this.currentTraceContext = tracer.currentTraceContext();
        this.startFunction = startFunction;
        this.stopConsumer = stopConsumer;
    }

    @Override
    public void onStart(CTX ctx) {
        Span parentSpan = getParentSpan(ctx);
        REQ request = getRequest(ctx);
        Span span = this.startFunction.apply(request, parentSpan);
        getTracingContext(ctx).setSpan(span);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof HttpContext;
    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

    abstract REQ getRequest(CTX ctx);

    @Override
    public void onError(CTX context) {

    }

    @Override
    public void onStop(CTX ctx) {
        Span span = getRequiredSpan(ctx);
        span.name(getSpanName(ctx));
        tagSpan(ctx, span);
        RES response = getResponse(ctx);
        error(response, span);
        this.stopConsumer.accept(response, span);
    }

    abstract RES getResponse(CTX ctx);

    private void error(@Nullable HttpResponse response, Span span) {
        if (response == null) {
            return;
        }
        int httpStatus = response.statusCode();
        Throwable error = response.error();
        if (error != null) {
            return;
        }
        if (httpStatus == 0) {
            return;
        }
        if (httpStatus < 100 || httpStatus > 399) {
            // TODO: Move to a common place
            span.tag("error", String.valueOf(httpStatus));
        }
    }

}
