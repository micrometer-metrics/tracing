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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.tracing.propagation.Propagator;

/**
 * This API was heavily influenced by Brave. Parts of its documentation were taken
 * directly from Brave.
 * <p>
 * Span is a single unit of work that needs to be started and stopped. Contains timing
 * information and events and tags.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface Span extends io.micrometer.tracing.SpanCustomizer {

    /**
     * A noop implementation.
     */
    Span NOOP = new Span() {
        @Override
        public boolean isNoop() {
            return true;
        }

        @Override
        public TraceContext context() {
            return TraceContext.NOOP;
        }

        @Override
        public Span start() {
            return this;
        }

        @Override
        public Span name(String name) {
            return this;
        }

        @Override
        public Span event(String value) {
            return this;
        }

        @Override
        public Span event(String value, long time, TimeUnit timeUnit) {
            return this;
        }

        @Override
        public Span tag(String key, String value) {
            return this;
        }

        @Override
        public Span error(Throwable throwable) {
            return this;
        }

        @Override
        public void end() {

        }

        @Override
        public void end(long time, TimeUnit timeUnit) {

        }

        @Override
        public void abandon() {

        }

        @Override
        public Span remoteServiceName(String remoteServiceName) {
            return this;
        }

        @Override
        public Span remoteIpAndPort(String ip, int port) {
            return this;
        }
    };

    /**
     * @return {@code true} when no recording is done and nothing is reported to an
     * external system. However, this span should still be injected into outgoing
     * requests. Use this flag to avoid performing expensive computation.
     */
    boolean isNoop();

    /**
     * @return {@link TraceContext} corresponding to this span.
     */
    TraceContext context();

    /**
     * Starts this span.
     * @return this span
     */
    Span start();

    /**
     * Sets a name on this span.
     * @param name name to set on the span
     * @return this span
     */
    Span name(String name);

    /**
     * Sets an event on this span.
     * @param value event name to set on the span
     * @return this span
     */
    Span event(String value);

    /**
     * Sets an event on this span.
     * @param value event name to set on the span
     * @param time timestamp of the event
     * @param timeUnit timestamp's time unit
     * @return this span
     */
    Span event(String value, long time, TimeUnit timeUnit);

    /**
     * Sets a tag on this span.
     * @param key tag key
     * @param value tag value
     * @return this span
     */
    Span tag(String key, String value);

    /**
     * Records an exception for this span.
     * @param throwable to record
     * @return this span
     */
    Span error(Throwable throwable);

    /**
     * Ends the span. The span gets stopped and recorded if not noop.
     */
    void end();

    /**
     * Ends the span. The span gets stopped and recorded if not noop.
     * @param time timestamp
     * @param timeUnit time unit of the timestamp
     */
    void end(long time, TimeUnit timeUnit);

    /**
     * Ends the span. The span gets stopped but does not get recorded.
     */
    void abandon();

    /**
     * Sets the remote service name for the span.
     * @param remoteServiceName remote service name
     * @return this span
     */
    Span remoteServiceName(String remoteServiceName);

    /**
     * Sets the remote url on the span.
     * @param ip remote ip
     * @param port remote port
     * @return this span
     * @since 1.0.0
     */
    Span remoteIpAndPort(String ip, int port);

    /**
     * Type of span. Can be used to specify additional relationships between spans in
     * addition to a parent/child relationship.
     * <p>
     * Documentation of the enum taken from OpenTelemetry.
     */
    enum Kind {

        /**
         * Indicates that the span covers server-side handling of an RPC or other remote
         * request.
         */
        SERVER,

        /**
         * Indicates that the span covers the client-side wrapper around an RPC or other
         * remote request.
         */
        CLIENT,

        /**
         * Indicates that the span describes producer sending a message to a broker.
         * Unlike client and server, there is no direct critical path latency relationship
         * between producer and consumer spans.
         */
        PRODUCER,

        /**
         * Indicates that the span describes consumer receiving a message from a broker.
         * Unlike client and server, there is no direct critical path latency relationship
         * between producer and consumer spans.
         */
        CONSUMER

    }

    /**
     * In some cases (e.g. when dealing with
     * {@link Propagator#extract(Object, Propagator.Getter)}'s we want to create a span
     * that has not yet been started, yet it's heavily configurable (some options are not
     * possible to be set when a span has already been started). We can achieve that by
     * using a builder.
     * <p>
     * Inspired by OpenZipkin Brave and OpenTelemetry API.
     */
    interface Builder {

        /**
         * A noop implementation.
         */
        Builder NOOP = new Builder() {
            @Override
            public Builder setParent(TraceContext context) {
                return this;
            }

            @Override
            public Builder setNoParent() {
                return this;
            }

            @Override
            public Builder name(String name) {
                return this;
            }

            @Override
            public Builder event(String value) {
                return this;
            }

            @Override
            public Builder tag(String key, String value) {
                return this;
            }

            @Override
            public Builder error(Throwable throwable) {
                return this;
            }

            @Override
            public Builder kind(Kind spanKind) {
                return this;
            }

            @Override
            public Builder remoteServiceName(String remoteServiceName) {
                return this;
            }

            @Override
            public Builder remoteIpAndPort(String ip, int port) {
                return this;
            }

            @Override
            public Builder startTimestamp(long startTimestamp, TimeUnit unit) {
                return this;
            }

            @Override
            public Span start() {
                return Span.NOOP;
            }
        };

        /**
         * Sets the parent of the built span.
         * @param context parent's context
         * @return this
         */
        Builder setParent(TraceContext context);

        /**
         * Sets no parent of the built span.
         * @return this
         */
        Builder setNoParent();

        /**
         * Sets the name of the span.
         * @param name span name
         * @return this
         */
        Builder name(String name);

        /**
         * Sets an event on the span.
         * @param value event value
         * @return this
         */
        Builder event(String value);

        /**
         * Sets a tag on the span.
         * @param key tag key
         * @param value tag value
         * @return this
         */
        Builder tag(String key, String value);

        /**
         * Sets an error on the span.
         * @param throwable error to set
         * @return this
         */
        Builder error(Throwable throwable);

        /**
         * Sets the kind on the span.
         * @param spanKind kind of the span
         * @return this
         */
        Builder kind(Span.Kind spanKind);

        /**
         * Sets the remote service name for the span.
         * @param remoteServiceName remote service name
         * @return this
         */
        Builder remoteServiceName(String remoteServiceName);

        /**
         * Sets the remote URL for the span.
         * @param ip remote service ip
         * @param port remote service port
         * @return this
         */
        Builder remoteIpAndPort(String ip, int port);

        /**
         * Sets start timestamp.
         * @param startTimestamp start timestamp
         * @param unit start time unit
         * @return this
         */
        Builder startTimestamp(long startTimestamp, TimeUnit unit);

        /**
         * Adds a link to the newly created {@code Span}.
         *
         * <p>
         * Links are used to link {@link Span}s in different traces. Used (for example) in
         * batching operations, where a single batch handler processes multiple requests
         * from different traces or the same trace.
         * @param traceContext the context of the linked {@code Span}.
         * @return this
         * @since 1.1.0
         */
        default Builder addLink(TraceContext traceContext) {
            return this;
        }

        /**
         * Adds a link to the newly created {@code Span}.
         *
         * <p>
         * Links are used to link {@link Span}s in different traces. Used (for example) in
         * batching operations, where a single batch handler processes multiple requests
         * from different traces or the same trace.
         * @param traceContext the context of the linked {@code Span}.
         * @param attributes the attributes of the {@code Link}.
         * @return this
         * @since 1.1.0
         */
        default Builder addLink(TraceContext traceContext, Map<String, String> attributes) {
            return this;
        }

        /**
         * Builds and starts the span.
         * @return started span
         */
        Span start();

    }

}
