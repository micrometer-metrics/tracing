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

import io.micrometer.common.annotation.TagValueExpressionResolver;
import io.micrometer.common.annotation.TagValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aopalliance.intercept.MethodInvocation;

import java.util.function.Function;

/**
 * Method Invocation processor for imperative code.
 *
 * Code ported from Spring Cloud Sleuth.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
public class ImperativeMethodInvocationProcessor extends AbstractMethodInvocationProcessor {

    /**
     * Creates a new instance of {@link ImperativeMethodInvocationProcessor}.
     * @param newSpanParser new span parser
     * @param tracer tracer
     * @param resolverProvider converts a class into an instance of resolver provider
     * @param expressionResolverProvider converts a class into an instance of expression
     * resolver provider
     */
    public ImperativeMethodInvocationProcessor(NewSpanParser newSpanParser, Tracer tracer,
            Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider,
            Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider) {
        super(newSpanParser, tracer, tracer.currentTraceContext(),
                new SpanTagAnnotationHandler(resolverProvider, expressionResolverProvider));
    }

    @Override
    public Object process(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable {
        return proceedUnderSynchronousSpan(invocation, newSpan, continueSpan);
    }

    private Object proceedUnderSynchronousSpan(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan)
            throws Throwable {
        Span span = tracer.currentSpan();
        // in case of @ContinueSpan and no span in tracer we start new span and should
        // close it on completion
        boolean startNewSpan = newSpan != null || span == null;
        if (startNewSpan) {
            span = tracer.nextSpan();
            newSpanParser.parse(invocation, newSpan, span);
            span.start();
        }
        String log = log(continueSpan);
        boolean hasLog = StringUtils.isNotBlank(log);
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            before(invocation, span, log, hasLog);
            return invocation.proceed();
        }
        catch (Exception ex) {
            onFailure(span, log, hasLog, ex);
            throw ex;
        }
        finally {
            after(span, startNewSpan, log, hasLog);
        }
    }

}
