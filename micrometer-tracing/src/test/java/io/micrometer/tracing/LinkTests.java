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
import org.mockito.BDDMockito;

import java.util.HashMap;
import java.util.Map;

class LinkTests {

    @Test
    void equals_should_work_with_trace_context() {
        TraceContext traceContext = BDDMockito.mock(TraceContext.class);
        Link link1 = new Link(traceContext);
        Link link2 = new Link(traceContext);

        BDDAssertions.then(link1).isEqualTo(link2);

        link1 = new Link(traceContext);
        link2 = new Link(TraceContext.NOOP);

        BDDAssertions.then(link1).isNotEqualTo(link2);
    }

    @Test
    void equals_should_work_with_tags() {
        TraceContext traceContext = BDDMockito.mock(TraceContext.class);
        Link link1 = new Link(traceContext, tags1());
        Link link2 = new Link(traceContext, tags1());

        BDDAssertions.then(link1).isEqualTo(link2);

        link1 = new Link(traceContext, tags1());
        link2 = new Link(traceContext, tags2());

        BDDAssertions.then(link1).isNotEqualTo(link2);
    }

    @Test
    void hashcode_should_work_with_trace_context() {
        TraceContext traceContext = BDDMockito.mock(TraceContext.class);
        Link link1 = new Link(traceContext);
        Link link2 = new Link(traceContext);

        BDDAssertions.then(link1.hashCode()).isEqualTo(link2.hashCode());

        link1 = new Link(traceContext);
        link2 = new Link(TraceContext.NOOP);

        BDDAssertions.then(link1.hashCode()).isNotEqualTo(link2.hashCode());
    }

    @Test
    void hashchode_should_work_with_tags() {
        TraceContext traceContext = BDDMockito.mock(TraceContext.class);
        Link link1 = new Link(traceContext, tags1());
        Link link2 = new Link(traceContext, tags1());

        BDDAssertions.then(link1.hashCode()).isEqualTo(link2.hashCode());

        link1 = new Link(traceContext, tags1());
        link2 = new Link(traceContext, tags2());

        BDDAssertions.then(link1.hashCode()).isNotEqualTo(link2.hashCode());
    }

    private Map<String, String> tags1() {
        Map<String, String> map = new HashMap<>();
        map.put("tag1", "value1");
        map.put("tag2", "value2");
        return map;
    }

    private Map<String, String> tags2() {
        Map<String, String> map = new HashMap<>();
        map.put("tag3", "value3");
        map.put("tag4", "value4");
        return map;
    }

}
