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

import org.jspecify.annotations.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aopalliance.intercept.MethodInvocation;

abstract class AbstractMethodInvocationProcessor implements MethodInvocationProcessor {

    private static final InternalLogger logger = InternalLoggerFactory
        .getInstance(AbstractMethodInvocationProcessor.class);

    final NewSpanParser newSpanParser;

    final Tracer tracer;

    final CurrentTraceContext currentTraceContext;

    @Nullable SpanTagAnnotationHandler spanTagAnnotationHandler;

    AbstractMethodInvocationProcessor(NewSpanParser newSpanParser, Tracer tracer,
            CurrentTraceContext currentTraceContext, @Nullable SpanTagAnnotationHandler spanTagAnnotationHandler) {
        this.newSpanParser = newSpanParser;
        this.tracer = tracer;
        this.currentTraceContext = currentTraceContext;
        this.spanTagAnnotationHandler = spanTagAnnotationHandler;
    }

    void before(MethodInvocation invocation, Span span, String log, boolean hasLog) {
        if (hasLog) {
            logEvent(span, String.format(AnnotationSpanDocumentation.Events.BEFORE.getValue(), log));
        }
        if (invocation instanceof SpanAspectMethodInvocation) {
            SpanAspectMethodInvocation spanInvocation = (SpanAspectMethodInvocation) invocation;
            if (spanTagAnnotationHandler != null && tracer.currentSpanCustomizer() != null) {
                spanTagAnnotationHandler.addAnnotatedParameters(tracer.currentSpanCustomizer(),
                        spanInvocation.getPjp());
            }
        }
        addTags(invocation, span);
    }

    void after(Span span, boolean isNewSpan, String log, boolean hasLog) {
        if (hasLog) {
            logEvent(span, String.format(AnnotationSpanDocumentation.Events.AFTER.getValue(), log));
        }
        if (isNewSpan) {
            span.end();
        }
    }

    void onFailure(Span span, String log, boolean hasLog, Throwable e) {
        if (logger.isDebugEnabled()) {
            logger.debug("Exception occurred while trying to continue the pointcut", e);
        }
        if (hasLog) {
            logEvent(span, String.format(AnnotationSpanDocumentation.Events.AFTER_FAILURE.getValue(), log));
        }
        span.error(e);
    }

    void addTags(MethodInvocation invocation, Span span) {
        if (invocation instanceof SpanAspectMethodInvocation) {
            SpanAspectMethodInvocation methodInvocation = (SpanAspectMethodInvocation) invocation;
            span.tag(AnnotationSpanDocumentation.Tags.CLASS.asString(),
                    methodInvocation.getPjp().getTarget().getClass().getSimpleName());
        }
        span.tag(AnnotationSpanDocumentation.Tags.METHOD.asString(), invocation.getMethod().getName());
    }

    void logEvent(@Nullable Span span, String name) {
        if (span == null) {
            logger.warn("You were trying to continue a span which was null. Please "
                    + "remember that if two proxied methods are calling each other from "
                    + "the same class then the aspect will not be properly resolved");
            return;

        }
        span.event(name);
    }

    String log(@Nullable ContinueSpan continueSpan) {
        if (continueSpan != null) {
            return continueSpan.log();
        }
        return "";
    }

    /**
     * Setting this enables support for {@link SpanTag}.
     * @param spanTagAnnotationHandler span tag annotation handler
     */
    public void setSpanTagAnnotationHandler(SpanTagAnnotationHandler spanTagAnnotationHandler) {
        this.spanTagAnnotationHandler = spanTagAnnotationHandler;
    }

}
