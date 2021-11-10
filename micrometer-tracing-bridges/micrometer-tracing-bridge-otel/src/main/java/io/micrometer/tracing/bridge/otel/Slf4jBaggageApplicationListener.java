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

import java.util.List;
import java.util.stream.Collectors;

import io.opentelemetry.api.baggage.Baggage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class Slf4jBaggageApplicationListener implements ApplicationListener<ApplicationEvent> {

	private static final Log log = LogFactory.getLog(Slf4jBaggageApplicationListener.class);

	private final List<String> lowerCaseCorrelationFields;

	private final List<String> correlationFields;

	public Slf4jBaggageApplicationListener(List<String> correlationFields) {
		this.lowerCaseCorrelationFields = correlationFields.stream().map(String::toLowerCase)
				.collect(Collectors.toList());
		this.correlationFields = correlationFields;
	}

	private void onScopeAttached(EventPublishingContextWrapper.ScopeAttachedEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope attached event [" + event + "]");
		}
		if (event.getBaggage() != null) {
			putEntriesIntoMdc(event.getBaggage());
		}
	}

	private void onScopeRestored(EventPublishingContextWrapper.ScopeRestoredEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope restored event [" + event + "]");
		}
		if (event.getBaggage() != null) {
			putEntriesIntoMdc(event.getBaggage());
		}
	}

	private void putEntriesIntoMdc(Baggage baggage) {
		baggage.forEach((key, baggageEntry) -> {
			if (lowerCaseCorrelationFields.contains(key.toLowerCase())) {
				MDC.put(key, baggageEntry.getValue());
			}
		});
	}

	private void onScopeClosed(EventPublishingContextWrapper.ScopeClosedEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope closed event [" + event + "]");
		}
		correlationFields.forEach(MDC::remove);
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
