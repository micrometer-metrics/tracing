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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final BaggageEntryMetadata PROPAGATION_UNLIMITED_METADATA = BaggageEntryMetadata
        .create(PROPAGATION_UNLIMITED);

    private static final InternalLogger log = InternalLoggerFactory.getInstance(BaggageTextMapPropagator.class);

    private final List<String> remoteFields;

    /**
     * {@link #remoteFields} as an array so that the hot paths can iterate it without
     * allocating an iterator, and can match names with
     * {@link String#equalsIgnoreCase(String)} instead of allocating lower-cased copies on
     * every call.
     */
    private final String[] remoteFieldNames;

    private final BaggageManager baggageManager;

    /**
     * Creates a new instance of {@link BaggageTextMapPropagator}.
     * @param remoteFields remote fields
     * @param baggageManager baggage manager
     */
    public BaggageTextMapPropagator(List<String> remoteFields, BaggageManager baggageManager) {
        this.remoteFields = Collections.unmodifiableList(new ArrayList<>(remoteFields));
        this.remoteFieldNames = this.remoteFields.toArray(new String[0]);
        this.baggageManager = baggageManager;
    }

    @Override
    public List<String> fields() {
        return this.remoteFields;
    }

    @Override
    public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
        if (this.remoteFieldNames.length == 0) {
            return;
        }
        Map<String, String> allBaggage = this.baggageManager.getAllBaggage();
        for (Map.Entry<String, String> entry : allBaggage.entrySet()) {
            String key = entry.getKey();
            // the baggage key casing wins over the configured remote field casing
            if (isRemoteField(key)) {
                setter.set(carrier, key, entry.getValue());
            }
        }
    }

    private boolean isRemoteField(String key) {
        for (String remoteFieldName : this.remoteFieldNames) {
            if (remoteFieldName.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
        // both are only materialized once there is something to propagate
        BaggageBuilder newBaggage = null;
        Map<String, String> debugEntries = null;

        for (String remoteFieldName : this.remoteFieldNames) {
            String value = getter.get(carrier, remoteFieldName);
            if (value == null) {
                continue;
            }
            if (newBaggage == null) {
                newBaggage = Baggage.fromContext(context).toBuilder();
                debugEntries = log.isDebugEnabled() ? new LinkedHashMap<>() : null;
            }
            newBaggage.put(remoteFieldName, value, PROPAGATION_UNLIMITED_METADATA);
            if (debugEntries != null) {
                debugEntries.put(remoteFieldName, value);
            }
        }

        if (newBaggage == null) {
            // nothing to propagate, leave the context untouched
            return context;
        }

        if (debugEntries != null) {
            log.debug("Will propagate new baggage context for entries " + debugEntries);
        }

        return context.with(newBaggage.build());
    }

}
