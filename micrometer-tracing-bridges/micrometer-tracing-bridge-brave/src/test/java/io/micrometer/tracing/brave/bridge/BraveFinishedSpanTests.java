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
package io.micrometer.tracing.brave.bridge;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class BraveFinishedSpanTests {

    TestSpanHandler handler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(handler).build();

    Tracer tracer = tracing.tracer();

    BraveTracer braveTracer = new BraveTracer(tracer, new BraveCurrentTraceContext(tracing.currentTraceContext()));

    @AfterEach
    void cleanup() {
        tracing.close();
    }

    @Test
    void should_calculate_instant_from_brave_timestamps() {
        long startMicros = TimeUnit.MILLISECONDS.toMicros(Instant.now().toEpochMilli());
        long endMicros = TimeUnit.MILLISECONDS.toMicros(Instant.now().toEpochMilli());
        MutableSpan span = new MutableSpan(tracer.nextSpan().context(), null);
        span.startTimestamp(startMicros);
        span.finishTimestamp(endMicros);

        FinishedSpan finishedSpan = new BraveFinishedSpan(span);

        then(finishedSpan.getStartTimestamp().toEpochMilli()).isEqualTo(TimeUnit.MICROSECONDS.toMillis(startMicros));
        then(finishedSpan.getEndTimestamp().toEpochMilli()).isEqualTo(TimeUnit.MICROSECONDS.toMillis(endMicros));
    }

    @Test
    void should_set_links() {
        Tracer tracer = tracing.tracer();
        Span.Builder builder = new BraveSpanBuilder(tracer);
        Span span1 = BraveSpan.fromBrave(tracer.nextSpan());
        Span span2 = BraveSpan.fromBrave(tracer.nextSpan());
        Span span3 = BraveSpan.fromBrave(tracer.nextSpan());
        Span span4 = BraveSpan.fromBrave(tracer.nextSpan());

        builder.addLink(span1.context()).addLink(span2.context(), tags()).start().end();

        MutableSpan finishedSpan = handler.get(0);
        BraveFinishedSpan braveFinishedSpan = new BraveFinishedSpan(finishedSpan);

        braveFinishedSpan.addLink(span3.context(), tags());
        braveFinishedSpan.addLinks(Collections.singletonMap(span4.context(), tags()));

        then(braveFinishedSpan.getLinks()).hasSize(4)
            .containsEntry(span1.context(), Collections.emptyMap())
            .containsEntry(span2.context(), tags())
            .containsEntry(span3.context(), tags())
            .containsEntry(span4.context(), tags());
    }

    private Map<String, String> tags() {
        Map<String, String> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

}
