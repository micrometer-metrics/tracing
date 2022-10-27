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

import io.micrometer.common.lang.Nullable;
import io.micrometer.tracing.propagation.Propagator;

import java.io.Closeable;
import java.util.Map;

/**
 * This API was heavily influenced by Brave. Parts of its documentation were taken
 * directly from Brave.
 * <p>
 * Using a tracer, you can create a root span capturing the critical path of a request.
 * Child spans can be created to allocate latency relating to outgoing requests.
 * <p>
 * When tracing single-threaded code, just run it inside a scoped span: <pre>{@code
 * // Start a new trace or a span within an existing trace representing an operation
 * ScopedSpan span = tracer.startScopedSpan("encode");
 * try {
 *   // The span is in "scope" so that downstream code such as loggers can see trace IDs
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.end();
 * }
 * }</pre>
 * <p>
 * When you need more features, or finer control, use the {@linkplain Span} type:
 * <pre>{@code
 * // Start a new trace or a span within an existing trace representing an operation
 * Span span = tracer.nextSpan().name("encode").start();
 * // Put the span in "scope" so that downstream code such as loggers can see trace IDs
 * try (SpanInScope ws = tracer.withSpanInScope(span)) {
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.end(); // note the scope is independent of the span. Always finish a span.
 * }
 * }</pre>
 * <p>
 * Both of the above examples report the exact same span on finish!
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @see Span
 * @see ScopedSpan
 * @see Propagator
 * @since 1.0.0
 */
public interface Tracer extends BaggageManager {

    Tracer NOOP = new Tracer() {
        @Override
        public Span nextSpan() {
            return Span.NOOP;
        }

        @Override
        public Span nextSpan(Span parent) {
            return Span.NOOP;
        }

        @Override
        public SpanInScope withSpan(Span span) {
            return () -> {

            };
        }

        @Override
        public ScopedSpan startScopedSpan(String name) {
            return ScopedSpan.NOOP;
        }

        @Override
        public Span.Builder spanBuilder() {
            return Span.Builder.NOOP;
        }

        @Override
        public TraceContext.Builder traceContextBuilder() {
            return TraceContext.Builder.NOOP;
        }

        @Override
        public CurrentTraceContext currentTraceContext() {
            return CurrentTraceContext.NOOP;
        }

        @Override
        public SpanCustomizer currentSpanCustomizer() {
            return SpanCustomizer.NOOP;
        }

        @Override
        public Span currentSpan() {
            return Span.NOOP;
        }

        @Override
        public Map<String, String> getAllBaggage() {
            return BaggageManager.NOOP.getAllBaggage();
        }

        @Override
        public BaggageInScope getBaggage(String name) {
            return BaggageInScope.NOOP;
        }

        @Override
        public BaggageInScope getBaggage(TraceContext traceContext, String name) {
            return BaggageInScope.NOOP;
        }

        @Override
        public BaggageInScope createBaggage(String name) {
            return BaggageInScope.NOOP;
        }

        @Override
        public BaggageInScope createBaggage(String name, String value) {
            return BaggageInScope.NOOP;
        }
    };

    /**
     * This creates a new span based on the current span in scope. If there's no such span
     * a new trace will be created.
     * @return a child span or a new trace if no span was present
     */
    Span nextSpan();

    /**
     * This creates a new span whose parent is {@link Span}. If parent is {@code null}
     * then will create act as {@link #nextSpan()}.
     * @param parent parent span
     * @return a child span for the given parent, {@code null} if context was empty.
     */
    Span nextSpan(@Nullable Span parent);

    /**
     * Makes the given span the "current span" and returns an object that exits that scope
     * on close. Calls to {@link #currentSpan()} and {@link #currentSpanCustomizer()} will
     * affect this span until the return value is closed.
     * <p>
     * The most convenient way to use this method is via the try-with-resources idiom.
     * <p>
     * When tracing in-process commands, prefer {@link #startScopedSpan(String)} which
     * scopes by default.
     * <p>
     * Note: While downstream code might affect the span, calling this method, and calling
     * close on the result have no effect on the input. For example, calling close on the
     * result does not finish the span. Not only is it safe to call close, you must call
     * close to end the scope, or risk leaking resources associated with the scope.
     * @param span span to place into scope or null to clear the scope
     * @return scope with span in it
     */
    Tracer.SpanInScope withSpan(@Nullable Span span);

    /**
     * Returns a new child span if there's a {@link #currentSpan()} or a new trace if
     * there isn't. The result is the "current span" until {@link ScopedSpan#end()} ()} is
     * called.
     * <p>
     * Here's an example: <pre>{@code
     * ScopedSpan span = tracer.startScopedSpan("encode");
     * try {
     *   // The span is in "scope" so that downstream code such as loggers can see trace IDs
     *   return encoder.encode();
     * } catch (RuntimeException | Error e) {
     *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
     *   throw e;
     * } finally {
     *   span.end();
     * }
     * }</pre>
     * @param name of the span in scope
     * @return span in scope
     */
    ScopedSpan startScopedSpan(String name);

    /**
     * In some cases (e.g. when dealing with
     * {@link Propagator#extract(Object, Propagator.Getter)}'s we want to create a span
     * that has not yet been started, yet it's heavily configurable (some options are not
     * possible to be set when a span has already been started). We can achieve that by
     * using a builder.
     * @return a span builder
     */
    Span.Builder spanBuilder();

    /**
     * Builder for {@link TraceContext}.
     * @return a trace context builder
     */
    TraceContext.Builder traceContextBuilder();

    /**
     * Returns the {@link CurrentTraceContext}.
     * @return current trace context
     */
    CurrentTraceContext currentTraceContext();

    /**
     * Allows to customize the current span in scope.
     * @return current span customizer
     */
    @Nullable
    SpanCustomizer currentSpanCustomizer();

    /**
     * Retrieves the current span in scope or {@code null} if one is not available.
     * @return current span in scope
     */
    @Nullable
    Span currentSpan();

    /**
     * Scope of a span. Needs to be closed so that resources are let go (e.g. MDC is
     * cleared).
     */
    interface SpanInScope extends Closeable {

        @Override
        void close();

    }

}
