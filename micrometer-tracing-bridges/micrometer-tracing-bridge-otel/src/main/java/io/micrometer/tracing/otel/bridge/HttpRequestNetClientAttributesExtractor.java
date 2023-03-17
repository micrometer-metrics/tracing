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

import io.micrometer.common.lang.Nullable;
import io.micrometer.tracing.http.HttpClientRequest;
import io.micrometer.tracing.http.HttpClientResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;

/**
 * Extracts OpenTelemetry network semantic attributes value for client http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 */
class HttpRequestNetClientAttributesExtractor
        implements NetClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

    @Nullable
    @Override
    public String getTransport(HttpClientRequest httpClientRequest, @Nullable HttpClientResponse httpClientResponse) {
        return null;
    }

    @Nullable
    @Override
    public String getPeerName(HttpClientRequest httpClientRequest) {
        return null;
    }

    @Nullable
    @Override
    public Integer getPeerPort(HttpClientRequest httpClientRequest) {
        return httpClientRequest == null ? null : httpClientRequest.remotePort();
    }

}
