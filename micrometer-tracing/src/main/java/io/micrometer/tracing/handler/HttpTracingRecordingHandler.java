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


import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.tracing.context.HttpHandlerContext;
import io.micrometer.core.instrument.transport.http.HttpRequest;
import io.micrometer.core.instrument.transport.http.HttpResponse;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.lang.Nullable;

@SuppressWarnings({"rawtypes", "unchecked"})
abstract class HttpTracingRecordingHandler<CTX extends HttpHandlerContext, REQ extends HttpRequest, RES extends HttpResponse>
        implements TracingRecordingHandler<CTX> {

    private final Tracer tracer;

    private final CurrentTraceContext currentTraceContext;

    private final Function<REQ, Span> startFunction;

    private final BiConsumer<RES, Span> stopConsumer;

    // private final TracingTagFilter tracingTagFilter = new TracingTagFilter();

    HttpTracingRecordingHandler(Tracer tracer, Function<REQ, Span> startFunction,
            BiConsumer<RES, Span> stopConsumer) {
        this.tracer = tracer;
        this.currentTraceContext = tracer.currentTraceContext();
        this.startFunction = startFunction;
        this.stopConsumer = stopConsumer;
    }

    @Override
    public void onError(Timer.Sample sample, CTX ctx, Throwable throwable) {

    }

    @Override
    public void onStart(Timer.Sample sample, CTX event) {
        Span parentSpan = getTracingContext(event).getSpan();
        CurrentTraceContext.Scope scope = null;
        if (parentSpan != null) {
            scope = this.currentTraceContext.maybeScope(parentSpan.context());
        }
        REQ request = getRequest(event);
        Span span = this.startFunction.apply(request);
        scope = this.currentTraceContext.newScope(span.context());
        getTracingContext(event).setSpanAndScope(span, scope);
    }

    @Override
    public boolean supportsContext(Timer.HandlerContext context) {
        return context instanceof HttpHandlerContext;
    }

    @Override
    public Tracer getTracer() {
        return this.tracer;
    }

    abstract REQ getRequest(CTX event);

    @Override
    public void onStop(Timer.Sample sample, CTX ctx, Timer timer,
            Duration duration) {
        Span span = getTracingContext(ctx).getSpan();
        // this.tracingTagFilter.tagSpan(span, sample.getTags());
        span.name(getSpanName(ctx));
        tagSpan(ctx, span);
        RES response = getResponse(ctx);
        error(response, span);
        this.stopConsumer.accept(response, span);
    }

    abstract String getSpanName(CTX event);

    abstract RES getResponse(CTX event);

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
