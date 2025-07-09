/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.tracing.annotation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.SpanCustomizer;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * This class is able to find all methods annotated with the Micrometer Tracing
 * annotations. All methods mean that if you have both an interface and an implementation
 * annotated with Micrometer Tracing annotations then this class is capable of finding
 * both of them and merging into one set of tracing information.
 * <p>
 * This information is then used to add proper tags to the span from the method arguments
 * that are annotated with {@link SpanTag}.
 *
 * Code ported from Spring Cloud Sleuth.
 *
 * @author Christian Schwerdtfeger
 * @since 1.1.0
 */
public class SpanTagAnnotationHandler extends AnnotationHandler<SpanCustomizer> {

    public SpanTagAnnotationHandler(Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        super((keyValue, spanCustomizer) -> spanCustomizer.tag(keyValue.getKey(), keyValue.getValue()),
                resolverProvider, expressionResolverProvider, SpanTag.class, (annotation, o) -> {
                    if (!(annotation instanceof SpanTag)) {
                        return null;
                    }
                    SpanTag spanTag = (SpanTag) annotation;
                    return KeyValue.of(resolveTagKey(spanTag),
                            resolveTagValue(spanTag, o, resolverProvider, expressionResolverProvider));
                });
    }

    private static String resolveTagKey(SpanTag annotation) {
        return StringUtils.isNotBlank(annotation.value()) ? annotation.value() : annotation.key();
    }

    static String resolveTagValue(SpanTag annotation, @Nullable Object argument,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        String value = null;
        if (annotation.resolver() != NoOpValueResolver.class) {
            ValueResolver ValueResolver = resolverProvider.apply(annotation.resolver());
            value = ValueResolver.resolve(argument);
        }
        else if (StringUtils.isNotBlank(annotation.expression())) {
            value = expressionResolverProvider.apply(ValueExpressionResolver.class)
                .resolve(annotation.expression(), argument);
        }
        else if (argument != null) {
            value = argument.toString();
        }
        return value == null ? "" : value;
    }

}
