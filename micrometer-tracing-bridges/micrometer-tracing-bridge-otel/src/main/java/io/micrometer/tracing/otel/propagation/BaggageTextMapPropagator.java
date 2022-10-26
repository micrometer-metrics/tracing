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
package io.micrometer.tracing.otel.propagation;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.BaggageManager;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link TextMapPropagator} that adds compatible baggage entries (name of the field means
 * an HTTP header entry). If existing baggage is present in the context, this will append
 * entries to the existing one. Preferably this {@link TextMapPropagator} should be added
 * as last.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class BaggageTextMapPropagator implements TextMapPropagator {

    /**
     * Taken from one of the W3C OTel tests. Can't find it in a spec.
     */
    private static final String PROPAGATION_UNLIMITED = "propagation=unlimited";

    private static final InternalLogger log = InternalLoggerFactory.getInstance(BaggageTextMapPropagator.class);

    private final List<String> remoteFields;

    private final BaggageManager baggageManager;

    /**
     * Creates a new instance of {@link BaggageTextMapPropagator}.
     * @param remoteFields remote fields
     * @param baggageManager baggage manager
     */
    public BaggageTextMapPropagator(List<String> remoteFields, BaggageManager baggageManager) {
        this.remoteFields = remoteFields;
        this.baggageManager = baggageManager;
    }

    @Override
    public List<String> fields() {
        return this.remoteFields;
    }

    @Override
    public <C> void inject(Context context, C c, TextMapSetter<C> setter) {
        List<Map.Entry<String, String>> baggageEntries = applicableBaggageEntries(c);
        baggageEntries.forEach(e -> setter.set(c, e.getKey(), e.getValue()));
    }

    private <C> List<Map.Entry<String, String>> applicableBaggageEntries(C c) {
        Map<String, String> allBaggage = this.baggageManager.getAllBaggage();
        List<String> lowerCaseKeys = this.remoteFields.stream().map(String::toLowerCase).collect(Collectors.toList());
        return allBaggage.entrySet().stream().filter(e -> lowerCaseKeys.contains(e.getKey().toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public <C> Context extract(Context context, C c, TextMapGetter<C> getter) {
        Map<String, String> baggageEntries = this.remoteFields.stream()
                .map(s -> new AbstractMap.SimpleEntry<>(s, getter.get(c, s))).filter(e -> e.getValue() != null)
                .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()));
        BaggageBuilder builder = Baggage.current().toBuilder();
        baggageEntries
                .forEach((key, value) -> builder.put(key, value, BaggageEntryMetadata.create(PROPAGATION_UNLIMITED)));
        Baggage.fromContext(context)
                .forEach((s, baggageEntry) -> builder.put(s, baggageEntry.getValue(), baggageEntry.getMetadata()));
        Baggage baggage = builder.build();
        Context withBaggage = context.with(baggage);
        if (log.isDebugEnabled()) {
            log.debug("Will propagate new baggage context for entries " + baggageEntries);
        }
        return withBaggage;
    }

}
