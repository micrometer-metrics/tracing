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

package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.api.trace.Span;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class Slf4jApplicationListener implements ApplicationListener<ApplicationEvent> {

	private static final Log log = LogFactory.getLog(Slf4jApplicationListener.class);

	private void onScopeAttached(EventPublishingContextWrapper.ScopeAttachedEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope changed event [" + event + "]");
		}
		Span span = event.getSpan();
		if (span != null) {
			MDC.put("traceId", span.getSpanContext().getTraceId());
			MDC.put("spanId", span.getSpanContext().getSpanId());
		}
	}

	private void onScopeRestored(EventPublishingContextWrapper.ScopeRestoredEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope restored event [" + event + "]");
		}
		Span span = event.getSpan();
		if (span != null) {
			MDC.put("traceId", span.getSpanContext().getTraceId());
			MDC.put("spanId", span.getSpanContext().getSpanId());
		}
	}

	private void onScopeClosed(EventPublishingContextWrapper.ScopeClosedEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope closed event [" + event + "]");
		}
		MDC.remove("traceId");
		MDC.remove("spanId");
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
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
