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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Extracts OpenTelemetry http semantic attributes value for client http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
public class DefaultHttpClientAttributesGetter
        implements HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

    public DefaultHttpClientAttributesGetter() {
        DeprecatedClassLogger.logWarning(getClass());
    }

    @Nullable
    @Override
    public String getUrlFull(HttpClientRequest httpClientRequest) {
        DeprecatedClassLogger.logWarning(getClass());
        return httpClientRequest.url();
    }

    @Nullable
    @Override
    public String getServerAddress(HttpClientRequest httpClientRequest) {
        DeprecatedClassLogger.logWarning(getClass());
        try {
            URI uri = URI.create(httpClientRequest.url());
            return uri.getHost();
        }
        catch (Exception ex) {
            return null;
        }
    }

    @Nullable
    @Override
    public Integer getServerPort(HttpClientRequest httpClientRequest) {
        DeprecatedClassLogger.logWarning(getClass());
        try {
            URI uri = URI.create(httpClientRequest.url());
            return uri.getPort();
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getUrlFull(HttpClientRequest)} instead.
     */
    @Nullable
    @Deprecated
    public String getUrl(HttpClientRequest httpClientRequest) {
        DeprecatedClassLogger.logWarning(getClass());
        return this.getUrlFull(httpClientRequest);
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. It should not be used since
     * always returned null in Micrometer Tracing.
     */
    @Nullable
    @Deprecated
    public String getFlavor(HttpClientRequest httpClientRequest, @Nullable HttpClientResponse httpClientResponse) {
        DeprecatedClassLogger.logWarning(getClass());
        return null;
    }

    @Nullable
    @Override
    public String getHttpRequestMethod(HttpClientRequest httpClientRequest) {
        DeprecatedClassLogger.logWarning(getClass());
        return httpClientRequest.method();
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpRequestMethod(HttpClientRequest)} instead.
     */
    @Nullable
    @Deprecated
    public String getMethod(HttpClientRequest httpClientRequest) {
        DeprecatedClassLogger.logWarning(getClass());
        return this.getHttpRequestMethod(httpClientRequest);
    }

    @Override
    public List<String> getHttpRequestHeader(HttpClientRequest httpClientRequest, String name) {
        DeprecatedClassLogger.logWarning(getClass());
        if (httpClientRequest == null) {
            return Collections.emptyList();
        }
        String value = httpClientRequest.header(name);
        return value == null ? Collections.emptyList() : Collections.singletonList(value);
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpRequestHeader(HttpClientRequest, String)} instead.
     */
    @Deprecated
    public List<String> getRequestHeader(HttpClientRequest httpClientRequest, String name) {
        DeprecatedClassLogger.logWarning(getClass());
        return this.getHttpRequestHeader(httpClientRequest, name);
    }

    @Nullable
    @Override
    public Integer getHttpResponseStatusCode(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse,
            @Nullable Throwable error) {
        DeprecatedClassLogger.logWarning(getClass());
        if (httpClientResponse == null) {
            return null;
        }
        return httpClientResponse.statusCode();
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpResponseStatusCode(HttpClientRequest, HttpClientResponse, Throwable)}
     * instead.
     */
    @Nullable
    @Deprecated
    public Integer getStatusCode(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse,
            Throwable error) {
        DeprecatedClassLogger.logWarning(getClass());
        return this.getHttpResponseStatusCode(httpClientRequest, httpClientResponse, error);
    }

    @Override
    public List<String> getHttpResponseHeader(HttpClientRequest httpClientRequest,
            HttpClientResponse httpClientResponse, String name) {
        DeprecatedClassLogger.logWarning(getClass());
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

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpResponseHeader(HttpClientRequest, HttpClientResponse, String)}
     * instead.
     */
    @Deprecated
    public List<String> getResponseHeader(HttpClientRequest httpClientRequest, HttpClientResponse httpClientResponse,
            String name) {
        DeprecatedClassLogger.logWarning(getClass());
        return this.getHttpResponseHeader(httpClientRequest, httpClientResponse, name);
    }

}
