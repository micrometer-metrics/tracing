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

package org.springframework.boot.autoconfigure.observability.tracing.brave.bridge;

import java.util.Collection;
import java.util.Collections;

import io.micrometer.core.instrument.transport.Kind;
import io.micrometer.core.instrument.transport.http.HttpRequest;
import io.micrometer.core.instrument.transport.http.HttpResponse;

/**
 * Brave implementation of a {@link HttpResponse}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class BraveHttpResponse implements HttpResponse {

	final brave.http.HttpResponse delegate;

	BraveHttpResponse(brave.http.HttpResponse delegate) {
		this.delegate = delegate;
	}

	static brave.http.HttpResponse toBrave(HttpResponse httpResponse) {
		return ((BraveHttpResponse) httpResponse).delegate;
	}

	static HttpResponse fromBrave(brave.http.HttpResponse httpResponse) {
		return new BraveHttpResponse(httpResponse);
	}

	@Override
	public String method() {
		return this.delegate.method();
	}

	@Override
	public String route() {
		return this.delegate.route();
	}

	@Override
	public int statusCode() {
		return this.delegate.statusCode();
	}

	@Override
	public Object unwrap() {
		return this.delegate.unwrap();
	}

	@Override
	public Collection<String> headerNames() {
		// this is unused by Brave
		return Collections.emptyList();
	}

	@Override
	public Kind kind() {
		return Kind.valueOf(this.delegate.spanKind().name());
	}

	@Override
	public HttpRequest request() {
		brave.http.HttpRequest request = this.delegate.request();
		if (request == null) {
			return null;
		}
		return new BraveHttpRequest(request);
	}

	@Override
	public Throwable error() {
		return this.delegate.error();
	}

}
