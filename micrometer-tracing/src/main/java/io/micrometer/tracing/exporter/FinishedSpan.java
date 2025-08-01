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
package io.micrometer.tracing.exporter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;

/**
 * This API is inspired by OpenZipkin Brave (from {code MutableSpan}).
 *
 * Represents a span that has been finished and is ready to be sent to an external
 * location (e.g. Zipkin).
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @author Yanming Zhou
 * @since 1.0.0
 */
public interface FinishedSpan {

    /**
     * Sets the name.
     * @param name name to set
     * @return this
     */
    FinishedSpan setName(String name);

    /**
     * @return span's name
     */
    String getName();

    /**
     * @return span's start timestamp
     */
    Instant getStartTimestamp();

    /**
     * @return span's end timestamp
     */
    Instant getEndTimestamp();

    /**
     * @return span's duration
     * @since 1.2.0
     */
    default Duration getDuration() {
        return Duration.between(getStartTimestamp(), getEndTimestamp());
    }

    /**
     * Sets the tags.
     * @param tags tags to set
     * @return this
     */
    FinishedSpan setTags(Map<String, String> tags);

    /**
     * @return span's tags
     */
    Map<String, String> getTags();

    /**
     * Sets the tags with typed values.
     * @param tags tags to set
     * @return this
     * @since 1.1.0
     */
    default FinishedSpan setTypedTags(Map<String, Object> tags) {
        Map<String, String> map = new HashMap<>();
        tags.forEach((s, o) -> {
            if (o instanceof List) {
                map.put(s, ((List<?>) o).stream().map(Object::toString).collect(Collectors.joining(",")));
            }
            else {
                map.put(s, String.valueOf(o));
            }
        });
        return setTags(map);
    }

    /**
     * Not all tracers (e.g. Brave) honor different values of tags in which case the tags
     * will always get converted to String.
     * @return span's tags with typed values
     * @since 1.1.0
     */
    default Map<String, Object> getTypedTags() {
        return new HashMap<>(getTags());
    }

    /**
     * Sets the events.
     * @param events events to set
     * @return this
     */
    FinishedSpan setEvents(Collection<Map.Entry<Long, String>> events);

    /**
     * @return span's events as timestamp to value mapping
     */
    Collection<Map.Entry<Long, String>> getEvents();

    /**
     * @return span's span id
     */
    String getSpanId();

    /**
     * @return span's parent id or {@code null} if not set
     */
    @Nullable String getParentId();

    /**
     * @return span's remote ip
     */
    @Nullable String getRemoteIp();

    /**
     * @return span's local ip
     */
    default @Nullable String getLocalIp() {
        return null;
    }

    /**
     * Sets the local ip.
     * @param ip ip to set
     * @return this
     */
    FinishedSpan setLocalIp(String ip);

    /**
     * @return span's remote port
     */
    int getRemotePort();

    /**
     * Sets the remote port.
     * @param port port to set
     * @return this
     */
    FinishedSpan setRemotePort(int port);

    /**
     * @return span's trace id
     */
    String getTraceId();

    /**
     * @return corresponding error or {@code null} if one was not thrown
     */
    @Nullable Throwable getError();

    /**
     * Sets the error.
     * @param error error to set
     * @return this
     */
    FinishedSpan setError(Throwable error);

    /**
     * @return span's kind
     */
    Span.@Nullable Kind getKind();

    /**
     * @return remote service name or {@code null} if not set
     */
    @Nullable String getRemoteServiceName();

    /**
     * Sets the remote service name.
     * @param remoteServiceName remote service name
     * @return this
     */
    FinishedSpan setRemoteServiceName(String remoteServiceName);

    /**
     * @return local service name or {@code null} if not set
     * @since 1.5
     */
    default @Nullable String getLocalServiceName() {
        return null;
    }

    /**
     * Sets the local service name.
     * @param localServiceName local service name
     * @return this
     * @since 1.5
     */
    default FinishedSpan setLocalServiceName(String localServiceName) {
        return this;
    }

    /**
     * @return links
     * @since 1.1.0
     */
    default List<Link> getLinks() {
        return Collections.emptyList();
    }

    /**
     * Adds links.
     * @param links links to add
     * @return this
     * @since 1.1.0
     */
    default FinishedSpan addLinks(List<Link> links) {
        return this;
    }

    /**
     * Adds a link.
     * @param link link to add
     * @return this
     * @since 1.1.0
     */
    default FinishedSpan addLink(Link link) {
        return this;
    }

}
