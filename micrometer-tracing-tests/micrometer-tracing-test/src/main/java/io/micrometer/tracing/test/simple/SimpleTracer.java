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

package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.*;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

/**
 * A test tracer implementation. Puts started span in a list.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class SimpleTracer implements Tracer {

    private final SimpleCurrentTraceContext currentTraceContext;

    private final SimpleBaggageManager simpleBaggageManager = new SimpleBaggageManager(this);

    private final Deque<SimpleSpan> spans = new LinkedList<>();

    private final Deque<SpanAndScope> scopedSpans = new LinkedList<>();

    /**
     * Creates a new instance of {@link SimpleTracer}.
     */
    public SimpleTracer() {
        this.currentTraceContext = new SimpleCurrentTraceContext(this);
    }

    @Override
    public SimpleSpan nextSpan(Span parent) {
        return new SimpleSpan();
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
        assertTrue(span.getStartTimestamp().getNano() > 0, "Span must be started");
        assertTrue(span.getEndTimestamp().getNano() > 0, "Span must be finished");
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
        assertTrue(span.getStartTimestamp().getNano() > 0, "Span must be started");
        return span;
    }

    @Override
    public SimpleSpanInScope withSpan(Span span) {
        return new SimpleSpanInScope(span, scopedSpans);
    }

    @Override
    public SimpleSpanCustomizer currentSpanCustomizer() {
        return new SimpleSpanCustomizer(currentSpan());
    }

    @Override
    public SimpleSpan currentSpan() {
        SpanAndScope first = this.scopedSpans.peekFirst();
        if (first == null) {
            return null;
        }
        return (SimpleSpan) first.getSpan();
    }

    @Override
    public SimpleSpan nextSpan() {
        final SimpleSpan span = new SimpleSpan();
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
    public BaggageInScope getBaggage(String name) {
        return this.simpleBaggageManager.getBaggage(name);
    }

    @Override
    public BaggageInScope getBaggage(TraceContext traceContext, String name) {
        return this.simpleBaggageManager.getBaggage(traceContext, name);
    }

    @Override
    public BaggageInScope createBaggage(String name) {
        return this.simpleBaggageManager.createBaggage(name);
    }

    @Override
    public BaggageInScope createBaggage(String name, String value) {
        return this.simpleBaggageManager.createBaggage(name, value);
    }

    /**
     * Created spans.
     * @return all created spans
     */
    public Deque<SimpleSpan> getSpans() {
        return spans;
    }

}
