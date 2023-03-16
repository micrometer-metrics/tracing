/**
 * Copyright 2023 the original author or authors.
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

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.common.util.StringUtils;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.internal.EncodingUtils;

class LinkUtils {

    private static final String LINKS_PREFIX = "links[";

    private static final String TRACE_ID = "links[%s].traceId";

    private static final String SPAN_ID = "links[%s].spanId";

    private static final String TAG = "links[%s].tags[%s]";

    private static final Pattern LINKS_ID = Pattern.compile("^links\\[(.*)]\\..*$");

    private static final Pattern TAG_KEY = Pattern.compile("^links\\[.*]\\.tags\\[(.*)]$");

    static boolean isApplicable(Map.Entry<String, String> entry) {
        return entry.getKey().startsWith(LINKS_PREFIX);
    }

    static int linkGroup(Map.Entry<String, String> entry) {
        Matcher matcher = LINKS_ID.matcher(entry.getKey());
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    static String traceIdKey(long index) {
        return String.format(TRACE_ID, index);
    }

    static String spanIdKey(long index) {
        return String.format(SPAN_ID, index);
    }

    static String tagKey(long index, String tagKey) {
        return String.format(TAG, index, tagKey);
    }

    static int nextIndex(Map<String, String> tags) {
        return tags.entrySet()
            .stream()
            .filter(LinkUtils::isApplicable)
            .collect(Collectors.groupingBy(LinkUtils::linkGroup))
            .keySet()
            .size();
    }

    static Map.Entry<TraceContext, Map<String, String>> toEntry(List<Map.Entry<String, String>> groupedTags) {
        String traceId = "";
        String spanId = "";
        Map<String, String> tags = new HashMap<>();
        for (Map.Entry<String, String> groupedTag : groupedTags) {
            if (groupedTag.getKey().endsWith(".traceId")) {
                traceId = groupedTag.getValue();
            }
            else if (groupedTag.getKey().endsWith(".spanId")) {
                spanId = groupedTag.getValue();
            }
            else if (groupedTag.getKey().contains("tags")) {
                String tagKey = tagKeyNameFromString(groupedTag.getKey());
                if (tagKey != null) {
                    tags.put(tagKey, groupedTag.getValue());
                }
            }
        }
        if (StringUtils.isNotBlank(traceId)) {
            brave.propagation.TraceContext.Builder newBuilder = traceId(brave.propagation.TraceContext.newBuilder(),
                    traceId);
            TraceContext traceContext = new BraveTraceContext(newBuilder.spanId(spanId(spanId)).build());
            return new AbstractMap.SimpleEntry<>(traceContext, tags);
        }
        return null;
    }

    static brave.propagation.TraceContext.Builder traceId(brave.propagation.TraceContext.Builder delegate,
            String traceId) {
        long[] fromString = EncodingUtils.fromString(traceId);
        if (fromString.length == 2) {
            delegate.traceIdHigh(fromString[0]);
            delegate.traceId(fromString[1]);
        }
        else {
            delegate.traceId(fromString[0]);
        }
        return delegate;
    }

    static long spanId(String spanId) {
        long[] fromString = EncodingUtils.fromString(spanId);
        return fromString[fromString.length == 2 ? 1 : 0];
    }

    static String tagKeyNameFromString(String tag) {
        Matcher matcher = TAG_KEY.matcher(tag);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

}
