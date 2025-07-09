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
package io.micrometer.tracing.internal;

import io.micrometer.common.lang.internal.Contract;
import io.micrometer.common.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Utility class that provides the name in hyphen based notation.
 *
 * @author Adrian Cole
 * @since 1.0.0
 */
public final class SpanNameUtil {

    static final int MAX_NAME_LENGTH = 50;

    private SpanNameUtil() {

    }

    /**
     * Shortens the name of a span.
     * @param name name to shorten
     * @return shortened name
     */
    @Contract("!null -> !null")
    public static @Nullable String shorten(@Nullable String name) {
        if (!StringUtils.isNotBlank(name)) {
            return name;
        }
        int maxLength = Math.min(name.length(), MAX_NAME_LENGTH);
        return name.substring(0, maxLength);
    }

    /**
     * Converts the name to a lower hyphen version.
     * @param name name to change
     * @return changed name
     */
    public static String toLowerHyphen(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i != 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            }
            else {
                result.append(c);
            }
        }
        return SpanNameUtil.shorten(result.toString());
    }

}
