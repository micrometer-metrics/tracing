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

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class NoopTracerTests {

    @Test
    void should_not_break_when_using_noop() {
        Tracer tracer = Tracer.NOOP;
        BDDAssertions.thenNoException().isThrownBy(() -> {
            Span span = tracer.nextSpan();
            BDDAssertions.then(span.isNoop()).isTrue();
            span.start()
                .name("foo")
                .tag("foo", "bar")
                .remoteServiceName("foo")
                .remoteIpAndPort("foo", 1)
                .error(new RuntimeException())
                .event("foo")
                .event("foo", 1, TimeUnit.DAYS)
                .end();
            BDDAssertions.then(span).isSameAs(Span.NOOP);

            TraceContext context = span.context();
            context.spanId();
            context.parentId();
            context.traceId();
            context.sampled();
            BDDAssertions.then(context).isSameAs(TraceContext.NOOP);

            Span span2 = tracer.nextSpan(span);
            BDDAssertions.then(span2).isSameAs(Span.NOOP);

            Tracer.SpanInScope spanInScope = tracer.withSpan(span);
            spanInScope.close();

            ScopedSpan scopedSpan = tracer.startScopedSpan("foo");
            BDDAssertions.then(scopedSpan.isNoop()).isTrue();
            scopedSpan.name("foo").tag("foo", "bar").error(new RuntimeException()).event("foo").end();
            BDDAssertions.then(scopedSpan).isSameAs(ScopedSpan.NOOP);

            Span currentSpan = tracer.currentSpan();
            BDDAssertions.then(currentSpan).isSameAs(Span.NOOP);

            Span.Builder spanBuilder = tracer.spanBuilder();
            spanBuilder.name("foo")
                .kind(Span.Kind.CONSUMER)
                .setParent(context)
                .setNoParent()
                .remoteServiceName("foo")
                .remoteIpAndPort("foo", 1)
                .error(new RuntimeException())
                .event("foo")
                .start();
            BDDAssertions.then(spanBuilder).isSameAs(Span.Builder.NOOP);

            SpanCustomizer spanCustomizer = tracer.currentSpanCustomizer();
            spanCustomizer.event("foo").name("foo").tag("foo", "bar");
            BDDAssertions.then(spanCustomizer).isSameAs(SpanCustomizer.NOOP);

            CurrentTraceContext currentTraceContext = tracer.currentTraceContext();
            currentTraceContext.maybeScope(null);
            currentTraceContext.newScope(null);
            currentTraceContext.wrap(() -> {
            });
            currentTraceContext.wrap(() -> "");
            currentTraceContext.wrap(Executors.newSingleThreadExecutor());
            currentTraceContext.wrap(Executors.newWorkStealingPool());
            BDDAssertions.then(currentTraceContext).isSameAs(CurrentTraceContext.NOOP);
        });
    }

}
