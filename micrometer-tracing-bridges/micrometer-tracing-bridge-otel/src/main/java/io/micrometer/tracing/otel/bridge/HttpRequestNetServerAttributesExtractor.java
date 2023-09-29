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
import io.micrometer.tracing.http.HttpServerRequest;
import io.micrometer.tracing.http.HttpServerResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;

import java.net.URI;

/**
 * Extracts OpenTelemetry network semantic attributes value for server http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
class HttpRequestNetServerAttributesExtractor
        implements NetServerAttributesGetter<HttpServerRequest, HttpServerResponse> {

    @Nullable
    @Override
    public String getTransport(HttpServerRequest httpRequest) {
        String url = httpRequest.url();
        if (url == null) {
            return null;
        }
        URI uri = URI.create(url);
        return uri.getScheme();
    }

    @Nullable
    @Override
    public String getServerAddress(HttpServerRequest httpServerRequest) {
        String url = httpServerRequest.url();
        if (url == null) {
            return null;
        }
        URI uri = URI.create(url);
        return uri.getHost();
    }

    @Nullable
    @Override
    public Integer getServerPort(HttpServerRequest httpServerRequest) {
        return httpServerRequest.remotePort();
    }

    @Nullable
    @Override
    public String getClientSocketAddress(HttpServerRequest httpServerRequest,
            @Nullable HttpServerResponse httpServerResponse) {
        return httpServerRequest.remoteIp();
    }

    @Nullable
    @Override
    public Integer getClientSocketPort(HttpServerRequest httpServerRequest,
            @Nullable HttpServerResponse httpServerResponse) {
        return httpServerRequest.remotePort();
    }

}
