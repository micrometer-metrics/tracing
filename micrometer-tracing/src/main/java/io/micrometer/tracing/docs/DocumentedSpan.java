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

package io.micrometer.tracing.docs;

import io.micrometer.api.instrument.docs.DocumentedSample;
import io.micrometer.api.instrument.docs.TagKey;
import io.micrometer.tracing.handler.TracingRecordingHandler;

/**
 * In order to describe your spans via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a span. In Micrometer Tracing we
 * analyze the sources and reuse this information to build a table of known spans, their
 * names, tags and events.
 *
 * We can generate documentation for all created spans but certain requirements need to be
 * met
 *
 * <ul>
 *     <li>metrics are grouped within an enum - the enum implements the {@link DocumentedSpan} interface</li>
 *     <li>if the span contains {@link TagKey} then those need to be declared as nested enums</li>
 *     <li>if the span contains {@link EventValue} then those need to be declared as nested enums</li>
 *     <li>the {@link DocumentedSpan#getTagKeys()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys / events</li>
 *     <li>the {@link DocumentedSpan#getEvents()} ()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys / events</li>
 *     <li>Javadocs around enums will be used as description</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface DocumentedSpan {

    /**
     * @return span name
     */
    String getName();

    /**
     * Builds a name from provided vars. Follows the {@link String#format(String, Object...)} patterns.
     * @param vars variables to pass to {@link String#format(String, Object...)}
     * @return constructed name
     */
    default String getName(String... vars) {
        if (getName().contains("%s")) {
            return String.format(getName(), (Object[]) vars);
        }
        return getName();
    }

    /**
     * @return allowed events
     */
    default EventValue[] getEvents() {
        return new EventValue[0];
    }

    /**
     * @return allowed tag keys - if set will override any tag keys coming from {@link DocumentedSpan#overridesDefaultSpanFrom()}
     */
    default TagKey[] getTagKeys() {
        return new TagKey[0];
    }

    /**
     * @return additional tag keys - if set will append any tag keys coming from {@link DocumentedSpan#overridesDefaultSpanFrom()}
     */
    default TagKey[] getAdditionalTagKeys() {
        return new TagKey[0];
    }

    /**
     * Provide a {@link DocumentedSample} class whose default
     * span creation should be ignored and will be overridden by this implementation. This should be overridden when
     * you have a custom {@link TracingRecordingHandler} that sets up spans in a custom fashion.
     *
     * @return {@link DocumentedSample} class
     */
    default DocumentedSample overridesDefaultSpanFrom() {
        return null;
    }
}
