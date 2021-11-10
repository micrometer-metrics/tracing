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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.core.log.LogAccessor;

/**
 * {@link ZipkinUrlExtractor} with caching mechanism.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class CachingZipkinUrlExtractor implements ZipkinUrlExtractor {

	private static final LogAccessor log = new LogAccessor(CachingZipkinUrlExtractor.class);

	final AtomicInteger zipkinPort = new AtomicInteger();

	private final ZipkinLoadBalancer zipkinLoadBalancer;

	/**
	 * Sets the {@link ZipkinUrlExtractor} with a {@link ZipkinLoadBalancer}.
	 * @param zipkinLoadBalancer Zipkin load balancer
	 */
	public CachingZipkinUrlExtractor(ZipkinLoadBalancer zipkinLoadBalancer) {
		this.zipkinLoadBalancer = zipkinLoadBalancer;
	}

	@Override
	public URI zipkinUrl(Supplier<String> baseUrl) {
		int cachedZipkinPort = zipkinPort(baseUrl);
		if (cachedZipkinPort == -1) {
			log.debug(() -> "The port in Zipkin's URL [" + baseUrl
					+ "] wasn't provided - that means that load balancing might take place");
			return this.zipkinLoadBalancer.instance();
		}
		log.debug(() -> "The port in Zipkin's URL [" + baseUrl
				+ "] is provided - that means that load balancing will not take place");
		return noOpZipkinLoadBalancer(baseUrl).instance();
	}

	StaticInstanceZipkinLoadBalancer noOpZipkinLoadBalancer(Supplier<String> baseUrl) {
		return new StaticInstanceZipkinLoadBalancer(baseUrl);
	}

	private int zipkinPort(Supplier<String> baseUrl) {
		int cachedZipkinPort = this.zipkinPort.get();
		if (cachedZipkinPort != 0) {
			return cachedZipkinPort;
		}
		return calculatePort(baseUrl);
	}

	int calculatePort(Supplier<String> baseUrl) {
		URI uri = createUri(baseUrl.get());
		int zipkinPort = uri.getPort();
		this.zipkinPort.set(zipkinPort);
		return zipkinPort;
	}

	URI createUri(String baseUrl) {
		return URI.create(baseUrl);
	}

}
