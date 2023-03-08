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
package io.micrometer.tracing.test.simple;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class SimpleBaggageManagerTests {

    SimpleTracer tracer = new SimpleTracer();

    SimpleBaggageManager manager = new SimpleBaggageManager(tracer);

    @Test
    void should_create_baggage() {
        SimpleSpan simpleSpan = tracer.nextSpan();
        try (Tracer.SpanInScope scope = tracer.withSpan(simpleSpan.start())) {
            try (BaggageInScope baggage = manager.createBaggageInScope("foo", "bar")) {
                then(manager.getBaggage("foo").get()).isEqualTo("bar");
                then(manager.getBaggage(simpleSpan.context(), "foo").get()).isEqualTo("bar");
                then(manager.getAllBaggage()).containsEntry("foo", "bar");
            }
        }
        then(manager.getBaggage("foo").get()).isNull();
        then(manager.getAllBaggage()).isEmpty();
    }

}
