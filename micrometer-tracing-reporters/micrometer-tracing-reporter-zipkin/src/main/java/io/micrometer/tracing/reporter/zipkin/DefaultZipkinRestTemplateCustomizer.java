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

package org.springframework.boot.autoconfigure.observability.tracing.reporter.zipkin.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/**
 * Default {@link ZipkinRestTemplateCustomizer} that provides the GZip compression if
 * enabled.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class DefaultZipkinRestTemplateCustomizer implements ZipkinRestTemplateCustomizer {

	private final Supplier<Boolean> compressionPropertySupplier;

	/**
	 * @param compressionPropertySupplier supplier for property related to compression
	 */
	public DefaultZipkinRestTemplateCustomizer(Supplier<Boolean> compressionPropertySupplier) {
		this.compressionPropertySupplier = compressionPropertySupplier;
	}

	@Override
	public RestTemplate customizeTemplate(RestTemplate restTemplate) {
		if (this.compressionPropertySupplier.get()) {
			restTemplate.getInterceptors().add(0, new GZipInterceptor());
		}
		return restTemplate;
	}

	private static class GZipInterceptor implements ClientHttpRequestInterceptor {

		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
				throws IOException {
			request.getHeaders().add("Content-Encoding", "gzip");
			ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
			try (GZIPOutputStream compressor = new GZIPOutputStream(gzipped)) {
				compressor.write(body);
			}
			return execution.execute(request, gzipped.toByteArray());
		}

	}

}
