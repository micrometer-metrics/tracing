/**
 * Copyright 2023 the original author or authors.
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
package io.micrometer.tracing.brave.bridge;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;

/**
 * @deprecated scheduled for removal in 1.4.0
 */
@Deprecated
final class DeprecatedClassLogger {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(DeprecatedClassLogger.class);

    static void logWarning(Class<?> clazz) {
        log.warn("The class <{}> is scheduled for removal in the next minor. Please migrate away from using it.",
                clazz);
    }

}
