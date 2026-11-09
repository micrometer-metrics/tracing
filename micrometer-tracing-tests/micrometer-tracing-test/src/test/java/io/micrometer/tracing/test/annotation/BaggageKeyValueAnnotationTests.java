/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.tracing.test.annotation;

import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.*;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.assertj.core.api.BDDAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.atomic.AtomicReference;

class BaggageKeyValueAnnotationTests {

    SimpleTracer tracer = new SimpleTracer();

    TestBeanInterface testBean;

    TestBeanInterface testBeanWithExpression;

    @BeforeEach
    void setup() {
        // Using the no-arg BaggageKeyValueAnnotationHandler - resolver will be
        // instantiated via reflection
        BaggageKeyValueAnnotationHandler baggageHandler = new BaggageKeyValueAnnotationHandler();
        ImperativeMethodInvocationProcessor processor = new ImperativeMethodInvocationProcessor(
                new DefaultNewSpanParser(), tracer, null, baggageHandler);
        AspectJProxyFactory pf = new AspectJProxyFactory(new TestBean(tracer));
        pf.addAspect(new SpanAspect(processor));
        testBean = pf.getProxy();

        // Handler with expression resolver provider configured
        ValueExpressionResolver expressionResolver = (expression, parameter) -> {
            if ("toUpperCase".equals(expression) && parameter != null) {
                return parameter.toString().toUpperCase();
            }
            return parameter != null ? parameter.toString() : "";
        };
        BaggageKeyValueAnnotationHandler baggageHandlerWithExpression = new BaggageKeyValueAnnotationHandler(
                aClass -> null, aClass -> expressionResolver);
        ImperativeMethodInvocationProcessor processorWithExpression = new ImperativeMethodInvocationProcessor(
                new DefaultNewSpanParser(), tracer, null, baggageHandlerWithExpression);
        AspectJProxyFactory pfExpr = new AspectJProxyFactory(new TestBean(tracer));
        pfExpr.addAspect(new SpanAspect(processorWithExpression));
        testBeanWithExpression = pfExpr.getProxy();
    }

    @Test
    void shouldPutBaggageInScopeForMethodDuration() {
        testBean.methodWithBaggage("myValue");

        BDDAssertions.then(tracer.getSpans()).hasSize(1);
        // Baggage should have been available during the method and closed after
        BDDAssertions.then(tracer.getBaggage("myBaggage")).isNotNull();
    }

    @Test
    void shouldPutBaggageInScopeAndCloseAfterMethod() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBean.methodWithBaggageCapture("capturedVal", capturedValue);

        BDDAssertions.then(capturedValue.get()).isEqualTo("capturedVal");
        BDDAssertions.then(tracer.getSpans()).hasSize(1);
    }

    @Test
    void shouldPutBaggageInScopeWithDefaultName() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBean.methodWithDefaultBaggageName("defaultVal", capturedValue);

        BDDAssertions.then(capturedValue.get()).isEqualTo("defaultVal");
    }

    @Test
    void shouldPutMultipleBaggageEntriesInScope() {
        AtomicReference<String> captured1 = new AtomicReference<>();
        AtomicReference<String> captured2 = new AtomicReference<>();
        testBean.methodWithMultipleBaggage("val1", "val2", captured1, captured2);

        BDDAssertions.then(captured1.get()).isEqualTo("val1");
        BDDAssertions.then(captured2.get()).isEqualTo("val2");
    }

    @Test
    void shouldCloseBaggageScopeEvenOnException() {
        try {
            testBean.methodWithBaggageThrowsException("exVal");
        }
        catch (RuntimeException ignored) {
        }

        BDDAssertions.then(tracer.getSpans()).hasSize(1);
    }

    @Test
    void shouldPutBaggageInScopeUsingKeyAttribute() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBean.methodWithBaggageKey("keyVal", capturedValue);

        BDDAssertions.then(capturedValue.get()).isEqualTo("keyVal");
        BDDAssertions.then(tracer.getSpans()).hasSize(1);
    }

    @Test
    void shouldResolveBaggageValueUsingResolver() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBean.methodWithBaggageResolver("hello", capturedValue);

        BDDAssertions.then(capturedValue.get()).isEqualTo("HELLO");
        BDDAssertions.then(tracer.getSpans()).hasSize(1);
    }

    @Test
    void shouldResolveBaggageValueUsingExpression() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBeanWithExpression.methodWithBaggageExpression("world", capturedValue);

        BDDAssertions.then(capturedValue.get()).isEqualTo("WORLD");
        BDDAssertions.then(tracer.getSpans()).hasSize(1);
    }

    @Test
    void shouldThrowWhenExpressionUsedWithoutExpressionResolver() {
        BDDAssertions.thenThrownBy(() -> testBean.methodWithBaggageExpression("value", new AtomicReference<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no ValueExpressionResolver is available");
    }

    @Test
    void shouldPutBaggageInScopeWithContinueSpan() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBean.methodWithBaggageContinueSpan("continueVal", capturedValue);

        BDDAssertions.then(capturedValue.get()).isEqualTo("continueVal");
    }

    @Test
    void shouldHandleNullArgument() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        testBean.methodWithBaggageCapture(null, capturedValue);

        // Null argument should result in empty string baggage value
        BDDAssertions.then(capturedValue.get()).isEqualTo("");
        BDDAssertions.then(tracer.getSpans()).hasSize(1);
    }

    protected interface TestBeanInterface {

        void methodWithBaggage(@BaggageKeyValue("myBaggage") String value);

        void methodWithBaggageCapture(@BaggageKeyValue("captured") @Nullable String value,
                AtomicReference<String> capture);

        void methodWithDefaultBaggageName(@BaggageKeyValue String value, AtomicReference<String> capture);

        void methodWithMultipleBaggage(@BaggageKeyValue("baggage1") String val1,
                @BaggageKeyValue("baggage2") String val2, AtomicReference<String> capture1,
                AtomicReference<String> capture2);

        void methodWithBaggageThrowsException(@BaggageKeyValue("exBaggage") String value);

        void methodWithBaggageKey(@BaggageKeyValue(key = "keyBaggage") String value, AtomicReference<String> capture);

        void methodWithBaggageResolver(
                @BaggageKeyValue(value = "resolvedBaggage", resolver = UpperCaseResolver.class) String value,
                AtomicReference<String> capture);

        void methodWithBaggageExpression(
                @BaggageKeyValue(value = "expressionBaggage", expression = "toUpperCase") String value,
                AtomicReference<String> capture);

        void methodWithBaggageContinueSpan(@BaggageKeyValue("continueBaggage") String value,
                AtomicReference<String> capture);

    }

    protected static class TestBean implements TestBeanInterface {

        private final Tracer tracer;

        TestBean(Tracer tracer) {
            this.tracer = tracer;
        }

        @NewSpan
        @Override
        public void methodWithBaggage(String value) {
        }

        @NewSpan
        @Override
        public void methodWithBaggageCapture(@Nullable String value, AtomicReference<String> capture) {
            io.micrometer.tracing.Baggage baggage = tracer.getBaggage("captured");
            if (baggage != null) {
                capture.set(baggage.get());
            }
        }

        @NewSpan
        @Override
        public void methodWithDefaultBaggageName(String value, AtomicReference<String> capture) {
            // When no value is specified, the parameter name is used as baggage name.
            // Due to compilation, parameter names may not be preserved,
            // so we check all baggage entries.
            var allBaggage = tracer.getAllBaggage();
            if (!allBaggage.isEmpty()) {
                capture.set(allBaggage.values().iterator().next());
            }
        }

        @NewSpan
        @Override
        public void methodWithMultipleBaggage(String val1, String val2, AtomicReference<String> capture1,
                AtomicReference<String> capture2) {
            io.micrometer.tracing.Baggage b1 = tracer.getBaggage("baggage1");
            io.micrometer.tracing.Baggage b2 = tracer.getBaggage("baggage2");
            if (b1 != null) {
                capture1.set(b1.get());
            }
            if (b2 != null) {
                capture2.set(b2.get());
            }
        }

        @NewSpan
        @Override
        public void methodWithBaggageThrowsException(String value) {
            throw new RuntimeException("test exception");
        }

        @NewSpan
        @Override
        public void methodWithBaggageKey(String value, AtomicReference<String> capture) {
            io.micrometer.tracing.Baggage baggage = tracer.getBaggage("keyBaggage");
            if (baggage != null) {
                capture.set(baggage.get());
            }
        }

        @NewSpan
        @Override
        public void methodWithBaggageResolver(String value, AtomicReference<String> capture) {
            io.micrometer.tracing.Baggage baggage = tracer.getBaggage("resolvedBaggage");
            if (baggage != null) {
                capture.set(baggage.get());
            }
        }

        @NewSpan
        @Override
        public void methodWithBaggageExpression(String value, AtomicReference<String> capture) {
            io.micrometer.tracing.Baggage baggage = tracer.getBaggage("expressionBaggage");
            if (baggage != null) {
                capture.set(baggage.get());
            }
        }

        @ContinueSpan
        @Override
        public void methodWithBaggageContinueSpan(String value, AtomicReference<String> capture) {
            var allBaggage = tracer.getAllBaggage();
            if (!allBaggage.isEmpty()) {
                capture.set(allBaggage.values().iterator().next());
            }
        }

    }

    /**
     * A simple resolver that uppercases the argument value. Has a public no-arg
     * constructor so it can be instantiated via reflection.
     */
    public static class UpperCaseResolver implements ValueResolver {

        @Override
        public String resolve(@Nullable Object parameter) {
            return parameter != null ? parameter.toString().toUpperCase() : "";
        }

    }

}
