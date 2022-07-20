/*
 * Copyright 2013-2021 the original author or authors.
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

package io.micrometer.tracing.brave.bridge;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.aws.AWSPropagation;
import io.micrometer.tracing.brave.propagation.PropagationType;

@SuppressWarnings({ "unchecked", "deprecation" })
class CompositePropagationFactory extends Propagation.Factory implements Propagation<String> {

	private final Map<PropagationType, Map.Entry<Propagation.Factory, Propagation<String>>> mapping = new HashMap<>();

	private final List<PropagationType> types;

	CompositePropagationFactory(Supplier<Factory> factorySupplier, BraveBaggageManager braveBaggageManager,
			List<String> localFields, List<io.micrometer.tracing.brave.propagation.PropagationType> types) {
		this.types = types;
		this.mapping.put(PropagationType.AWS,
				new AbstractMap.SimpleEntry<>(AWSPropagation.FACTORY, AWSPropagation.FACTORY.get()));
		// Note: Versions <2.2.3 use injectFormat(MULTI) for non-remote (ex
		// spring-messaging)
		// See #1643
		Factory b3Factory = b3Factory();
		this.mapping.put(PropagationType.B3, new AbstractMap.SimpleEntry<>(b3Factory, b3Factory.get()));
		W3CPropagation w3CPropagation = new W3CPropagation(braveBaggageManager, localFields);
		this.mapping.put(io.micrometer.tracing.brave.propagation.PropagationType.W3C,
				new AbstractMap.SimpleEntry<>(w3CPropagation, w3CPropagation.get()));
		LazyPropagationFactory lazyPropagationFactory = new LazyPropagationFactory(factorySupplier);
		this.mapping.put(io.micrometer.tracing.brave.propagation.PropagationType.CUSTOM,
				new AbstractMap.SimpleEntry<>(lazyPropagationFactory, lazyPropagationFactory.get()));
	}

	private Factory b3Factory() {
		return B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build();
	}

	@Override
	public List<String> keys() {
		return this.types.stream().map(this.mapping::get).flatMap(p -> p.getValue().keys().stream())
				.collect(Collectors.toList());
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {
			this.types.stream().map(this.mapping::get)
					.forEach(p -> p.getValue().injector(setter).inject(traceContext, request));
		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		return request -> {
			for (PropagationType type : this.types) {
				Map.Entry<Factory, Propagation<String>> entry = this.mapping.get(type);
				if (entry == null) {
					continue;
				}
				Propagation<String> propagator = entry.getValue();
				if (propagator == null || propagator == NoOpPropagation.INSTANCE) {
					continue;
				}
				TraceContextOrSamplingFlags extract = propagator.extractor(getter).extract(request);
				if (extract != TraceContextOrSamplingFlags.EMPTY) {
					return extract;
				}
			}
			return TraceContextOrSamplingFlags.EMPTY;
		};
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

	@Override
	public boolean supportsJoin() {
		return this.types.stream().map(this.mapping::get).allMatch(e -> e.getKey().supportsJoin());
	}

	@Override
	public boolean requires128BitTraceId() {
		return this.types.stream().map(this.mapping::get).allMatch(e -> e.getKey().requires128BitTraceId());
	}

	@Override
	public TraceContext decorate(TraceContext context) {
		for (PropagationType type : this.types) {
			Map.Entry<Factory, Propagation<String>> entry = this.mapping.get(type);
			if (entry == null) {
				continue;
			}
			TraceContext decorate = entry.getKey().decorate(context);
			if (decorate != context) {
				return decorate;
			}
		}
		return super.decorate(context);
	}

	@SuppressWarnings("unchecked")
	private static final class LazyPropagationFactory extends Propagation.Factory {

		private final Supplier<Factory> delegate;

		private volatile Propagation.Factory propagationFactory;

		private LazyPropagationFactory(Supplier<Factory> delegate) {
			this.delegate = delegate;
		}

		private Propagation.Factory propagationFactory() {
			if (this.propagationFactory == null) {
				Factory supplier = this.delegate.get();
				this.propagationFactory = supplier != null ? supplier : NoOpPropagation.INSTANCE;
			}
			return this.propagationFactory;
		}

		@Override
		public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
			return propagationFactory().create(keyFactory);
		}

		@Override
		public boolean supportsJoin() {
			return propagationFactory().supportsJoin();
		}

		@Override
		public boolean requires128BitTraceId() {
			return propagationFactory().requires128BitTraceId();
		}

		@Override
		public Propagation<String> get() {
			return new LazyPropagation(this);
		}

		@Override
		public TraceContext decorate(TraceContext context) {
			return propagationFactory().decorate(context);
		}

	}

	@SuppressWarnings("unchecked")
	private static final class LazyPropagation implements Propagation<String> {

		private final LazyPropagationFactory delegate;

		private volatile Propagation<String> propagation;

		private LazyPropagation(LazyPropagationFactory delegate) {
			this.delegate = delegate;
		}

		private Propagation<String> propagation() {
			if (this.propagation == null) {
				this.propagation = this.delegate.propagationFactory().get();
			}
			return this.propagation;
		}

		@Override
		public List<String> keys() {
			return propagation().keys();
		}

		@Override
		public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
			return propagation().injector(setter);
		}

		@Override
		public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
			return propagation().extractor(getter);
		}

	}

	private static class NoOpPropagation extends Propagation.Factory implements Propagation<String> {

		static final NoOpPropagation INSTANCE = new NoOpPropagation();

		@Override
		public List<String> keys() {
			return Collections.emptyList();
		}

		@Override
		public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
			return (traceContext, request) -> {

			};
		}

		@Override
		public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
			return request -> TraceContextOrSamplingFlags.EMPTY;
		}

		@Override
		public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
			return StringPropagationAdapter.create(this, keyFactory);
		}

	}

}
