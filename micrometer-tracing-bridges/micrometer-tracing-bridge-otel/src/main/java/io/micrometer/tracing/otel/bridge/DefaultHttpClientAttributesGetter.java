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
package io.micrometer.tracing.otel.bridge;

import java.util.Collections;
import java.util.List;

import io.micrometer.tracing.http.HttpClientRequest;
import io.micrometer.tracing.http.HttpClientResponse;
import io.micrometer.common.lang.Nullable;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;

/**
 * Extracts OpenTelemetry http semantic attributes value for client http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 */
public class DefaultHttpClientAttributesGetter
        implements HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

    @Nullable
    @Override
    public String url(HttpClientRequest httpClientRequest) {
        return httpClientRequest.url();
    }

    @Nullable
    @Override
    public String flavor(HttpClientRequest httpClientRequest, @Nullable HttpClientResponse httpClientResponse) {
        return null;
    }

    @Override
    public String method(HttpClientRequest httpClientRequest) {
        return httpClientRequest.method();
    }

    @Override
    public List<String> requestHeader(HttpClientRequest httpClientRequest, String name) {
        if (httpClientRequest == null) {
            return Collections.emptyList();
        }
        String value = httpClientRequest.header(name);
        return value == null ? Collections.emptyList() : Collections.singletonList(value);
    }

    @Nullable
    @Override
    public Integer statusCode(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse,
            Throwable error) {
        if (httpClientResponse == null) {
            return null;
        }
        return httpClientResponse.statusCode();
    }

    @Override
    public List<String> responseHeader(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse,
            String name) {
        if (httpClientResponse == null) {
            return Collections.emptyList();
        }
        try {
            String value = httpClientResponse.header(name);
            return value == null ? Collections.emptyList() : Collections.singletonList(value);
        }
        catch (Exception e) {
            return Collections.emptyList();
        }
    }

}
