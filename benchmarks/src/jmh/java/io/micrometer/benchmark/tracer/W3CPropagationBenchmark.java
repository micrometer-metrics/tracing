/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.benchmark.tracer;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.W3CPropagation;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class W3CPropagationBenchmark {

    private W3CPropagation propagation;

    private TraceContext context;

    private Propagation.Setter<Map<String, String>, String> setter;

    private Propagation.Getter<Map<String, String>, String> getter;

    private Map<String, String> extractCarrier;

    private Map<String, String> extractOversizedCarrier;

    @Setup
    public void setup() {
        BraveBaggageManager baggageManager = new BraveBaggageManager();
        List<String> localFields = Collections.emptyList();
        this.propagation = new W3CPropagation(baggageManager, localFields);

        // Set up TraceContext with some baggage fields
        BaggageField field1 = BaggageField.create("key1");
        BaggageField field2 = BaggageField.create("key2");
        BaggagePropagation.FactoryBuilder factoryBuilder = BaggagePropagation.newFactoryBuilder(this.propagation);
        factoryBuilder.add(BaggagePropagationConfig.SingleBaggageField.remote(field1));
        factoryBuilder.add(BaggagePropagationConfig.SingleBaggageField.remote(field2));
        Propagation.Factory factory = factoryBuilder.build();

        TraceContext.Builder contextBuilder = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true);
        // We decorate the context with baggage fields so BaggageFields extra is present
        TraceContext decoratedContext = factory.decorate(contextBuilder.build());
        field1.updateValue(decoratedContext, "value1");
        field2.updateValue(decoratedContext, "value2,with=delimiters;and spaces");

        this.context = decoratedContext;
        this.setter = Map::put;
        this.getter = Map::get;

        // Pre-allocate and pre-populate carriers for extraction to isolate parsing
        // performance.
        // We MUST include a valid traceparent header, otherwise Brave's extractor
        // short-circuits
        // and returns EMPTY immediately without ever parsing the baggage header.
        this.extractCarrier = new HashMap<>();
        this.extractCarrier.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        this.extractCarrier.put("baggage", "key1=value1,key2=value2%2Cwith%3Ddelimiters%3Band%20spaces");

        this.extractOversizedCarrier = new HashMap<>();
        this.extractOversizedCarrier.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("k").append(i).append("=").append("v").append(i).append(",");
        }
        sb.setLength(sb.length() - 1);
        this.extractOversizedCarrier.put("baggage", sb.toString());
    }

    @Benchmark
    public Map<String, String> inject() {
        Map<String, String> localCarrier = new HashMap<>();
        this.propagation.injector(this.setter).inject(this.context, localCarrier);
        return localCarrier;
    }

    @Benchmark
    public TraceContextOrSamplingFlags extract() {
        return this.propagation.extractor(this.getter).extract(this.extractCarrier);
    }

    @Benchmark
    public TraceContextOrSamplingFlags extractOversized() {
        return this.propagation.extractor(this.getter).extract(this.extractOversizedCarrier);
    }

}
