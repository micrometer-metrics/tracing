/**
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.http;

import io.micrometer.observation.transport.Kind;

/**
 * This API is taken from OpenZipkin Brave.
 * <p>
 * Abstract request type used for parsing and sampling. Represents an HTTP Client request.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
public interface HttpClientRequest extends HttpRequest {

    /**
     * Adds a new header.
     * @param name header name
     * @param value header value
     */
    void header(String name, String value);

    @Override
    default Kind kind() {
        return Kind.CLIENT;
    }

}
