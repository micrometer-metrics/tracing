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
package io.micrometer.tracing.otel.bridge;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.opentelemetry.api.trace.Span;
import org.slf4j.MDC;

/**
 * Reacts to events with updating of Slf4j's {@link MDC}.
 *
 * @since 1.0.0
 */
public class Slf4JEventListener implements EventListener {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(Slf4JEventListener.class);

    private void onScopeAttached(EventPublishingContextWrapper.ScopeAttachedEvent event) {
        log.trace("Got scope changed event [{}]", event);
        Span span = event.getSpan();
        if (span != null) {
            MDC.put("traceId", span.getSpanContext().getTraceId());
            MDC.put("spanId", span.getSpanContext().getSpanId());
        }
    }

    private void onScopeRestored(EventPublishingContextWrapper.ScopeRestoredEvent event) {
        log.trace("Got scope restored event [{}]", event);
        Span span = event.getSpan();
        if (span != null) {
            MDC.put("traceId", span.getSpanContext().getTraceId());
            MDC.put("spanId", span.getSpanContext().getSpanId());
        }
    }

    private void onScopeClosed(EventPublishingContextWrapper.ScopeClosedEvent event) {
        log.trace("Got scope closed event [{}]", event);
        MDC.remove("traceId");
        MDC.remove("spanId");
    }

    @Override
    public void onEvent(Object event) {
        if (event instanceof EventPublishingContextWrapper.ScopeAttachedEvent) {
            onScopeAttached((EventPublishingContextWrapper.ScopeAttachedEvent) event);
        }
        else if (event instanceof EventPublishingContextWrapper.ScopeClosedEvent) {
            onScopeClosed((EventPublishingContextWrapper.ScopeClosedEvent) event);
        }
        else if (event instanceof EventPublishingContextWrapper.ScopeRestoredEvent) {
            onScopeRestored((EventPublishingContextWrapper.ScopeRestoredEvent) event);
        }
    }

}
