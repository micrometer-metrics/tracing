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

import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import io.micrometer.tracing.exporter.FinishedSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;

class BraveFinishedSpanTests {

    Tracing tracing = Tracing.newBuilder().build();

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
    }

}
