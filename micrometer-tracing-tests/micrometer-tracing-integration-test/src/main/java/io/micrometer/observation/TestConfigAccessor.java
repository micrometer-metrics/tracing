/*
 * Copyright 2013-2020 the original author or authors.
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

package io.micrometer.observation;

import java.util.Collection;

/**
 * Internal, test class - do not use.
 *
 * @since 1.0.0
 */
public final class TestConfigAccessor {

    private TestConfigAccessor() {
        throw new IllegalStateException("Should not instantiate a utility class");
    }

    /**
     * Returns the stored handlers.
     * @param config meter registry config
     * @return stored handlers
     */
    public static Collection<ObservationHandler<?>> getHandlers(ObservationRegistry.ObservationConfig config) {
        return config.getObservationHandlers();
    }

    /**
     * Clears the timer recording handlers.
     * @param config meter registry config
     */
    public static void clearHandlers(ObservationRegistry.ObservationConfig config) {
        config.getObservationHandlers().clear();
    }

}
