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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;

import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.BaggageManager;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * OpenTelemetry implementation of a {@link BaggageManager}. Doesn't implement an
 * interface cause {@link Tracer} already implements it.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelBaggageManager implements BaggageManager {

	private final CurrentTraceContext currentTraceContext;

	private final List<String> remoteFields;

	private final List<String> tagFields;

	private final ApplicationEventPublisher publisher;

	public OtelBaggageManager(CurrentTraceContext currentTraceContext, List<String> remoteFields,
			List<String> tagFields, ApplicationEventPublisher publisher) {
		this.currentTraceContext = currentTraceContext;
		this.remoteFields = remoteFields;
		this.tagFields = tagFields;
		this.publisher = publisher;
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
	public BaggageInScope getBaggage(String name) {
		Entry entry = getBaggage(name, currentBaggage());
		return createNewEntryIfMissing(name, entry);
	}

	BaggageInScope createNewEntryIfMissing(String name, Entry entry) {
		if (entry == null) {
			return createBaggage(name);
		}
		return otelBaggage(entry);
	}

	private Entry getBaggage(String name, io.opentelemetry.api.baggage.Baggage baggage) {
		return entryForName(name, baggage);
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		OtelTraceContext context = (OtelTraceContext) traceContext;
		// TODO: Refactor
		Deque<Context> stack = new ArrayDeque<>();
		stack.addFirst(Context.current());
		stack.addFirst(context.context());
		Context ctx = removeFirst(stack);
		Entry entry = null;
		while (ctx != null && entry == null) {
			entry = getBaggage(name, Baggage.fromContext(ctx));
			ctx = removeFirst(stack);
		}
		return createNewEntryIfMissing(name, entry);
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
		return Entry.fromBaggage(baggage).stream().filter(e -> e.getKey().toLowerCase().equals(name.toLowerCase()))
				.findFirst().orElse(null);
	}

	private BaggageInScope otelBaggage(Entry entry) {
		return new OtelBaggageInScope(this, this.currentTraceContext, this.tagFields, entry);
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return createBaggage(name, "");
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		BaggageInScope baggageInScope = baggageWithValue(name, "");
		return baggageInScope.set(value);
	}

	private BaggageInScope baggageWithValue(String name, String value) {
		List<String> remoteFieldsFields = this.remoteFields;
		boolean remoteField = remoteFieldsFields.stream().map(String::toLowerCase)
				.anyMatch(s -> s.equals(name.toLowerCase()));
		BaggageEntryMetadata entryMetadata = BaggageEntryMetadata.create(propagationString(remoteField));
		Entry entry = new Entry(name, value, entryMetadata);
		return new OtelBaggageInScope(this, this.currentTraceContext, this.tagFields, entry);
	}

	private String propagationString(boolean remoteField) {
		// TODO: [OTEL] Magic strings
		String propagation = "";
		if (remoteField) {
			propagation = "propagation=unlimited";
		}
		return propagation;
	}

}

class CompositeBaggage implements io.opentelemetry.api.baggage.Baggage {

	// TODO: Try to use a Map of BaggageEntry only: delete the Entry class
	// (might need a bigger refactor)
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
		return this.entries.stream().filter(entry -> entryKey.equals(entry.getKey())).map(Entry::getValue).findFirst()
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

	static List<Entry> fromBaggage(Baggage baggage) {
		List<Entry> list = new ArrayList<>(baggage.size());
		baggage.forEach((key, value) -> list.add(new Entry(key, value.getValue(), value.getMetadata())));
		return list;
	}

}
