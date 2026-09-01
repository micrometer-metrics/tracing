/*
 * Copyright 2026 the original author or authors.
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
package io.micrometer.tracing.integration.noaspectj;

import io.micrometer.tracing.annotation.ContinueSpan;
import io.micrometer.tracing.annotation.DefaultNewSpanParser;
import io.micrometer.tracing.annotation.ImperativeMethodInvocationProcessor;
import io.micrometer.tracing.annotation.MethodInvocationProcessor;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanAspect;
import io.micrometer.tracing.annotation.SpanTag;
import io.micrometer.tracing.annotation.SpanTagAnnotationHandler;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.lang.reflect.Method;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoAspectJSpanAspectIntegrationTests {

    SimpleTracer tracer = new SimpleTracer();

    Deque<SimpleSpan> spans;

    @BeforeEach
    void setup() {
        spans = tracer.getSpans();
    }

    @Test
    void shouldFailToReflectAspectMethodsOnSpanAspectWithoutAspectJOnClasspath() {
        // Introspecting methods on SpanAspect requires AspectJ's ProceedingJoinPoint
        assertThatThrownBy(SpanAspect.class::getDeclaredMethods).isInstanceOf(NoClassDefFoundError.class)
            .hasMessageContaining("org/aspectj/lang/ProceedingJoinPoint");
    }

    @Test
    void shouldFailToUseAspectJProxyFactoryWithoutAspectJWeaver() {
        MethodInvocationProcessor processor = new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(),
                tracer);

        assertThatThrownBy(() -> {
            Class<?> clazz = Class.forName("org.springframework.aop.aspectj.annotation.AspectJProxyFactory");
            Object pf = clazz.getConstructor(Object.class).newInstance(new SampleServiceImpl());
            clazz.getMethod("addAspect", Object.class).invoke(pf, new SpanAspect(processor));
            clazz.getMethod("getProxy").invoke(pf);
        }).isInstanceOf(Throwable.class).hasCauseInstanceOf(NoClassDefFoundError.class);
    }

    @Test
    void shouldDemonstrateThatSpanTagAndClassTagsAreIgnoredWithoutAspectJ() {
        // When using pure Spring AOP without AspectJ, a custom MethodInterceptor can pass
        // a
        // Spring MethodInvocation to MethodInvocationProcessor.process().
        // However, because MethodInvocationProcessor expects a SpanAspectMethodInvocation
        // (which wraps AspectJ's ProceedingJoinPoint), @SpanTag annotations and class
        // tags are silently ignored.
        MethodInvocationProcessor processor = new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(),
                tracer, new SpanTagAnnotationHandler(aClass -> null, aClass -> null));

        ProxyFactory pf = new ProxyFactory(new SampleServiceImpl());
        pf.addAdvice((MethodInterceptor) invocation -> {
            Method method = invocation.getMethod();
            NewSpan newSpan = method.getAnnotation(NewSpan.class);
            ContinueSpan continueSpan = method.getAnnotation(ContinueSpan.class);
            return processor.process(invocation, newSpan, continueSpan);
        });

        SampleService proxy = (SampleService) pf.getProxy();
        proxy.execute("tagged-param-value");

        assertThat(this.spans).hasSize(1);
        SimpleSpan span = this.spans.peek();
        assertThat(span.getName()).isEqualTo("sample-span");
        // Method tag is set because it only requires MethodInvocation.getMethod()
        assertThat(span.getTags()).containsEntry("annotated.method", "execute");
        // CLASS tag is NOT set because it requires AspectJ ProceedingJoinPoint
        assertThat(span.getTags()).doesNotContainKey("annotated.class");
        // @SpanTag is NOT processed because addAnnotatedParameters requires AspectJ
        // ProceedingJoinPoint
        assertThat(span.getTags()).doesNotContainKey("myTag");
    }

    interface SampleService {

        @NewSpan(name = "sample-span")
        void execute(@SpanTag("myTag") String param);

    }

    static class SampleServiceImpl implements SampleService {

        @Override
        public void execute(String param) {
        }

    }

}
