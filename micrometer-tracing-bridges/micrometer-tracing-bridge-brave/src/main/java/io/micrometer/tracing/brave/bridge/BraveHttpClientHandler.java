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

package io.micrometer.tracing.brave.bridge;

import io.micrometer.core.instrument.transport.http.HttpClientRequest;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.http.HttpClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brave implementation of a {@link HttpClientHandler}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BraveHttpClientHandler implements HttpClientHandler {

    private static final Logger log = LoggerFactory.getLogger(BraveHttpClientHandler.class);

    final brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> delegate;

    /**
     * @param delegate Brave delegate
     */
    public BraveHttpClientHandler(
            brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Span handleSend(HttpClientRequest request) {
        return io.micrometer.tracing.brave.bridge.BraveSpan.fromBrave(this.delegate.handleSend(io.micrometer.tracing.brave.bridge.BraveHttpClientRequest.toBrave(request)));
    }

    @Override
    public Span handleSend(HttpClientRequest request, TraceContext parent) {
        brave.Span span = this.delegate.handleSendWithParent(io.micrometer.tracing.brave.bridge.BraveHttpClientRequest.toBrave(request),
                io.micrometer.tracing.brave.bridge.BraveTraceContext.toBrave(parent));
        if (!span.isNoop()) {
            span.remoteIpAndPort(request.remoteIp(), request.remotePort());
        }
        return io.micrometer.tracing.brave.bridge.BraveSpan.fromBrave(span);
    }

    @Override
    public void handleReceive(HttpClientResponse response, Span span) {
        if (response == null) {
            log.debug("Response is null, will not handle receiving of span [{}]", span);
            return;
        }
        this.delegate.handleReceive(io.micrometer.tracing.brave.bridge.BraveHttpClientResponse.toBrave(response), io.micrometer.tracing.brave.bridge.BraveSpan.toBrave(span));
    }

}
