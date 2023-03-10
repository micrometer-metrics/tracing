/*
 * Copyright 2022 VMware, Inc.
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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.Nullable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * <p>
 * AspectJ aspect for intercepting types or methods annotated with
 * {@link NewSpan @NewSpan} or {@link ContinueSpan @ContinueSpan}<br>
 * The aspect supports programmatic customizations through constructor-injectable custom
 * logic.
 * </p>
 * <p>
 *
 * @author Marcin Grzejszczak
 * @since 1.11.0
 * @see ImperativeMethodInvocationProcessor
 */
@Aspect
@NonNullApi
public class SpanAspect {

    private final MethodInvocationProcessor methodInvocationProcessor;

    public SpanAspect(MethodInvocationProcessor methodInvocationProcessor) {
        this.methodInvocationProcessor = methodInvocationProcessor;
    }

    @Around("@annotation(io.micrometer.tracing.annotation.ContinueSpan)")
    @Nullable
    public Object continueSpanMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = getMethod(pjp);
        ContinueSpan continueSpan = method.getAnnotation(ContinueSpan.class);
        return methodInvocationProcessor.process(new SpanAspectMethodInvocation(pjp, method), null, continueSpan);
    }

    @Around("@annotation(io.micrometer.tracing.annotation.NewSpan)")
    @Nullable
    public Object newSpanMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = getMethod(pjp);
        NewSpan newSpan = method.getAnnotation(NewSpan.class);
        return methodInvocationProcessor.process(new SpanAspectMethodInvocation(pjp, method), newSpan, null);
    }

    private Method getMethod(ProceedingJoinPoint pjp) throws NoSuchMethodException {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Method targetMethod = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
        if (hasSpanAnnotation(targetMethod)) {
            return targetMethod;
        }
        return method;
    }

    private static boolean hasSpanAnnotation(Method method) {
        return method.getAnnotation(NewSpan.class) != null || method.getAnnotation(ContinueSpan.class) != null;
    }

}
