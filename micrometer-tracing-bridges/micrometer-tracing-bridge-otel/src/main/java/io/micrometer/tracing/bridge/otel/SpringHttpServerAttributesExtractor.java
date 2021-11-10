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

package org.springframework.cloud.sleuth.otel.bridge;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;

import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.lang.Nullable;

/**
 * Extracts OpenTelemetry http semantic attributes value for server http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 */
public class SpringHttpServerAttributesExtractor
		extends HttpServerAttributesExtractor<HttpServerRequest, HttpServerResponse> {

	@Nullable
	@Override
	protected String flavor(HttpServerRequest httpServerRequest) {
		return null;
	}

	@Nullable
	@Override
	protected String target(HttpServerRequest httpServerRequest) {
		URI uri = toUri(httpServerRequest);
		if (uri == null) {
			return null;
		}
		return uri.getPath() + queryPart(uri);
	}

	private URI toUri(HttpServerRequest request) {
		String url = request.url();
		return url == null ? null : URI.create(url);
	}

	private String queryPart(URI uri) {
		String query = uri.getQuery();
		return query != null ? "?" + query : "";
	}

	@Nullable
	@Override
	protected String route(HttpServerRequest httpServerRequest) {
		return httpServerRequest.route();
	}

	@Nullable
	@Override
	protected String scheme(HttpServerRequest httpServerRequest) {
		String url = httpServerRequest.url();
		if (url == null) {
			return null;
		}
		if (url.startsWith("https:")) {
			return "https";
		}
		if (url.startsWith("http:")) {
			return "http";
		}
		return null;
	}

	@Nullable
	@Override
	protected String serverName(HttpServerRequest httpServerRequest, @Nullable HttpServerResponse httpServerResponse) {
		return null;
	}

	@Nullable
	@Override
	protected String method(HttpServerRequest httpServerRequest) {
		return httpServerRequest.method();
	}

	@Override
	protected List<String> requestHeader(HttpServerRequest httpServerRequest, String name) {
		String value = httpServerRequest.header(name);
		return value == null ? Collections.emptyList() : Collections.singletonList(value);
	}

	@Nullable
	@Override
	protected Long requestContentLength(HttpServerRequest httpServerRequest,
			@Nullable HttpServerResponse httpServerResponse) {
		return null;
	}

	@Nullable
	@Override
	protected Long requestContentLengthUncompressed(HttpServerRequest httpServerRequest,
			@Nullable HttpServerResponse httpServerResponse) {
		return null;
	}

	@Nullable
	@Override
	protected Integer statusCode(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse) {
		return httpServerResponse.statusCode();
	}

	@Nullable
	@Override
	protected Long responseContentLength(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse) {
		return null;
	}

	@Nullable
	@Override
	protected Long responseContentLengthUncompressed(HttpServerRequest httpServerRequest,
			HttpServerResponse httpServerResponse) {
		return null;
	}

	@Override
	protected List<String> responseHeader(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse,
			String name) {
		String value = httpServerResponse.header(name);
		return value == null ? Collections.emptyList() : Collections.singletonList(value);
	}

}
