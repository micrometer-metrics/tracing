/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Handler that processes {@link BaggageKeyValue} annotations on method parameters,
 * opening baggage scopes that must be closed after method execution.
 * <p>
 * When a {@link BaggageKeyValue#resolver()} is specified, this handler first attempts to
 * obtain the resolver from the configured provider (e.g. Spring bean lookup). If the
 * provider returns {@code null} or is not configured, the handler falls back to
 * instantiating the resolver class directly via its no-arg constructor.
 *
 * @author Stuart Kemp
 * @since 1.4.0
 */
public class BaggageKeyValueAnnotationHandler {

    private static final InternalLogger logger = InternalLoggerFactory
        .getInstance(BaggageKeyValueAnnotationHandler.class);

    private final Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider;

    private final Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider;

    /**
     * Creates a new handler with no external resolver providers. Resolvers specified via
     * {@link BaggageKeyValue#resolver()} will be instantiated directly via their no-arg
     * constructor.
     */
    public BaggageKeyValueAnnotationHandler() {
        this(aClass -> new NoOpValueResolver(), aClass -> {
            throw new UnsupportedOperationException("No ValueExpressionResolver configured");
        });
    }

    public BaggageKeyValueAnnotationHandler(
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        this.resolverProvider = resolverProvider;
        this.expressionResolverProvider = expressionResolverProvider;
    }

    /**
     * Opens baggage scopes for all parameters annotated with {@link BaggageKeyValue}.
     * @param tracer the tracer to use for creating baggage
     * @param pjp the proceeding join point
     * @return list of opened baggage scopes that must be closed
     */
    public List<io.micrometer.tracing.BaggageInScope> openBaggageScopes(Tracer tracer, ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] args = pjp.getArgs();
        if (parameterAnnotations.length == 0) {
            return Collections.emptyList();
        }
        List<io.micrometer.tracing.BaggageInScope> scopes = new ArrayList<>();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof BaggageKeyValue) {
                    BaggageKeyValue baggageAnnotation = (BaggageKeyValue) annotation;
                    String name = resolveBaggageName(baggageAnnotation, method.getParameters()[i].getName());
                    String value = resolveBaggageValue(baggageAnnotation, args[i]);
                    scopes.add(tracer.createBaggageInScope(name, value));
                }
            }
        }
        return scopes;
    }

    private String resolveBaggageName(BaggageKeyValue annotation, String parameterName) {
        if (StringUtils.isNotBlank(annotation.value())) {
            return annotation.value();
        }
        if (StringUtils.isNotBlank(annotation.key())) {
            return annotation.key();
        }
        return parameterName;
    }

    String resolveBaggageValue(BaggageKeyValue annotation, @Nullable Object argument) {
        String value = null;
        if (annotation.resolver() != NoOpValueResolver.class) {
            ValueResolver resolver = getOrCreateResolver(annotation.resolver());
            value = resolver.resolve(argument);
        }
        else if (StringUtils.isNotBlank(annotation.expression())) {
            ValueExpressionResolver expressionResolver = getOrCreateExpressionResolver();
            if (expressionResolver != null) {
                value = expressionResolver.resolve(annotation.expression(), argument);
            }
            else {
                throw new IllegalStateException("Expression [" + annotation.expression()
                        + "] specified on @BaggageKeyValue but no ValueExpressionResolver is available. "
                        + "If using Spring Boot, ensure tracing auto-configuration is active. "
                        + "Otherwise, provide a ValueExpressionResolver via the BaggageKeyValueAnnotationHandler "
                        + "constructor or use a ValueResolver instead.");
            }
        }
        else if (argument != null) {
            value = argument.toString();
        }
        return value == null ? "" : value;
    }

    private ValueResolver getOrCreateResolver(Class<? extends ValueResolver> resolverClass) {
        ValueResolver resolver = resolverProvider.apply(resolverClass);
        if (resolver != null && !(resolver instanceof NoOpValueResolver)) {
            return resolver;
        }
        try {
            return resolverClass.getDeclaredConstructor().newInstance();
        }
        catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not instantiate ValueResolver [" + resolverClass.getName()
                            + "]. Ensure it has a public no-arg constructor or provide it via a resolver provider.",
                    ex);
        }
    }

    @Nullable private ValueExpressionResolver getOrCreateExpressionResolver() {
        try {
            return expressionResolverProvider.apply(ValueExpressionResolver.class);
        }
        catch (Exception ex) {
            logger.debug("Could not obtain ValueExpressionResolver from provider", ex);
            return null;
        }
    }

}
