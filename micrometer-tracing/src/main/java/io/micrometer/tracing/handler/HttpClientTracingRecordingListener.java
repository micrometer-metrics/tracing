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

package org.springframework.boot.autoconfigure.observability.tracing.listener;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.tracing.Tracer;
import io.micrometer.core.instrument.tracing.context.IntervalHttpClientEvent;
import io.micrometer.core.instrument.tracing.http.HttpClientHandler;
import io.micrometer.core.instrument.transport.http.HttpClientRequest;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;

/**
 * TracingRecordingListener that uses the Tracing API to record events for HTTP client
 * side.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class HttpClientTracingRecordingListener extends
		HttpTracingRecordingListener<IntervalHttpClientEvent, HttpClientRequest, HttpClientResponse>
		implements TracingRecordingListener<IntervalHttpClientEvent> {

	/**
	 * Creates a new instance of {@link HttpClientTracingRecordingListener}.
	 *
	 * @param tracer tracer
	 * @param handler http client handler
	 */
	public HttpClientTracingRecordingListener(Tracer tracer, HttpClientHandler handler) {
		super(tracer, handler::handleSend, handler::handleReceive);
	}

	@Override
	public boolean supportsContext(Timer.Context context) {
		return context != null && context.getClass().isAssignableFrom(IntervalHttpClientEvent.class);
	}

	@Override
	HttpClientRequest getRequest(IntervalHttpClientEvent event) {
		IntervalHttpClientEvent clientEvent = event;
		return clientEvent.getRequest();
	}

	@Override
	String getSpanName(IntervalHttpClientEvent event) {
		return getRequest(event).method();
	}

	@Override
	HttpClientResponse getResponse(IntervalHttpClientEvent event) {
		IntervalHttpClientEvent clientEvent = event;
		return clientEvent.getResponse();
	}


}
