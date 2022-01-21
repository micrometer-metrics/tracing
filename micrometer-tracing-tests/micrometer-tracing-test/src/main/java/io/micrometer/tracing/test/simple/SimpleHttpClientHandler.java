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

package io.micrometer.tracing.test.simple;

import io.micrometer.api.instrument.transport.http.HttpClientRequest;
import io.micrometer.api.instrument.transport.http.HttpClientResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.http.HttpClientHandler;

/**
 * A test implementation of a http client handler.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleHttpClientHandler implements HttpClientHandler {

    private final SimpleTracer simpleTracer;

    private boolean receiveHandled;

    /**
     * @param simpleTracer simple tracer
     */
    public SimpleHttpClientHandler(SimpleTracer simpleTracer) {
        this.simpleTracer = simpleTracer;
    }

    @Override
    public Span handleSend(HttpClientRequest request) {
        return this.simpleTracer.nextSpan().start();
    }

    @Override
    public Span handleSend(HttpClientRequest request, TraceContext parent) {
        return this.simpleTracer.nextSpan().start();
    }

    @Override
    public void handleReceive(HttpClientResponse response, Span span) {
        span.end();
        this.receiveHandled = true;
    }

    /**
     * @return Was the handle receive method called?
     */
    public boolean isReceiveHandled() {
        return this.receiveHandled;
    }
}
