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

import io.micrometer.tracing.http.HttpResponse;
import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.http.HttpResponseParser;

/**
 * Brave implementation of a {@link HttpResponseParser}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
public class BraveHttpResponseParser implements HttpResponseParser {

    final brave.http.HttpResponseParser delegate;

    /**
     * Creates a new instance of {@link BraveHttpResponseParser}.
     * @param delegate Brave version of {@link HttpResponseParser}
     */
    public BraveHttpResponseParser(brave.http.HttpResponseParser delegate) {
        this.delegate = delegate;
        DeprecatedClassLogger.logWarning(getClass());
    }

    /**
     * Converts from Tracing to Brave.
     * @param parser API parser
     * @return Brave parser
     */
    public static brave.http.HttpResponseParser toBrave(HttpResponseParser parser) {
        DeprecatedClassLogger.logWarning(BraveHttpRequestParser.class);
        if (parser instanceof BraveHttpResponseParser) {
            return ((BraveHttpResponseParser) parser).delegate;
        }
        return (response, context, span) -> parser.parse(BraveHttpResponse.fromBrave(response),
                BraveTraceContext.fromBrave(context), BraveSpanCustomizer.fromBrave(span));
    }

    @Override
    public void parse(HttpResponse response, TraceContext context, SpanCustomizer span) {
        DeprecatedClassLogger.logWarning(getClass());
        this.delegate.parse(BraveHttpResponse.toBrave(response), BraveTraceContext.toBrave(context),
                BraveSpanCustomizer.toBrave(span));
    }

}
