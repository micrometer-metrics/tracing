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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;

import org.springframework.core.log.LogAccessor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Resolves at runtime where the Zipkin server is. If there's no discovery client then
 * {@link URI} from the properties is taken. Otherwise service discovery is pinged for
 * current Zipkin address.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ZipkinRestTemplateWrapper extends RestTemplate {

	private static final LogAccessor log = new LogAccessor(ZipkinRestTemplateWrapper.class);

	private static final int DEFAULT_TIMEOUT = 500;

	private final Supplier<String> zipkinBaseUrlSupplier;

	private final ZipkinUrlExtractor extractor;

	/**
	 * @param zipkinBaseUrlSupplier supplier for Zipkin base URL
	 * @param extractor extractor of Zipkin URL
	 */
	public ZipkinRestTemplateWrapper(Supplier<String> zipkinBaseUrlSupplier, ZipkinUrlExtractor extractor) {
		this.zipkinBaseUrlSupplier = zipkinBaseUrlSupplier;
		this.extractor = extractor;
		setRequestFactory(clientHttpRequestFactory());
	}

	private ClientHttpRequestFactory clientHttpRequestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setReadTimeout(DEFAULT_TIMEOUT);
		factory.setConnectTimeout(DEFAULT_TIMEOUT);
		return factory;
	}

	@Override
	protected <T> T doExecute(URI originalUrl, HttpMethod method, RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException {
		URI uri = this.extractor.zipkinUrl(this.zipkinBaseUrlSupplier);
		URI newUri = resolvedZipkinUri(originalUrl, uri);
		return super.doExecute(newUri, method, requestCallback, responseExtractor);
	}

	private URI resolvedZipkinUri(URI originalUrl, URI resolvedZipkinUri) {
		try {
			return new URI(resolvedZipkinUri.getScheme(), resolvedZipkinUri.getUserInfo(), resolvedZipkinUri.getHost(),
					resolvedZipkinUri.getPort(), originalUrl.getPath(), originalUrl.getQuery(),
					originalUrl.getFragment());
		}
		catch (URISyntaxException e) {
			log.debug(() -> "Failed to create the new URI from original [" + originalUrl + "] and new one ["
					+ resolvedZipkinUri + "]");
			return originalUrl;
		}
	}

}
