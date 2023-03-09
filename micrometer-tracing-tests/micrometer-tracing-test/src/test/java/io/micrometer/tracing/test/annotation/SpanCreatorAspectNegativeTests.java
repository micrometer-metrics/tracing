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

import io.micrometer.tracing.annotation.*;
import io.micrometer.tracing.test.simple.SimpleTracer;
import io.micrometer.tracing.test.simple.TracerAssert;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class SpanCreatorAspectNegativeTests {

    SimpleTracer tracer = new SimpleTracer();

    NotAnnotatedTestBeanInterface testBean = new NotAnnotatedTestBean();

    TestBeanInterface annotatedTestBean = new TestBean();

    @Test
    void shouldNotCallAdviceForNotAnnotatedBean() {
        AspectJProxyFactory pf = new AspectJProxyFactory(this.testBean);
        pf.addAspect(new SpanAspect(new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(), tracer, aClass -> null, aClass -> null)));

        ((NotAnnotatedTestBeanInterface) pf.getProxy()).testMethod();

        BDDAssertions.then(tracer.getSpans()).isEmpty();
    }

    @Test
    void shouldCallAdviceForAnnotatedBean() throws Throwable {
        AspectJProxyFactory pf = new AspectJProxyFactory(this.annotatedTestBean);
        pf.addAspect(new SpanAspect(new ImperativeMethodInvocationProcessor(new DefaultNewSpanParser(), tracer, aClass -> null, aClass -> null)));

        // Sleuth allowed checking for parent methods / interfaces
        ((TestBeanInterface) pf.getProxy()).testMethod2();

        TracerAssert.assertThat(tracer).onlySpan().isStarted().isEnded()
                        .hasNameEqualTo("test-method2");
    }

    protected interface NotAnnotatedTestBeanInterface {

        void testMethod();

    }

    protected interface TestBeanInterface {

        @NewSpan
        void testMethod();

        void testMethod2();

        void testMethod3();

        @NewSpan(name = "testMethod4")
        void testMethod4();

        @NewSpan(name = "testMethod5")
        void testMethod5(@SpanTag("testTag") String test);

        void testMethod6(String test);

        void testMethod7();

    }

    protected static class NotAnnotatedTestBean implements NotAnnotatedTestBeanInterface {

        @Override
        public void testMethod() {
        }

    }

    protected static class TestBean implements TestBeanInterface {

        @Override
        public void testMethod() {
        }

        @NewSpan
        @Override
        public void testMethod2() {
        }

        @NewSpan(name = "testMethod3")
        @Override
        public void testMethod3() {
        }

        @Override
        public void testMethod4() {
        }

        @Override
        public void testMethod5(String test) {
        }

        @NewSpan(name = "testMethod6")
        @Override
        public void testMethod6(@SpanTag("testTag6") String test) {

        }

        @Override
        public void testMethod7() {
        }

    }

}
