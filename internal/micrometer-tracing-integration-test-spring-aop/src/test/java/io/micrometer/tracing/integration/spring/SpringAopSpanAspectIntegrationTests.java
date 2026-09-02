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
package io.micrometer.tracing.integration.spring;

import io.micrometer.tracing.annotation.ContinueSpan;
import io.micrometer.tracing.annotation.DefaultNewSpanParser;
import io.micrometer.tracing.annotation.ImperativeMethodInvocationProcessor;
import io.micrometer.tracing.annotation.MethodInvocationProcessor;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanAspect;
import io.micrometer.tracing.annotation.SpanTag;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.net.URL;
import java.security.CodeSource;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAopSpanAspectIntegrationTests {

    SimpleTracer tracer = new SimpleTracer();

    Deque<SimpleSpan> spans;

    @BeforeEach
    void setup() {
        spans = tracer.getSpans();
    }

    @Test
    void shouldLoadMethodInvocationFromSpringAop() {
        CodeSource codeSource = MethodInvocation.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        URL location = codeSource.getLocation();
        assertThat(location).isNotNull();
        assertThat(location.toString()).contains("spring-aop").doesNotContain("aopalliance-1.0");
    }

    @Test
    void shouldCreateSpanWithSpringAopProxy() {
        TestService service = createProxy(new TestServiceImpl());
        service.annotatedMethod("test-value");

        assertThat(this.spans).hasSize(1);
        SimpleSpan span = this.spans.peek();
        assertThat(span.getName()).isEqualTo("custom-span");
        assertThat(span.getTags()).containsEntry("tagKey", "test-value");
        assertThat(span.getEndTimestamp().toEpochMilli()).isNotZero();
        assertThat(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldContinueSpanWithSpringAopProxy() {
        TestService service = createProxy(new TestServiceImpl());
        service.annotatedMethod("test-value");
        service.continueMethod("continued-value");

        assertThat(this.spans).hasSize(2);
    }

    private TestService createProxy(TestService target) {
        MethodInvocationProcessor processor = new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(),
                tracer, aClass -> null, aClass -> null);
        AspectJProxyFactory pf = new AspectJProxyFactory(target);
        pf.addAspect(new SpanAspect(processor));
        return pf.getProxy();
    }

    interface TestService {

        void annotatedMethod(@SpanTag("tagKey") String param);

        void continueMethod(@SpanTag("continueTag") String param);

    }

    static class TestServiceImpl implements TestService {

        @NewSpan(name = "custom-span")
        @Override
        public void annotatedMethod(String param) {
        }

        @ContinueSpan(log = "continueLog")
        @Override
        public void continueMethod(String param) {
        }

    }

}
