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

import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class SpanTagAnnotationHandlerTests {

    ValueResolver ValueResolver = parameter -> "Value from myCustomValueResolver";

    ValueExpressionResolver ValueExpressionResolver = new SpelValueExpressionResolver();

    SpanTagAnnotationHandler handler;

    @BeforeEach
    void setup() {
        this.handler = new SpanTagAnnotationHandler(aClass -> ValueResolver, aClass -> ValueExpressionResolver);
    }

    @Test
    void shouldUseCustomValueResolver() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForValueResolver", String.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        if (annotation instanceof SpanTag) {
            String resolvedValue = this.handler.resolveTagValue((SpanTag) annotation, "test", aClass -> ValueResolver,
                    aClass -> ValueExpressionResolver);
            assertThat(resolvedValue).isEqualTo("Value from myCustomValueResolver");
        }
        else {
            fail("Annotation was not SpanTag");
        }
    }

    @Test
    void shouldUseTagValueExpression() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueExpression", String.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        if (annotation instanceof SpanTag) {
            String resolvedValue = this.handler.resolveTagValue((SpanTag) annotation, "test", aClass -> ValueResolver,
                    aClass -> ValueExpressionResolver);

            assertThat(resolvedValue).isEqualTo("hello characters");
        }
        else {
            fail("Annotation was not SpanTag");
        }
    }

    @Test
    void shouldReturnArgumentToString() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForArgumentToString", Long.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        if (annotation instanceof SpanTag) {
            String resolvedValue = this.handler.resolveTagValue((SpanTag) annotation, 15, aClass -> ValueResolver,
                    aClass -> ValueExpressionResolver);
            assertThat(resolvedValue).isEqualTo("15");
        }
        else {
            fail("Annotation was not SpanTag");
        }
    }

    protected class AnnotationMockClass {

        @NewSpan
        public void getAnnotationForValueResolver(@SpanTag(key = "test", resolver = ValueResolver.class) String test) {
        }

        @NewSpan
        public void getAnnotationForTagValueExpression(
                @SpanTag(key = "test", expression = "'hello' + ' characters'") String test) {
        }

        @NewSpan
        public void getAnnotationForArgumentToString(@SpanTag("test") Long param) {
        }

    }

    static class SpelValueExpressionResolver implements ValueExpressionResolver {

        private static final Log log = LogFactory.getLog(SpelValueExpressionResolver.class);

        @Override
        public String resolve(String expression, @Nullable Object parameter) {
            try {
                SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                ExpressionParser expressionParser = new SpelExpressionParser();
                Expression expressionToEvaluate = expressionParser.parseExpression(expression);
                String value = expressionToEvaluate.getValue(context, parameter, String.class);
                return value == null ? "" : value;
            }
            catch (Exception ex) {
                log.error("Exception occurred while tying to evaluate the SPEL expression [" + expression + "]", ex);
            }
            return parameter == null ? "" : parameter.toString();
        }

    }

}
