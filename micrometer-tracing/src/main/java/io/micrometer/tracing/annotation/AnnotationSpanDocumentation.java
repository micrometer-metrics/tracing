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

package io.micrometer.tracing.annotation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.tracing.docs.EventValue;
import io.micrometer.tracing.docs.SpanDocumentation;

enum AnnotationSpanDocumentation implements SpanDocumentation {

    /**
     * Span that wraps a @NewSpan or @ContinueSpan annotations.
     */
    ANNOTATION_NEW_OR_CONTINUE_SPAN {
        @Override
        public String getName() {
            return "%s";
        }

        @Override
        public KeyName[] getKeyNames() {
            return Tags.values();
        }

        @Override
        public EventValue[] getEvents() {
            return Events.values();
        }

    };

    /**
     * Tags related to Micrometer Tracing annotations.
     *
     * @author Marcin Grzejszczak
     */
    enum Tags implements KeyName {

        /**
         * Class name where a method got annotated with a Micrometer Tracing annotation.
         */
        CLASS {
            @Override
            public String asString() {
                return "annotated.class";
            }
        },

        /**
         * Method name that got annotated with Micrometer Tracing annotation.
         */
        METHOD {
            @Override
            public String asString() {
                return "annotated.method";
            }
        }

    }

    enum Events implements EventValue {

        /**
         * Annotated before executing a method annotated with @ContinueSpan or @NewSpan.
         */
        BEFORE {
            @Override
            public String getValue() {
                return "%s.before";
            }
        },

        /**
         * Annotated after executing a method annotated with @ContinueSpan or @NewSpan.
         */
        AFTER {
            @Override
            public String getValue() {
                return "%s.after";
            }
        },

        /**
         * Annotated after throwing an exception from a method annotated
         * with @ContinueSpan or @NewSpan.
         */
        AFTER_FAILURE {
            @Override
            public String getValue() {
                return "%s.afterFailure";
            }
        }

    }

}
