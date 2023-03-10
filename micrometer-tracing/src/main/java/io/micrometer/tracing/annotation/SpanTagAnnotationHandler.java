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

package io.micrometer.tracing.annotation;

import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.SpanCustomizer;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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
class SpanTagAnnotationHandler {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SpanTagAnnotationHandler.class);

    private final SpanCustomizer spanCustomizer;

    private final Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider;

    private final Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider;

    SpanTagAnnotationHandler(SpanCustomizer spanCustomizer,
            Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider,
            Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider) {
        this.spanCustomizer = spanCustomizer;
        this.resolverProvider = resolverProvider;
        this.expressionResolverProvider = expressionResolverProvider;
    }

    void addAnnotatedParameters(MethodInvocation pjp) {
        try {
            Method method = pjp.getMethod();
            List<AnnotatedParameter> annotatedParameters = AnnotationUtils.findAnnotatedParameters(method,
                    pjp.getArguments());
            getAnnotationsFromInterfaces(pjp, method, annotatedParameters);
            addAnnotatedArguments(annotatedParameters);
        }
        catch (SecurityException ex) {
            log.error("Exception occurred while trying to add annotated parameters", ex);
        }
    }

    private void getAnnotationsFromInterfaces(MethodInvocation pjp, Method mostSpecificMethod,
            List<AnnotatedParameter> annotatedParameters) {
        Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();
        if (implementedInterfaces.length > 0) {
            for (Class<?> implementedInterface : implementedInterfaces) {
                for (Method methodFromInterface : implementedInterface.getMethods()) {
                    if (methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
                        List<AnnotatedParameter> annotatedParametersForActualMethod = AnnotationUtils
                                .findAnnotatedParameters(methodFromInterface, pjp.getArguments());
                        mergeAnnotatedParameters(annotatedParameters, annotatedParametersForActualMethod);
                    }
                }
            }
        }
    }

    private boolean methodsAreTheSame(Method mostSpecificMethod, Method method1) {
        return method1.getName().equals(mostSpecificMethod.getName())
                && Arrays.equals(method1.getParameterTypes(), mostSpecificMethod.getParameterTypes());
    }

    private void mergeAnnotatedParameters(List<AnnotatedParameter> annotatedParametersIndices,
            List<AnnotatedParameter> annotatedParametersIndicesForActualMethod) {
        for (AnnotatedParameter container : annotatedParametersIndicesForActualMethod) {
            final int index = container.parameterIndex;
            boolean parameterContained = false;
            for (AnnotatedParameter parameterContainer : annotatedParametersIndices) {
                if (parameterContainer.parameterIndex == index) {
                    parameterContained = true;
                    break;
                }
            }
            if (!parameterContained) {
                annotatedParametersIndices.add(container);
            }
        }
    }

    private void addAnnotatedArguments(List<AnnotatedParameter> toBeAdded) {
        for (AnnotatedParameter container : toBeAdded) {
            String tagValue = resolveTagValue(container.annotation, container.argument);
            String tagKey = resolveTagKey(container);
            spanCustomizer.tag(tagKey, tagValue);
        }
    }

    private String resolveTagKey(AnnotatedParameter container) {
        return StringUtils.isNotBlank(container.annotation.value()) ? container.annotation.value()
                : container.annotation.key();
    }

    String resolveTagValue(SpanTag annotation, Object argument) {
        String value = null;
        if (annotation.resolver() != NoOpTagValueResolver.class) {
            TagValueResolver tagValueResolver = resolverProvider.apply(annotation.resolver());
            value = tagValueResolver.resolve(argument);
        }
        else if (StringUtils.isNotBlank(annotation.expression())) {
            value = this.expressionResolverProvider.apply(TagValueExpressionResolver.class)
                    .resolve(annotation.expression(), argument);
        }
        else if (argument != null) {
            value = argument.toString();
        }
        return value == null ? "" : value;
    }

}
