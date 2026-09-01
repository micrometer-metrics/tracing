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
package io.micrometer.tracing.integration.aspectj;

import io.micrometer.tracing.annotation.DefaultNewSpanParser;
import io.micrometer.tracing.annotation.ImperativeMethodInvocationProcessor;
import io.micrometer.tracing.annotation.MethodInvocationProcessor;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanAspect;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AspectJStandaloneSpanAspectIntegrationTests {

    SimpleTracer tracer = new SimpleTracer();

    Deque<SimpleSpan> spans;

    @BeforeEach
    void setup() {
        spans = tracer.getSpans();
    }

    @Test
    void shouldLoadMethodInvocationFromAopAllianceJar() {
        CodeSource codeSource = MethodInvocation.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        URL location = codeSource.getLocation();
        assertThat(location).isNotNull();
        assertThat(location.toString()).contains("aopalliance").doesNotContain("spring-aop");
    }

    @Test
    void shouldNotHaveSpringAopOnClasspath() {
        assertThatThrownBy(() -> Class.forName("org.springframework.aop.framework.ProxyFactory"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void shouldProcessMethodInvocationWithoutSpring() throws Throwable {
        MethodInvocationProcessor processor = new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(),
                tracer);
        SpanAspect spanAspect = new SpanAspect(processor);

        Method method = SampleService.class.getMethod("execute");
        NewSpan newSpan = method.getAnnotation(NewSpan.class);

        SampleService target = new SampleService();
        TestInvocation invocation = new TestInvocation(target, method, new Object[0]);

        Object result = processor.process(invocation, newSpan, null);

        assertThat(result).isEqualTo("executed");
        assertThat(this.spans).hasSize(1);
        SimpleSpan span = this.spans.peek();
        assertThat(span.getName()).isEqualTo("sample-span");
        assertThat(span.getEndTimestamp().toEpochMilli()).isNotZero();
        assertThat(this.tracer.currentSpan()).isNull();
    }

    static class SampleService {

        @NewSpan(name = "sample-span")
        public String execute() {
            return "executed";
        }

    }

    static class TestInvocation implements MethodInvocation {

        private final Object target;

        private final Method method;

        private final Object[] args;

        TestInvocation(Object target, Method method, Object[] args) {
            this.target = target;
            this.method = method;
            this.args = args;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return args;
        }

        @Override
        public Object proceed() throws Throwable {
            return method.invoke(target, args);
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return method;
        }

    }

}
