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

package io.micrometer.tracing.otel.bridge;

import io.micrometer.core.instrument.transport.http.HttpRequest;
import io.micrometer.core.instrument.transport.http.HttpResponse;
import io.micrometer.tracing.lang.Nullable;
import io.micrometer.tracing.util.StringUtils;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;


class PathAttributeExtractor implements AttributesExtractor<HttpRequest, HttpResponse> {

    private static final AttributeKey<String> HTTP_PATH = AttributeKey.stringKey("http.path");

    @Override
    public void onStart(AttributesBuilder attributes, HttpRequest httpRequest) {
        String path = httpRequest.path();
        if (StringUtils.isNotEmpty(path)) {
            // TODO some tests expect this even on client spans, but this goes against
            // Otel semantic conventions
            // should fix tests
            set(attributes, SemanticAttributes.HTTP_ROUTE, path);
            // some tests from Sleuth expect http.route attribute and some http.path
            set(attributes, HTTP_PATH, path);
        }
    }

    @Override
    public void onEnd(AttributesBuilder attributes, HttpRequest httpRequest, @Nullable HttpResponse httpResponse,
            @Nullable Throwable error) {

    }

}
