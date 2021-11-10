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
import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.stream.Collectors.toMap;

public class BaggageTaggingSpanProcessor implements SpanProcessor {

	private final Map<String, AttributeKey<String>> tagsToApply;

	public BaggageTaggingSpanProcessor(List<String> tagsToApply) {
		this.tagsToApply = tagsToApply.stream().map(tag -> stringKey(tag))
				.collect(toMap(AttributeKey::getKey, key -> key));
	}

	@Override
	public void onStart(Context context, ReadWriteSpan readWriteSpan) {
		Baggage baggage = Baggage.fromContext(context);

		baggage.forEach((key, baggageEntry) -> {
			AttributeKey<String> attributeKey = tagsToApply.get(key);
			if (attributeKey != null) {
				readWriteSpan.setAttribute(attributeKey, baggageEntry.getValue());
			}
		});
	}

	@Override
	public boolean isStartRequired() {
		return true;
	}

	@Override
	public void onEnd(ReadableSpan readableSpan) {
		// no-op
	}

	@Override
	public boolean isEndRequired() {
		return false;
	}

}
