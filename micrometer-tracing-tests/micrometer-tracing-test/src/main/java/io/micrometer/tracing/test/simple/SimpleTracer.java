/**
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.*;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A test tracer implementation. Puts started span in a list.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleTracer implements Tracer {

    private static final Map<TraceContext, SimpleSpan> traceContextToSpans = new ConcurrentHashMap<>();

    private static final ThreadLocal<SimpleSpan> scopedSpans = new ThreadLocal<>();

    private final SimpleCurrentTraceContext currentTraceContext;

    final SimpleBaggageManager simpleBaggageManager = new SimpleBaggageManager(this);

    private final Deque<SimpleSpan> spans = new LinkedBlockingDeque<>();

    /**
     * Creates a new instance of {@link SimpleTracer}.
     */
    public SimpleTracer() {
        this.currentTraceContext = new SimpleCurrentTraceContext(this);
    }

    @Override
    public SimpleSpan nextSpan(@Nullable Span parent) {
        SimpleSpan span = simpleSpan(parent);
        this.spans.add(span);
        return span;
    }

    private SimpleSpan simpleSpan(@Nullable Span parent) {
        Map<String, String> baggageFromParent = Collections.emptyMap();
        SimpleSpan span = new SimpleSpan();
        if (parent != null) {
            span.context().setParentId(parent.context().spanId());
            baggageFromParent = simpleBaggageManager.getAllBaggageForCtx(parent.context());
        }
        String traceId = parent != null ? parent.context().traceId() : span.context().generateId();
        span.context().setTraceId(traceId);
        span.context().setSpanId(parent != null ? span.context().generateId() : traceId);
        span.context().addParentBaggage(baggageFromParent);
        return span;
    }

    /**
     * Returns a single reported span.
     * @return a single reported span
     * @throws AssertionError when there are 0 or more than 1 spans
     * @throws AssertionError when span hasn't been started and stopped
     */
    public SimpleSpan onlySpan() {
        assertTrue(this.spans.size() == 1, "There must be only one span");
        SimpleSpan span = this.spans.getFirst();
        assertTrue(span.getStartTimestamp().toEpochMilli() > 0, "Span must be started");
        assertTrue(span.getEndTimestamp().toEpochMilli() > 0, "Span must be finished");
        return span;
    }

    private void assertTrue(boolean condition, String text) {
        if (!condition) {
            throw new AssertionError(text);
        }
    }

    /**
     * Returns the last reported span.
     * @return the last reported span
     * @throws AssertionError when there are 0 spans
     * @throws AssertionError when span hasn't been started
     */
    public SimpleSpan lastSpan() {
        assertTrue(!this.spans.isEmpty(), "There must be at least one span");
        SimpleSpan span = this.spans.getLast();
        assertTrue(span.getStartTimestamp().toEpochMilli() > 0, "Span must be started");
        return span;
    }

    @Override
    public SimpleSpanInScope withSpan(@Nullable Span span) {
        return new SimpleSpanInScope(this.currentTraceContext.newScope(span != null ? span.context() : null));
    }

    @Override
    public SimpleSpanCustomizer currentSpanCustomizer() {
        return new SimpleSpanCustomizer(this);
    }

    @Override
    public @Nullable SimpleSpan currentSpan() {
        return scopedSpans.get();
    }

    @Override
    public SimpleSpan nextSpan() {
        SimpleSpan span = simpleSpan(currentSpan());
        this.spans.add(span);
        return span;
    }

    @Override
    public ScopedSpan startScopedSpan(String name) {
        return new SimpleScopedSpan(this).name(name);
    }

    @Override
    public SimpleSpanBuilder spanBuilder() {
        return new SimpleSpanBuilder(this);
    }

    @Override
    public TraceContext.Builder traceContextBuilder() {
        return new SimpleTraceContextBuilder();
    }

    @Override
    public SimpleCurrentTraceContext currentTraceContext() {
        return this.currentTraceContext;
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return this.simpleBaggageManager.getAllBaggage();
    }

    @Override
    public Baggage getBaggage(String name) {
        return this.simpleBaggageManager.getBaggage(name);
    }

    @Override
    public Baggage getBaggage(TraceContext traceContext, String name) {
        return this.simpleBaggageManager.getBaggage(traceContext, name);
    }

    @Override
    public Map<String, String> getAllBaggage(@Nullable TraceContext traceContext) {
        if (traceContext == null) {
            return this.simpleBaggageManager.getAllBaggage();
        }
        return this.simpleBaggageManager.getAllBaggageForCtx(traceContext);
    }

    @Override
    public Baggage createBaggage(String name) {
        return this.simpleBaggageManager.createBaggage(name);
    }

    @Override
    public Baggage createBaggage(String name, String value) {
        return this.simpleBaggageManager.createBaggage(name, value);
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return this.simpleBaggageManager.createBaggageInScope(name, value);
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return this.simpleBaggageManager.createBaggageInScope(traceContext, name, value);
    }

    @Override
    public List<String> getBaggageFields() {
        return this.simpleBaggageManager.getBaggageFields();
    }

    /**
     * Created spans.
     * @return all created spans
     */
    public Deque<SimpleSpan> getSpans() {
        return spans;
    }

    /**
     * Binds the given {@link Span} to the given {@link TraceContext}.
     * @param traceContext the traceContext to use to bind this span to
     * @param span the span that needs to be bounded to the traceContext
     */
    static void bindSpanToTraceContext(TraceContext traceContext, SimpleSpan span) {
        traceContextToSpans.put(traceContext, span);
    }

    /**
     * Returns the {@link Span} that is bounded to the given {@link TraceContext}.
     * @param traceContext the traceContext to use to fetch the span
     * @return the span that is bounded to the given traceContext (null if none)
     */
    static @Nullable SimpleSpan getSpanForTraceContext(TraceContext traceContext) {
        return traceContextToSpans.get(traceContext);
    }

    static SimpleSpan getCurrentSpan() {
        return scopedSpans.get();
    }

    static void resetCurrentSpan() {
        scopedSpans.remove();
    }

    static void setCurrentSpan(SimpleSpan simpleSpan) {
        scopedSpans.set(simpleSpan);
    }

    static void setCurrentSpan(TraceContext context) {
        scopedSpans.set(context != null ? getSpanForTraceContext(context) : null);
    }

}
