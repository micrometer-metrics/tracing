/*
 * Copyright 2013-2020 the original author or authors.
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

package io.micrometer.tracing.otel.bridge;

import java.util.Arrays;
import java.util.Collections;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class BaggageTaggingSpanProcessorTest {

    @Test
    void interfaceMethods() {
        BaggageTaggingSpanProcessor spanProcessor = new BaggageTaggingSpanProcessor(Collections.emptyList());
        assertThat(spanProcessor.isEndRequired()).isFalse();
        assertThat(spanProcessor.isStartRequired()).isTrue();
    }

    @Test
    void onStart_emptyBaggage() {
        BaggageTaggingSpanProcessor spanProcessor = new BaggageTaggingSpanProcessor(Arrays.asList("tagOne", "tagTwo"));

        Baggage baggage = Baggage.builder().build();
        ReadWriteSpan span = mock(ReadWriteSpan.class);

        spanProcessor.onStart(Context.root().with(baggage), span);
        verifyNoInteractions(span);
    }

    @Test
    void onStart_withBaggage() {
        BaggageTaggingSpanProcessor spanProcessor = new BaggageTaggingSpanProcessor(Arrays.asList("tagOne", "tagTwo"));

        Baggage baggage = Baggage.builder().put("tagOne", "valueOne").put("tagTwo", "valueTwo")
                .put("otherTag", "otherValue").build();
        ReadWriteSpan span = mock(ReadWriteSpan.class);

        spanProcessor.onStart(Context.root().with(baggage), span);
        verify(span).setAttribute(stringKey("tagOne"), "valueOne");
        verify(span).setAttribute(stringKey("tagTwo"), "valueTwo");

        verifyNoMoreInteractions(span);
    }

}
