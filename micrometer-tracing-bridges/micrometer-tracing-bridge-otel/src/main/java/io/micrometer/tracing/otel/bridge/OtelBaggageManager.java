/*
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

import org.jspecify.annotations.Nullable;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;

import java.util.*;

/**
 * OpenTelemetry implementation of a {@link BaggageManager}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelBaggageManager implements BaggageManager {

    /**
     * Taken from one of the W3C OTel tests. Can't find it in a spec.
     */
    private static final String PROPAGATION_UNLIMITED = "propagation=unlimited";

    private static final BaggageEntryMetadata UNLIMITED_METADATA = BaggageEntryMetadata.create(PROPAGATION_UNLIMITED);

    private static final BaggageEntryMetadata NO_METADATA = BaggageEntryMetadata.create("");

    private final CurrentTraceContext currentTraceContext;

    /**
     * Remote field names as an array so that lookups can use
     * {@link String#equalsIgnoreCase(String)} instead of allocating lower-cased copies on
     * every call.
     */
    private final String[] remoteFieldNames;

    private final String[] tagFields;

    private final List<String> baggageFields;

    /**
     * Creates a new instance of {@link OtelBaggageManager}.
     * <p>
     * Tag field names are copied on construction. Subsequent changes to {@code tagFields}
     * do not affect tagging.
     * @param currentTraceContext current trace context
     * @param remoteFields names of baggage fields to propagate remotely
     * @param tagFields names of baggage fields to add as span tags
     */
    public OtelBaggageManager(CurrentTraceContext currentTraceContext, List<String> remoteFields,
            List<String> tagFields) {
        this.currentTraceContext = currentTraceContext;
        this.remoteFieldNames = remoteFields.toArray(new String[0]);
        this.tagFields = tagFields.toArray(new String[0]);
        this.baggageFields = baggageFields(tagFields, remoteFields);
    }

    private static List<String> baggageFields(List<String> tagFields, List<String> remoteFields) {
        Set<String> combined = new HashSet<>(tagFields);
        combined.addAll(remoteFields);
        return new ArrayList<>(combined);
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return toMap((OtelTraceContext) this.currentTraceContext.context());
    }

    @Override
    public Map<String, String> getAllBaggage(@Nullable TraceContext traceContext) {
        return traceContext == null ? getAllBaggage() : toMap((OtelTraceContext) traceContext);
    }

    private Map<String, String> toMap(@Nullable OtelTraceContext traceContext) {
        Baggage baggage = Baggage.current();
        Map<String, String> map = new HashMap<>();
        baggage.forEach((key, entry) -> map.put(key, entry.getValue()));
        if (traceContext != null) {
            Baggage retained = Baggage.fromContext(traceContext.context());
            if (retained != baggage) {
                retained.forEach((key, entry) -> map.put(key, entry.getValue()));
            }
        }
        return map;
    }

    private Map<String, BaggageEntry> currentBaggageEntries() {
        OtelTraceContext traceContext = (OtelTraceContext) this.currentTraceContext.context();
        // Preserve exact-key merging before the case-insensitive lookup.
        Map<String, BaggageEntry> entries = new HashMap<>();
        Baggage.current().forEach(entries::put);
        if (traceContext != null) {
            Baggage.fromContext(traceContext.context()).forEach(entries::put);
        }
        return entries;
    }

    @Override
    public io.micrometer.tracing.Baggage getBaggage(String name) {
        Map.Entry<String, BaggageEntry> entry = findEntry(name, currentBaggageEntries());
        if (entry == null) {
            return createBaggage(name);
        }
        return otelBaggage((OtelTraceContext) this.currentTraceContext.context(), entry);
    }

    @Override
    public io.micrometer.tracing.@Nullable Baggage getBaggage(TraceContext traceContext, String name) {
        OtelTraceContext context = (OtelTraceContext) traceContext;
        Context current = Context.current();
        Context traceContextContext = context.context();
        Map.Entry<String, BaggageEntry> entry = findEntry(name, Baggage.fromContext(traceContextContext).asMap());
        if (entry == null && !Objects.equals(current, traceContextContext)) {
            entry = findEntry(name, Baggage.fromContext(current).asMap());
        }
        if (entry != null) {
            return otelBaggage(context, entry);
        }
        return null;
    }

    @Nullable String getValue(OtelTraceContext traceContext, String name) {
        Map.Entry<String, BaggageEntry> entry = findEntry(name, Baggage.fromContext(traceContext.context()).asMap());
        return entry != null ? entry.getValue().getValue() : null;
    }

    private static Map.@Nullable Entry<String, BaggageEntry> findEntry(String name, Map<String, BaggageEntry> entries) {
        for (Map.Entry<String, BaggageEntry> entry : entries.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    private OtelBaggageInScope otelBaggage(@Nullable OtelTraceContext traceContext,
            Map.Entry<String, BaggageEntry> entry) {
        BaggageEntry value = entry.getValue();
        return new OtelBaggageInScope(this, this.currentTraceContext, traceContext, this.tagFields, entry.getKey(),
                value.getValue(), value.getMetadata());
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage createBaggage(String name) {
        return createBaggage(name, null);
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage createBaggage(String name, @Nullable String value) {
        io.micrometer.tracing.Baggage baggage = otelBaggage((OtelTraceContext) this.currentTraceContext.context(), name,
                value);
        return baggage.set(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return otelBaggage((OtelTraceContext) this.currentTraceContext.context(), name, value).makeCurrent(true);
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return otelBaggage((OtelTraceContext) traceContext, name, value).makeCurrent(true);
    }

    private OtelBaggageInScope otelBaggage(@Nullable OtelTraceContext traceContext, String name,
            @Nullable String value) {
        BaggageEntryMetadata entryMetadata = isRemoteField(name) ? UNLIMITED_METADATA : NO_METADATA;
        return new OtelBaggageInScope(this, this.currentTraceContext, traceContext, this.tagFields, name, value,
                entryMetadata);
    }

    private boolean isRemoteField(String name) {
        for (String remoteFieldName : this.remoteFieldNames) {
            if (remoteFieldName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> getBaggageFields() {
        return this.baggageFields;
    }

    static Baggage merge(Baggage base, Baggage overrides) {
        if (base.isEmpty()) {
            return overrides;
        }
        if (overrides.isEmpty()) {
            return base;
        }
        BaggageBuilder builder = base.toBuilder();
        overrides.forEach((key, entry) -> builder.put(key, entry.getValue(), entry.getMetadata()));
        return builder.build();
    }

}
