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

import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.http.HttpRequest;
import io.micrometer.tracing.http.HttpResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

class PathAttributeExtractor implements AttributesExtractor<HttpRequest, HttpResponse> {

    private static final AttributeKey<String> HTTP_PATH = AttributeKey.stringKey("http.path");

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, HttpRequest httpRequest) {
        String path = httpRequest.path();
        if (StringUtils.isNotEmpty(path)) {
            setAttribute(attributes, SemanticAttributes.HTTP_ROUTE, path);
            setAttribute(attributes, HTTP_PATH, path);
        }
    }

    private <T> void setAttribute(AttributesBuilder attributes, AttributeKey<T> key, T value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, HttpRequest httpRequest, HttpResponse httpResponse,
            Throwable error) {

    }

}
