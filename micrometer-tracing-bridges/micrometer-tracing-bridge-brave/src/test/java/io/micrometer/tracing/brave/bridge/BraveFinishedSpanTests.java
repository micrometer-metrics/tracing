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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class BraveFinishedSpanTests {

    TestSpanHandler handler = new TestSpanHandler();

    Tracing tracing = Tracing.newBuilder().addSpanHandler(handler).build();

    Tracer tracer = tracing.tracer();

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
        then(finishedSpan.getDuration().toNanos()).isEqualTo(TimeUnit.MICROSECONDS.toNanos(endMicros - startMicros));
    }

    @Test
    void should_set_tags() {
        FinishedSpan span = BraveFinishedSpan.fromBrave(new MutableSpan(tracer.nextSpan().context(), null));
        then(span.getTags()).isEmpty();

        Map<String, String> map = new HashMap<>();
        map.put("foo", "bar");
        span.setTags(map);

        then(span.getTags().get("foo")).isEqualTo("bar");
    }

    @Test
    void should_set_typed_tags() {
        FinishedSpan span = BraveFinishedSpan.fromBrave(new MutableSpan(tracer.nextSpan().context(), null));
        then(span.getTypedTags()).isEmpty();

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 2L);
        map.put("bar", Arrays.asList("a", "b", "c"));
        map.put("baz", Arrays.asList(1, 2, 3));
        span.setTypedTags(map);

        then(span.getTypedTags()).hasSize(3)
            .containsEntry("foo", "2")
            .containsEntry("bar", "a,b,c")
            .containsEntry("baz", "1,2,3");
    }

    @Test
    void should_set_links() {
        Tracer tracer = tracing.tracer();
        Span.Builder builder = new BraveSpanBuilder(tracer);
        Span span1 = BraveSpan.fromBrave(tracer.nextSpan());
        Span span2 = BraveSpan.fromBrave(tracer.nextSpan());
        Span span3 = BraveSpan.fromBrave(tracer.nextSpan());
        Span span4 = BraveSpan.fromBrave(tracer.nextSpan());

        builder.addLink(new Link(span1.context())).addLink(new Link(span2, tags())).start().end();

        MutableSpan traceFinishedSpan = handler.get(0);
        BraveFinishedSpan finishedSpan = new BraveFinishedSpan(traceFinishedSpan);

        finishedSpan.addLink(new Link(span3, tags()));
        finishedSpan.addLinks(Collections.singletonList(new Link(span4.context(), tags())));

        then(finishedSpan.getLinks()).hasSize(4)
            .contains(new Link(span1.context(), Collections.emptyMap()), new Link(span2.context(), tags()),
                    new Link(span3.context(), tags()), new Link(span4.context(), tags()));
    }

    private Map<String, Object> tags() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

}
