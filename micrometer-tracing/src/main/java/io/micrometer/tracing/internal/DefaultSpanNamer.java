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

package org.springframework.boot.autoconfigure.observability.tracing.internal;

import java.lang.reflect.Method;

import io.micrometer.tracing.SpanName;
import io.micrometer.tracing.SpanNamer;

/**
 * Default implementation of SpanNamer that tries to get the span name as follows:
 *
 * * from the @SpanName annotation on the class if one is present.
 *
 * * from the @SpanName annotation on the method if passed object is of a {@link Method}.
 * type
 *
 * * from the toString() of the delegate if it's not the default
 * {@link Object#toString()}.
 *
 * * the default provided value.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @see SpanName
 */
public class DefaultSpanNamer implements SpanNamer {

    private static boolean isDefaultToString(Object delegate, String spanName) {
        if (delegate instanceof Method) {
            return delegate.toString().equals(spanName);
        }
        return (delegate.getClass().getName() + "@" + Integer.toHexString(delegate.hashCode())).equals(spanName);
    }

    @Override
    public String name(Object object, String defaultValue) {
        SpanName annotation = annotation(object);
        String spanName = annotation != null ? annotation.value() : object.toString();
        // If there is no overridden toString method we'll put a constant value
        if (isDefaultToString(object, spanName)) {
            return defaultValue;
        }
        return spanName;
    }

    private SpanName annotation(Object o) {
        if (o instanceof Method) {
            return ((Method) o).getAnnotation(SpanName.class);
        }
        return o.getClass().getAnnotation(SpanName.class);
    }

}
