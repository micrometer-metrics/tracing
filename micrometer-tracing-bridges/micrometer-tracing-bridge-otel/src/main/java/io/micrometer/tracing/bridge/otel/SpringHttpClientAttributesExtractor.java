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

import java.util.Collections;
import java.util.List;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;

import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.lang.Nullable;

/**
 * Extracts OpenTelemetry http semantic attributes value for client http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 */
public class SpringHttpClientAttributesExtractor
		extends HttpClientAttributesExtractor<HttpClientRequest, HttpClientResponse> {

	@Nullable
	@Override
	protected String url(HttpClientRequest httpClientRequest) {
		return httpClientRequest.url();
	}

	@Nullable
	@Override
	protected String flavor(HttpClientRequest httpClientRequest, @Nullable HttpClientResponse httpClientResponse) {
		return null;
	}

	@Override
	protected String method(HttpClientRequest httpClientRequest) {
		return httpClientRequest.method();
	}

	@Override
	protected List<String> requestHeader(HttpClientRequest httpClientRequest, String name) {
		String value = httpClientRequest.header(name);
		return value == null ? Collections.emptyList() : Collections.singletonList(value);
	}

	@Nullable
	@Override
	protected Long requestContentLength(HttpClientRequest httpClientRequest,
			@Nullable HttpClientResponse httpClientResponse) {
		return null;
	}

	@Nullable
	@Override
	protected Long requestContentLengthUncompressed(HttpClientRequest httpClientRequest,
			@Nullable HttpClientResponse httpClientResponse) {
		return null;
	}

	@Override
	protected Integer statusCode(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse) {
		return httpClientResponse.statusCode();
	}

	@Nullable
	@Override
	protected Long responseContentLength(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse) {
		return null;
	}

	@Nullable
	@Override
	protected Long responseContentLengthUncompressed(HttpClientRequest httpClientRequest,
			HttpClientResponse httpClientResponse) {
		return null;
	}

	@Override
	protected List<String> responseHeader(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse,
			String name) {
		String value = httpClientResponse.header(name);
		return value == null ? Collections.emptyList() : Collections.singletonList(value);
	}

}
