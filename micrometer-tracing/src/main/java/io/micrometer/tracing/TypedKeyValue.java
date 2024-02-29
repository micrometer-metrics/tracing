/**
 * Copyright 2022 the original author or authors.
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
package io.micrometer.tracing;

import io.micrometer.common.KeyValue;

public abstract class TypedKeyValue<T> implements KeyValue {

    protected final String key;

    protected final T value;

    TypedKeyValue(String key, T value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return String.valueOf(value);
    }

    public abstract void setAttribute(Span span);

    public static TypedKeyValue<Long> of(String key, long value) {
        return new TypedKeyValue<Long>(key, value) {
            @Override
            public void setAttribute(Span span) {
                span.tag(getKey(), value);
            }
        };
    }

    public static TypedKeyValue<String> of(String key, String value) {
        return new TypedKeyValue<String>(key, value) {
            @Override
            public void setAttribute(Span span) {
                span.tag(getKey(), value);
            }
        };
    }

    public static TypedKeyValue<Double> of(String key, double value) {
        return new TypedKeyValue<Double>(key, value) {
            @Override
            public void setAttribute(Span span) {
                span.tag(getKey(), value);
            }
        };

    }

    public static TypedKeyValue<Boolean> of(String key, boolean value) {
        return new TypedKeyValue<Boolean>(key, value) {
            @Override
            public void setAttribute(Span span) {
                span.tag(getKey(), value);
            }
        };
    }

}
