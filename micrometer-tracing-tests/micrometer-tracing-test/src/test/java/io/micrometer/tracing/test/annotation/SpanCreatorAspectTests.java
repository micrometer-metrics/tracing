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

package io.micrometer.tracing.test.annotation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.*;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Deque;
import java.util.Map;
import java.util.stream.Collectors;

class SpanCreatorAspectTests {

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
    void shouldCreateSpanWhenAnnotationOnInterfaceMethod() {
        testBean().testMethod();

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("test-method");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWhenAnnotationOnClassMethod() {
        testBean().testMethod2();

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("test-method2");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithCustomNameWhenAnnotationOnClassMethod() {
        testBean().testMethod3();

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method3");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithCustomNameWhenAnnotationOnInterfaceMethod() {
        testBean().testMethod4();

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method4");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithTagWhenAnnotationOnInterfaceMethod() {
        // tag::execution[]
        testBean().testMethod5("test");
        // end::execution[]

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method5");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("testTag", "test");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithTagWhenAnnotationOnClassMethod() {
        testBean().testMethod6("test");

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method6");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("testTag6", "test");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithLogWhenAnnotationOnInterfaceMethod() {
        testBean().testMethod8("test");

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method8");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldCreateSpanWithLogWhenAnnotationOnClassMethod() {
        testBean().testMethod9("test");

        BDDAssertions.then(this.spans).hasSize(1);
        // Different in Sleuth
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("custom-name-on-test-method9");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("annotated.class", "TestBean")
                .containsEntry("annotated.method", "testMethod9");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
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

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("foo");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("customTestTag10", "test");
        BDDAssertions.then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
                .contains("customTest.before", "customTest.after");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldStartAndCloseSpanOnContinueSpanIfSpanNotSet() {
        testBean().testMethod10("test");

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("test-method10");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("customTestTag10", "test");
        BDDAssertions.then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
                .contains("customTest.before", "customTest.after");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
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

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("foo");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("customTestTag10", "test");
        BDDAssertions.then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
                .contains("customTest.before", "customTest.after");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldContinueSpanWithLogWhenAnnotationOnClassMethod() {
        Span span = this.tracer.nextSpan().name("foo");

        try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
            testBean().testMethod11("test");
        }
        finally {
            span.end();
        }

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("foo");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("annotated.class", "TestBean")
                .containsEntry("annotated.method", "testMethod11").containsEntry("customTestTag11", "test");
        BDDAssertions.then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
                .contains("customTest.before", "customTest.after");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldAddErrorTagWhenExceptionOccurredInNewSpan() {
        try {
            testBean().testMethod12("test");
        }
        catch (RuntimeException ignored) {
        }

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("test-method12");
        BDDAssertions.then(this.spans.peek().getTags()).containsEntry("testTag12", "test");
        BDDAssertions.then(this.spans.peek().getError()).hasMessageContaining("test exception 12");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldAddErrorTagWhenExceptionOccurredInContinueSpan() throws InterruptedException {
        Span span = this.tracer.nextSpan().name("foo");

        try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
            testBean().testMethod13();
        }
        catch (RuntimeException ignored) {
        }
        finally {
            span.end();
        }

        BDDAssertions.then(this.spans).hasSize(1);
        BDDAssertions.then(this.spans.peek().getName()).isEqualTo("foo");
        BDDAssertions.then(this.spans.peek().getError()).hasMessageContaining("test exception 13");
        BDDAssertions.then(this.spans.peek().getEvents().stream().map(Map.Entry::getValue).collect(Collectors.toList()))
                .contains("testMethod13.before", "testMethod13.afterFailure", "testMethod13.after");
        BDDAssertions.then(this.spans.peek().getEndTimestamp().toEpochMilli()).isNotZero();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    @Test
    void shouldNotCreateSpanWhenNotAnnotated() {
        testBean().testMethod7();

        BDDAssertions.then(this.spans).isEmpty();
        BDDAssertions.then(this.tracer.currentSpan()).isNull();
    }

    protected interface TestBeanInterface {

        @NewSpan
        void testMethod();

        void testMethod2();

        @NewSpan(name = "interfaceCustomNameOnTestMethod3")
        void testMethod3();

        @NewSpan("customNameOnTestMethod4")
        void testMethod4();

        @NewSpan(name = "customNameOnTestMethod5")
        void testMethod5(@SpanTag("testTag") String param);

        void testMethod6(String test);

        void testMethod7();

        @NewSpan(name = "customNameOnTestMethod8")
        void testMethod8(String param);

        @NewSpan(name = "testMethod9")
        void testMethod9(String param);

        @ContinueSpan(log = "customTest")
        void testMethod10(@SpanTag("testTag10") String param);

        @ContinueSpan(log = "customTest")
        void testMethod10_v2(@SpanTag("testTag10") String param);

        @ContinueSpan(log = "testMethod11")
        void testMethod11(@SpanTag("testTag11") String param);

        @NewSpan
        void testMethod12(@SpanTag("testTag12") String param);

        @ContinueSpan(log = "testMethod13")
        void testMethod13();

    }

    protected static class TestBean implements TestBeanInterface {

        @NewSpan
        @Override
        public void testMethod() {
        }

        @NewSpan
        @Override
        public void testMethod2() {
        }

        @NewSpan(name = "customNameOnTestMethod3")
        @Override
        public void testMethod3() {
        }

        @NewSpan("customNameOnTestMethod4")
        @Override
        public void testMethod4() {
        }

        // In Sleuth it would be taken from the interface
        @NewSpan(name = "customNameOnTestMethod5")
        @Override
        public void testMethod5(String test) {
        }

        @NewSpan(name = "customNameOnTestMethod6")
        @Override
        public void testMethod6(@SpanTag("testTag6") String test) {

        }

        @Override
        public void testMethod7() {
        }

        // In Sleuth it would be taken from the interface
        @NewSpan(name = "customNameOnTestMethod8")
        @Override
        public void testMethod8(String param) {

        }

        @NewSpan(name = "customNameOnTestMethod9")
        @Override
        public void testMethod9(String param) {

        }

        @ContinueSpan(log = "customTest")
        @Override
        public void testMethod10(@SpanTag("customTestTag10") String param) {

        }

        @ContinueSpan(log = "customTest")
        @Override
        public void testMethod10_v2(@SpanTag(key = "customTestTag10") String param) {

        }

        @ContinueSpan(log = "customTest")
        @Override
        public void testMethod11(@SpanTag("customTestTag11") String param) {

        }

        @NewSpan
        @Override
        public void testMethod12(@SpanTag("testTag12") String param) {
            throw new RuntimeException("test exception 12");
        }

        @Override
        @ContinueSpan(log = "testMethod13")
        public void testMethod13() {
            throw new RuntimeException("test exception 13");
        }

    }

}
