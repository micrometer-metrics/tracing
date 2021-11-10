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

import org.springframework.web.client.RestTemplate;

/**
 * Implementations customize the {@link RestTemplate} used to report spans to Zipkin. For
 * example, they can add an additional header needed by their environment.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface ZipkinRestTemplateCustomizer {

	/**
	 * Customizes the {@link RestTemplate} instance. Might return a new one if necessary.
	 * @param restTemplate default object to customize
	 * @return customized {@link RestTemplate} or a new object
	 */
	default RestTemplate customizeTemplate(RestTemplate restTemplate) {
		return restTemplate;
	}

}
