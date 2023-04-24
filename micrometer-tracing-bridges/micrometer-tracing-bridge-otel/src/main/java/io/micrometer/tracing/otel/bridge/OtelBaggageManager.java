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

import io.micrometer.common.lang.Nullable;
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

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

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

    private final CurrentTraceContext currentTraceContext;

    private final List<String> remoteFields;

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
        this.tagFields = tagFields;
    }

    @Override
    public Map<String, String> getAllBaggage() {
        Map<String, String> baggage = new HashMap<>();
        currentBaggage().getEntries().forEach(entry -> baggage.put(entry.getKey(), entry.getValue()));
        return baggage;
    }

    CompositeBaggage currentBaggage() {
        OtelTraceContext traceContext = (OtelTraceContext) currentTraceContext.context();
        Context context = Context.current();
        Deque<Context> stack = new ArrayDeque<>();
        if (traceContext != null) {
            stack.addFirst(traceContext.context());
        }
        stack.addFirst(context);
        return new CompositeBaggage(stack);
    }

    @Override
    public io.micrometer.tracing.Baggage getBaggage(String name) {
        Entry entry = getBaggage(name, currentBaggage());
        return createNewEntryIfMissing(name, entry);
    }

    io.micrometer.tracing.Baggage createNewEntryIfMissing(String name, Entry entry) {
        if (entry == null) {
            return createBaggage(name);
        }
        return otelBaggage(entry);
    }

    private Entry getBaggage(String name, io.opentelemetry.api.baggage.Baggage baggage) {
        return entryForName(name, baggage);
    }

    @Override
    public io.micrometer.tracing.Baggage getBaggage(TraceContext traceContext, String name) {
        OtelTraceContext context = (OtelTraceContext) traceContext;
        Deque<Context> stack = new ArrayDeque<>();
        Context current = Context.current();
        Context traceContextContext = context.context();
        stack.addFirst(current);
        if (current != traceContextContext) {
            stack.addFirst(traceContextContext);
        }
        Context ctx = removeFirst(stack);
        Entry entry = null;
        while (ctx != null && entry == null) {
            entry = getBaggage(name, Baggage.fromContext(ctx));
            ctx = removeFirst(stack);
        }
        if (entry != null) {
            return otelBaggage(entry);
        }
        return null;
    }

    Entry getEntry(OtelTraceContext traceContext, String name) {
        OtelTraceContext context = traceContext;
        Context ctx = context.context();
        return getBaggage(name, Baggage.fromContext(ctx));
    }

    Context removeFirst(Deque<Context> stack) {
        return stack.isEmpty() ? null : stack.removeFirst();
    }

    private Entry entryForName(String name, io.opentelemetry.api.baggage.Baggage baggage) {
        return Entry.fromBaggage(baggage)
            .stream()
            .filter(e -> e.getKey().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private io.micrometer.tracing.Baggage otelBaggage(Entry entry) {
        return new OtelBaggageInScope(this, this.currentTraceContext, this.tagFields, entry);
    }

    @Override
    public io.micrometer.tracing.Baggage createBaggage(String name) {
        return createBaggage(name, null);
    }

    @Override
    public io.micrometer.tracing.Baggage createBaggage(String name, String value) {
        io.micrometer.tracing.Baggage baggage = baggageWithValue(name, null);
        return baggage.set(value);
    }

    private io.micrometer.tracing.Baggage baggageWithValue(String name, @Nullable String value) {
        boolean remoteField = this.remoteFields.stream()
            .map(String::toLowerCase)
            .anyMatch(s -> s.equals(name.toLowerCase()));
        BaggageEntryMetadata entryMetadata = BaggageEntryMetadata.create(propagationString(remoteField));
        Entry entry = new Entry(name, value, entryMetadata);
        return new OtelBaggageInScope(this, this.currentTraceContext, this.tagFields, entry);
    }

    private String propagationString(boolean remoteField) {
        String propagation = "";
        if (remoteField) {
            propagation = PROPAGATION_UNLIMITED;
        }
        return propagation;
    }

}

class CompositeBaggage implements io.opentelemetry.api.baggage.Baggage {

    private final Collection<Entry> entries;

    private final Map<String, BaggageEntry> baggageEntries;

    CompositeBaggage(Deque<Context> stack) {
        this.entries = unmodifiableCollection(createEntries(stack));
        this.baggageEntries = unmodifiableMap(this.entries.stream().collect(toMap(Entry::getKey, identity())));
    }

    private Collection<Entry> createEntries(Deque<Context> stack) {
        // parent baggage foo=bar
        // child baggage foo=baz - we want the last one to override the previous one
        Map<String, Entry> map = new HashMap<>();
        Iterator<Context> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            Context next = iterator.next();
            Baggage baggage = Baggage.fromContext(next);
            baggage.forEach((key, value) -> map.put(key, new Entry(key, value.getValue(), value.getMetadata())));
        }

        return map.values();
    }

    Collection<Entry> getEntries() {
        return this.entries;
    }

    @Override
    public int size() {
        return this.entries.size();
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super BaggageEntry> consumer) {
        this.entries.forEach(entry -> consumer.accept(entry.getKey(), entry));
    }

    @Override
    public Map<String, BaggageEntry> asMap() {
        return this.baggageEntries;
    }

    @Override
    public String getEntryValue(String entryKey) {
        return this.entries.stream()
            .filter(entry -> entryKey.equals(entry.getKey()))
            .map(Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    @Override
    public BaggageBuilder toBuilder() {
        return Baggage.builder();
    }

}

class Entry implements BaggageEntry {

    final String key;

    final String value;

    final BaggageEntryMetadata entryMetadata;

    Entry(String key, String value, BaggageEntryMetadata entryMetadata) {
        this.key = key;
        this.value = value;
        this.entryMetadata = entryMetadata;
    }

    static List<Entry> fromBaggage(Baggage baggage) {
        List<Entry> list = new ArrayList<>(baggage.size());
        baggage.forEach((key, value) -> list.add(new Entry(key, value.getValue(), value.getMetadata())));
        return list;
    }

    public String getKey() {
        return this.key;
    }

    @Override
    public String getValue() {
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
