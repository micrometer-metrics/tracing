/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.otel.propagation;

/*
 * Supported propagation types.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public enum PropagationType {

	/**
	 * AWS propagation type.
	 */
	AWS,

	/**
	 * B3 propagation type.
	 */
	B3,

	/**
	 * Jaeger propagation type.
	 */
	JAEGER,

	/**
	 * Lightstep propagation type.
	 */
	OT_TRACER,

	/**
	 * W3C propagation type.
	 */
	W3C,

	/**
	 * Custom propagation type.
	 */
	CUSTOM

}
