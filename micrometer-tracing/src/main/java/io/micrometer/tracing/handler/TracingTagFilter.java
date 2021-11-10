/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.boot.autoconfigure.observability.tracing.listener;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.tracing.Span;

class TracingTagFilter {

	void tagSpan(Span span, Iterable<Tag> tags) {
		// TODO: This is the default behaviour in Boot
		tags.forEach(tag -> {
			if (tag.getKey().equalsIgnoreCase("error")
					&& tag.getValue().equalsIgnoreCase("none")) {
				return;
			}
			span.tag(tag.getKey(), tag.getValue());
		});
	}

}
