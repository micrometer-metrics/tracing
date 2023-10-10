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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Extracts OpenTelemetry http semantic attributes value for server http spans.
 *
 * @author Nikita Salnikov-Tarnovski
 * @since 1.0.0
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
public class DefaultHttpServerAttributesExtractor
        implements HttpServerAttributesGetter<HttpServerRequest, HttpServerResponse> {

    /**
     * @deprecated This method was removed from OpenTelemetry. It should not be used since
     * always returned null in Micrometer Tracing.
     */
    @Nullable
    @Deprecated
    public String getFlavor(HttpServerRequest httpServerRequest) {
        return null;
    }

    @Nullable
    @Override
    public String getUrlPath(HttpServerRequest httpServerRequest) {
        URI uri = toUri(httpServerRequest);
        if (uri == null) {
            return null;
        }
        return uri.getPath();
    }

    @Nullable
    @Override
    public String getUrlQuery(HttpServerRequest httpServerRequest) {
        URI uri = toUri(httpServerRequest);
        if (uri == null) {
            return null;
        }
        return queryPart(uri);
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getUrlPath(HttpServerRequest)} and {@link #getUrlQuery(HttpServerRequest)}
     * instead.
     */
    @Nullable
    @Deprecated
    public String getTarget(HttpServerRequest httpServerRequest) {
        return this.getUrlPath(httpServerRequest) + this.getUrlQuery(httpServerRequest);
    }

    private URI toUri(HttpServerRequest request) {
        String url = request.url();
        return url == null ? null : URI.create(url);
    }

    private String queryPart(URI uri) {
        String query = uri.getQuery();
        return query != null ? "?" + query : "";
    }

    @Nullable
    @Override
    public String getHttpRoute(HttpServerRequest httpServerRequest) {
        return httpServerRequest.route();
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpRoute(HttpServerRequest)} instead.
     */
    @Nullable
    @Deprecated
    public String getRoute(HttpServerRequest httpServerRequest) {
        return this.getHttpRoute(httpServerRequest);
    }

    @Nullable
    @Override
    public String getUrlScheme(HttpServerRequest httpServerRequest) {
        String url = httpServerRequest.url();
        if (url == null) {
            return null;
        }
        if (url.startsWith("https:")) {
            return "https";
        }
        if (url.startsWith("http:")) {
            return "http";
        }
        return null;
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getUrlScheme(HttpServerRequest)} instead.
     */
    @Nullable
    @Deprecated
    public String getScheme(HttpServerRequest httpServerRequest) {
        return this.getUrlScheme(httpServerRequest);
    }

    @Nullable
    @Override
    public String getHttpRequestMethod(HttpServerRequest httpServerRequest) {
        return httpServerRequest.method();
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpRequestMethod(HttpServerRequest)} instead.
     */
    @Nullable
    @Deprecated
    public String getMethod(HttpServerRequest httpServerRequest) {
        return this.getHttpRequestMethod(httpServerRequest);
    }

    @Override
    public List<String> getHttpRequestHeader(HttpServerRequest httpServerRequest, String name) {
        String value = httpServerRequest.header(name);
        return value == null ? Collections.emptyList() : Collections.singletonList(value);
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpRequestHeader(HttpServerRequest, String)} instead.
     */
    @Deprecated
    public List<String> getRequestHeader(HttpServerRequest httpServerRequest, String name) {
        return this.getHttpRequestHeader(httpServerRequest, name);
    }

    @Nullable
    @Override
    public Integer getHttpResponseStatusCode(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse,
            @Nullable Throwable error) {
        return httpServerResponse.statusCode();
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpResponseStatusCode(HttpServerRequest, HttpServerResponse, Throwable)}
     * instead.
     */
    @Nullable
    @Deprecated
    public Integer getStatusCode(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse,
            Throwable error) {
        return this.getHttpResponseStatusCode(httpServerRequest, httpServerResponse, error);
    }

    @Override
    public List<String> getHttpResponseHeader(HttpServerRequest httpServerRequest,
            HttpServerResponse httpServerResponse, String name) {
        String value = httpServerResponse.header(name);
        return value == null ? Collections.emptyList() : Collections.singletonList(value);
    }

    /**
     * @deprecated This method was removed from OpenTelemetry. Please use
     * {@link #getHttpResponseHeader(HttpServerRequest, HttpServerResponse, String)}
     * instead.
     */
    @Deprecated
    public List<String> getResponseHeader(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse,
            String name) {
        return this.getHttpResponseHeader(httpServerRequest, httpServerResponse, name);
    }

}
