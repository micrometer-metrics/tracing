/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.benchmark.tracer;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.openjdk.jmh.infra.Blackhole;

interface MicrometerTracingBenchmarks {

    default void micrometerTracing(Tracer tracer, int childSpanCount, Blackhole blackhole) {
        Span parentSpan = tracer.nextSpan().name("parent-span").start();
        for (int i = 0; i < childSpanCount; i++) {
            Span span = tracer.nextSpan(parentSpan).name("new-span" + i);
            span.start().tag("key", "value").event("event").end();
        }
        parentSpan.end();
        blackhole.consume(parentSpan);
    }

    default void micrometerTracingWithScope(Tracer tracer, int childSpanCount, Blackhole blackhole) {
        Span parentSpan = tracer.nextSpan().name("parent-span").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(parentSpan)) {
            for (int i = 0; i < childSpanCount; i++) {
                Span childSpan = tracer.nextSpan().name("new-span" + i).start();
                try (Tracer.SpanInScope ws2 = tracer.withSpan(childSpan)) {
                    childSpan.tag("key", "value").event("event");
                }
                childSpan.end();
            }
        }
        parentSpan.end();
        blackhole.consume(parentSpan);
    }
}
