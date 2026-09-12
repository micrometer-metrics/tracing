/**
 * Copyright 2024 the original author or authors.
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

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueResolver;

import java.lang.annotation.*;

/**
 * Annotation to be used on method parameters to put the parameter value into baggage
 * scope for the duration of the method execution. The baggage scope is automatically
 * closed when the method completes.
 * <p>
 * There are 3 different ways to resolve the baggage value. All of them are controlled by
 * the annotation values. Precedence is:
 * <p>
 * Try with the {@link ValueResolver} bean if the value of the bean wasn't set, try to
 * evaluate a SPEL expression if there's no SPEL expression just return a
 * {@code toString()} value of the parameter.
 *
 * @author Stuart Kemp
 * @since 1.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.PARAMETER)
public @interface BaggageKeyValue {

    /**
     * The name of the baggage entry.
     * @return the baggage name
     */
    String value() default "";

    /**
     * The name of the baggage entry. An alias for {@link #value()}.
     * @return the baggage name
     */
    String key() default "";

    /**
     * Execute this expression to calculate the baggage value. Will be analyzed if no
     * value of the {@link BaggageKeyValue#resolver()} was set.
     * @return an expression
     */
    String expression() default "";

    /**
     * Use this bean to resolve the baggage value. Has the highest precedence.
     * @return {@link ValueResolver} bean
     */
    Class<? extends ValueResolver> resolver() default NoOpValueResolver.class;

}
