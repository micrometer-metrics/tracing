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
import java.util.function.BiConsumer;

import static java.util.Collections.unmodifiableMap;

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

    private final List<String> remoteFields;

    /**
     * {@link #remoteFields} as an array so that lookups can use
     * {@link String#equalsIgnoreCase(String)} instead of allocating lower-cased copies on
     * every call.
     */
    private final String[] remoteFieldNames;

    private final List<String> baggageFields;

    private final List<String> tagFields;

    /**
     * Creates a new instance of {@link OtelBaggageManager}.
     * @param currentTraceContext current trace context
     * @param remoteFields remote fields names
     * @param tagFields tag fields names
     */
    public OtelBaggageManager(CurrentTraceContext currentTraceContext, List<String> remoteFields,
            List<String> tagFields) {
        this.currentTraceContext = currentTraceContext;
        this.remoteFields = remoteFields;
        this.remoteFieldNames = remoteFields.toArray(new String[0]);
        this.tagFields = tagFields;
        this.baggageFields = baggageFields(tagFields, remoteFields);
    }

    private static List<String> baggageFields(List<String> tagFields, List<String> remoteFields) {
        Set<String> combined = new HashSet<>(tagFields);
        combined.addAll(remoteFields);
        return new ArrayList<>(combined);
    }

    @Override
    public Map<String, String> getAllBaggage() {
        return toMap(currentBaggage());
    }

    private Map<String, String> toMap(CompositeBaggage compositeBaggage) {
        Map<String, BaggageEntry> entries = compositeBaggage.asMap();
        Map<String, String> baggage = new HashMap<>((int) (entries.size() / 0.75f) + 1);
        for (Map.Entry<String, BaggageEntry> entry : entries.entrySet()) {
            baggage.put(entry.getKey(), entry.getValue().getValue());
        }
        return baggage;
    }

    @Override
    public Map<String, String> getAllBaggage(@Nullable TraceContext traceContext) {
        if (traceContext == null) {
            return getAllBaggage();
        }
        return toMap(baggage((OtelTraceContext) traceContext));
    }

    CompositeBaggage currentBaggage() {
        return baggage((OtelTraceContext) currentTraceContext.context());
    }

    private CompositeBaggage baggage(@Nullable OtelTraceContext traceContext) {
        Context current = Context.current();
        if (traceContext == null || traceContext.context() == null) {
            return new CompositeBaggage(current);
        }
        // entries from the trace context override the ones from the current context
        return new CompositeBaggage(current, traceContext.context());
    }

    @Override
    public io.micrometer.tracing.Baggage getBaggage(String name) {
        Entry entry = getBaggage(name, currentBaggage());
        return createNewEntryIfMissing(name, entry);
    }

    io.micrometer.tracing.Baggage createNewEntryIfMissing(String name, @Nullable Entry entry) {
        if (entry == null) {
            return createBaggage(name);
        }
        return otelBaggage(entry);
    }

    private @Nullable Entry getBaggage(String name, io.opentelemetry.api.baggage.Baggage baggage) {
        return entryForName(name, baggage);
    }

    @Override
    public io.micrometer.tracing.@Nullable Baggage getBaggage(TraceContext traceContext, String name) {
        OtelTraceContext context = (OtelTraceContext) traceContext;
        LinkedList<Context> stack = new LinkedList<>();
        Context current = Context.current();
        Context traceContextContext = context.context();
        stack.addFirst(current);
        if (!Objects.equals(current, traceContextContext)) {
            stack.addFirst(traceContextContext);
        }
        Context ctx = removeFirst(stack);
        Entry entry = null;
        while (ctx != null && entry == null) {
            entry = getBaggage(name, Baggage.fromContext(ctx));
            ctx = removeFirst(stack);
        }
        if (entry != null) {
            return otelBaggage(context, entry);
        }
        return null;
    }

    @Nullable Entry getEntry(OtelTraceContext traceContext, String name) {
        OtelTraceContext context = traceContext;
        Context ctx = context.context();
        return getBaggage(name, Baggage.fromContext(ctx));
    }

    @Nullable Context removeFirst(Deque<Context> stack) {
        return stack.isEmpty() ? null : stack.removeFirst();
    }

    private @Nullable Entry entryForName(String name, io.opentelemetry.api.baggage.Baggage baggage) {
        for (Map.Entry<String, BaggageEntry> entry : baggage.asMap().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                BaggageEntry value = entry.getValue();
                return new Entry(entry.getKey(), value.getValue(), value.getMetadata());
            }
        }
        return null;
    }

    private io.micrometer.tracing.Baggage otelBaggage(Entry entry) {
        return new OtelBaggageInScope(this, this.currentTraceContext, this.tagFields, entry);
    }

    private io.micrometer.tracing.Baggage otelBaggage(OtelTraceContext otelTraceContext, Entry entry) {
        return new OtelBaggageInScope(this, this.currentTraceContext, otelTraceContext, this.tagFields, entry);
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage createBaggage(String name) {
        return createBaggage(name, null);
    }

    @Override
    @Deprecated
    public io.micrometer.tracing.Baggage createBaggage(String name, @Nullable String value) {
        io.micrometer.tracing.Baggage baggage = baggageWithValue(name, value);
        return baggage.set(value);
    }

    @Override
    public BaggageInScope createBaggageInScope(String name, String value) {
        return baggageWithValue(name, value).makeCurrent();
    }

    @Override
    public BaggageInScope createBaggageInScope(TraceContext traceContext, String name, String value) {
        return baggageWithValue(name, value).makeCurrent(traceContext, value);
    }

    private io.micrometer.tracing.Baggage baggageWithValue(String name, @Nullable String value) {
        BaggageEntryMetadata entryMetadata = isRemoteField(name) ? UNLIMITED_METADATA : NO_METADATA;
        Entry entry = new Entry(name, value, entryMetadata);
        return new OtelBaggageInScope(this, this.currentTraceContext, this.tagFields, entry);
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
        return this.remoteFields;
    }

}

class CompositeBaggage implements io.opentelemetry.api.baggage.Baggage {

    private final Map<String, BaggageEntry> baggageEntries;

    /**
     * @param contexts contexts to merge, in increasing order of precedence - given parent
     * baggage {@code foo=bar} and child baggage {@code foo=baz}, the child wins
     */
    CompositeBaggage(Context... contexts) {
        Map<String, BaggageEntry> map = new HashMap<>();
        for (Context context : contexts) {
            Baggage.fromContext(context)
                .forEach((key, value) -> map.put(key, new Entry(key, value.getValue(), value.getMetadata())));
        }
        this.baggageEntries = unmodifiableMap(map);
    }

    @Override
    public int size() {
        return this.baggageEntries.size();
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super BaggageEntry> consumer) {
        this.baggageEntries.forEach(consumer);
    }

    @Override
    public Map<String, BaggageEntry> asMap() {
        return this.baggageEntries;
    }

    @Override
    public @Nullable String getEntryValue(String entryKey) {
        BaggageEntry entry = this.baggageEntries.get(entryKey);
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public BaggageBuilder toBuilder() {
        return Baggage.builder();
    }

}

class Entry implements BaggageEntry {

    final String key;

    final @Nullable String value;

    final BaggageEntryMetadata entryMetadata;

    Entry(String key, @Nullable String value, BaggageEntryMetadata entryMetadata) {
        this.key = key;
        this.value = value;
        this.entryMetadata = entryMetadata;
    }

    public String getKey() {
        return this.key;
    }

    @Override
    public @Nullable String getValue() {
        return this.value;
    }

    @Override
    public BaggageEntryMetadata getMetadata() {
        return this.entryMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Entry entry = (Entry) o;
        return Objects.equals(this.key, entry.key) && Objects.equals(this.value, entry.value)
                && Objects.equals(this.entryMetadata, entry.entryMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.key, this.value, this.entryMetadata);
    }

}
