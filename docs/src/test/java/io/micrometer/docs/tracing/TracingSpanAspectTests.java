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
package io.micrometer.docs.tracing;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.*;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Deque;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

class TracingSpanAspectTests {

    SimpleTracer tracer = new SimpleTracer();

    TestBeanInterface testBean = new TestBean();

    Deque<SimpleSpan> spans;

    @BeforeEach
    void setup() {
        spans = tracer.getSpans();
    }

    private TestBeanInterface testBean() {
        AspectJProxyFactory pf = new AspectJProxyFactory(this.testBean);
        pf.addAspect(new SpanAspect(new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(), tracer,
                aClass -> null, aClass -> null)));
        return pf.getProxy();
    }

    @Test
    void shouldCreateSpanWhenAnnotationOnClassMethod() {
        testBean().testMethod2();

        then(this.spans).hasSize(1);
        then(this.spans.peek().getName()).isEqualTo("test-method2");
        then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
        testBean().testMethod3();

        then(this.spans).hasSize(1);
        then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method3");
        then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldContinueSpanWithLogWhenAnnotationOnInterfaceMethod() {
        Span span = this.tracer.nextSpan().name("foo");

        try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
            testBean().testMethod10("test");
        }
        finally {
            span.end();
        }

        then(this.spans).hasSize(1);
        then(this.spans.peek().getName()).isEqualTo("foo");
        then(this.spans.peek().getTags()).containsEntry("customTestTag10", "test");
        then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
            .contains("customTest.before", "customTest.after");
        then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
        testBean().testMethod10("test");

        then(this.spans).hasSize(1);
        then(this.spans.peek().getName()).isEqualTo("test-method10");
        then(this.spans.peek().getTags()).containsEntry("customTestTag10", "test");
        then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
            .contains("customTest.before", "customTest.after");
        then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldContinueSpanWhenKeyIsUsedOnSpanTagWhenAnnotationOnInterfaceMethod() {
        Span span = this.tracer.nextSpan().name("foo");

        try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
            testBean().testMethod10_v2("test");
        }
        finally {
            span.end();
        }

        then(this.spans).hasSize(1);
        then(this.spans.peek().getName()).isEqualTo("foo");
        then(this.spans.peek().getTags()).containsEntry("testTag10", "test");
        then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
            .contains("customTest.before", "customTest.after");
        then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void testForDocs() {

        // tag::usage_example[]

        // Creates a new span with
        testBean().testMethod2();
        then(createdSpanViaAspect()).isEqualTo("test-method2");

        // Uses the name from the annotation
        testBean().testMethod3();
        then(createdSpanViaAspect()).isEqualTo("custom-name-on-test-method3");

        // Continues the previous span
        Span span = this.tracer.nextSpan().name("foo");
        try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {

            // Adds tags and events to an existing span
            testBean().testMethod10("tagValue");
            SimpleSpan continuedSpan = modifiedSpanViaAspect();
            then(continuedSpan.getName()).isEqualTo("foo");
            then(continuedSpan.getTags()).containsEntry("customTestTag10", "tagValue");
            then(continuedSpan.getEvents()).extracting("value").contains("customTest.before", "customTest.after");
        }
        span.end();

        // Continues the previous span
        span = this.tracer.nextSpan().name("foo");
        try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {

            // Adds tags and events to an existing span (reusing setup from the parent
            // interface)
            testBean().testMethod10_v2("tagValue");
            SimpleSpan continuedSpan = modifiedSpanViaAspect();
            then(continuedSpan.getName()).isEqualTo("foo");
            then(continuedSpan.getTags()).containsEntry("testTag10", "tagValue");
            then(continuedSpan.getEvents()).extracting("value").contains("customTest.before", "customTest.after");
        }
        span.end();

        // end::usage_example[]
    }

    private String createdSpanViaAspect() {
        String spanName = this.spans.peek().getName();
        this.spans.clear();
        return spanName;
    }

    private SimpleSpan modifiedSpanViaAspect() {
        SimpleSpan span = this.spans.peek();
        this.spans.clear();
        return span;
    }

    // tag::example[]
    // In Sleuth @NewSpan and @ContinueSpan annotations would be taken into
    // consideration. In Micrometer Tracing due to limitations of @Aspect
    // we can't do that. The @SpanTag annotation will work well though.
    protected interface TestBeanInterface {

        void testMethod2();

        void testMethod3();

        void testMethod10(@SpanTag("testTag10") String param);

        void testMethod10_v2(@SpanTag("testTag10") String param);

    }

    // Example of an implementation class
    protected static class TestBean implements TestBeanInterface {

        @NewSpan
        @Override
        public void testMethod2() {
        }

        @NewSpan(name = "customNameOnTestMethod3")
        @Override
        public void testMethod3() {
        }

        @ContinueSpan(log = "customTest")
        @Override
        public void testMethod10(@SpanTag("customTestTag10") String param) {

        }

        @ContinueSpan(log = "customTest")
        @Override
        public void testMethod10_v2(String param) {

        }

    }
    // end::example[]

    // tag::spring_config[]
    @Configuration
    public class SpanAspectConfiguration {

        @Bean
        NewSpanParser newSpanParser() {
            return new DefaultNewSpanParser();
        }

        // You can provide your own resolvers - here we go with a noop example.
        @Bean
        ValueResolver valueResolver() {
            return new NoOpValueResolver();
        }

        // Example of a SpEL resolver
        @Bean
        ValueExpressionResolver valueExpressionResolver() {
            return new SpelTagValueExpressionResolver();
        }

        @Bean
        MethodInvocationProcessor methodInvocationProcessor(NewSpanParser newSpanParser, Tracer tracer,
                BeanFactory beanFactory) {
            return new ImperativeMethodInvocationProcessor(newSpanParser, tracer, beanFactory::getBean,
                    beanFactory::getBean);
        }

        @Bean
        SpanAspect spanAspect(MethodInvocationProcessor methodInvocationProcessor) {
            return new SpanAspect(methodInvocationProcessor);
        }

    }

    // Example of using SpEL to resolve expressions in @SpanTag
    static class SpelTagValueExpressionResolver implements ValueExpressionResolver {

        private static final Log log = LogFactory.getLog(SpelTagValueExpressionResolver.class);

        @Override
        public String resolve(String expression, Object parameter) {
            try {
                SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                ExpressionParser expressionParser = new SpelExpressionParser();
                Expression expressionToEvaluate = expressionParser.parseExpression(expression);
                return expressionToEvaluate.getValue(context, parameter, String.class);
            }
            catch (Exception ex) {
                log.error("Exception occurred while tying to evaluate the SpEL expression [" + expression + "]", ex);
            }
            return parameter.toString();
        }

    }
    // end::spring_config[]

}
