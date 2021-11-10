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
import io.micrometer.core.instrument.tracing.context.IntervalHttpServerEvent;
import io.micrometer.core.instrument.tracing.http.HttpServerHandler;
import io.micrometer.core.instrument.transport.http.HttpResponse;
import io.micrometer.core.instrument.transport.http.HttpServerRequest;
import io.micrometer.core.instrument.transport.http.HttpServerResponse;

/**
 * TracingRecordingListener that uses the Tracing API to record events for HTTP server
 * side.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class HttpServerTracingRecordingListener extends
		HttpTracingRecordingListener<IntervalHttpServerEvent, HttpServerRequest, HttpServerResponse>
		implements TracingRecordingListener<IntervalHttpServerEvent> {

	/**
	 * Creates a new instance of {@link HttpServerTracingRecordingListener}.
	 *
	 * @param tracer tracer
	 * @param handler http server handler
	 */
	public HttpServerTracingRecordingListener(Tracer tracer, HttpServerHandler handler) {
		super(tracer, handler::handleReceive, handler::handleSend);
	}

	@Override
	HttpServerRequest getRequest(IntervalHttpServerEvent event) {
		IntervalHttpServerEvent serverEvent = event;
		return serverEvent.getRequest();
	}

	@Override
	String getSpanName(IntervalHttpServerEvent event) {
		IntervalHttpServerEvent serverEvent = event;
		if (serverEvent.getResponse() != null) {
			return spanNameFromRoute(serverEvent.getResponse());
		}
		return serverEvent.getRequest().method();
	}

	@Override
	public boolean supportsContext(Timer.Context context) {
		return context != null && context.getClass().isAssignableFrom(IntervalHttpServerEvent.class);
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
	HttpServerResponse getResponse(IntervalHttpServerEvent event) {
		IntervalHttpServerEvent serverEvent = event;
		return serverEvent.getResponse();
	}

}
