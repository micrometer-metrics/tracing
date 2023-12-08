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
package io.micrometer.tracing.brave.bridge;

import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.http.HttpRequest;
import io.micrometer.tracing.http.HttpRequestParser;

/**
 * Brave implementation of a {@link HttpRequestParser}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
public class BraveHttpRequestParser implements HttpRequestParser {

    final brave.http.HttpRequestParser delegate;

    /**
     * Creates a new version of a {@link BraveHttpRequestParser}.
     * @param delegate Brave version of {@link HttpRequestParser}
     */
    @Deprecated
    public BraveHttpRequestParser(brave.http.HttpRequestParser delegate) {
        this.delegate = delegate;
        DeprecatedClassLogger.logWarning(getClass());
    }

    /**
     * Converts from Tracing to Brave.
     * @param parser API parser
     * @return Brave version of the parser
     */
    public static brave.http.HttpRequestParser toBrave(HttpRequestParser parser) {
        DeprecatedClassLogger.logWarning(BraveHttpRequestParser.class);
        if (parser instanceof BraveHttpRequestParser) {
            return ((BraveHttpRequestParser) parser).delegate;
        }
        return (request, context, span) -> parser.parse(BraveHttpRequest.fromBrave(request),
                BraveTraceContext.fromBrave(context), BraveSpanCustomizer.fromBrave(span));
    }

    @Override
    public void parse(HttpRequest request, TraceContext context, SpanCustomizer span) {
        DeprecatedClassLogger.logWarning(getClass());
        this.delegate.parse(BraveHttpRequest.toBrave(request), BraveTraceContext.toBrave(context),
                BraveSpanCustomizer.toBrave(span));
    }

}
