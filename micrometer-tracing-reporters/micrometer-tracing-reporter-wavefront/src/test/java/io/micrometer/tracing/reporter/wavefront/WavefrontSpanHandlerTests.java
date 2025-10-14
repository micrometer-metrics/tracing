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
package io.micrometer.tracing.reporter.wavefront;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.test.simple.SimpleSpan;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link WavefrontSpanHandler}.
 *
 * @author Moritz Halbritter
 */
class WavefrontSpanHandlerTests {

    private WavefrontSender sender;

    private WavefrontSpanHandler sut;

    @BeforeEach
    void setUp() {
        this.sender = mock(WavefrontSender.class);
        this.sut = new WavefrontSpanHandler(10000, sender, SpanMetrics.NOOP, "source",
                new ApplicationTags.Builder("application", "service").build(), Collections.emptySet());
    }

    @AfterEach
    void tearDown() {
        this.sut.close();
    }

    @Test
    void sends() throws Exception {
        TraceContext traceContext = new DummyTraceContext();
        SimpleSpan span = new SimpleSpan();
        sut.end(traceContext, span);
        sut.close();

        verify(sender).sendSpan(eq("defaultOperation"), anyLong(), anyLong(), eq("source"),
                eq(UUID.fromString("00000000-0000-0000-7fff-ffffffffffff")),
                eq(UUID.fromString("00000000-0000-0000-7fff-ffffffffffff")), any(), any(), any(), any());
    }

    @Test
    void stopsInTime() throws IOException {
        await().pollDelay(Duration.ofMillis(10)).atMost(Duration.ofMillis(100)).until(() -> {
            sut.close();
            return true;
        });

        verify(sender).flush();
        verify(sender).close();
    }

    static class DummyTraceContext implements TraceContext {

        @Override
        public String traceId() {
            return "7fffffffffffffff";
        }

        @Override
        public @Nullable String parentId() {
            return null;
        }

        @Override
        public String spanId() {
            return "7fffffffffffffff";
        }

        @Override
        public Boolean sampled() {
            return true;
        }

    }

}
