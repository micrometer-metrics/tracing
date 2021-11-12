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

package io.micrometer.tracing.brave.sampler;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import brave.sampler.Sampler;

/**
 * This sampler is appropriate for low-traffic instrumentation (ex servers that each
 * receive less than 100K requests), or those who do not provision random trace ids. It
 * not appropriate for collectors as the sampling decision isn't idempotent (consistent
 * based on trace id).
 *
 * Implementation
 *
 * <p>
 * Taken from CountingTraceIdSampler class from Zipkin project.
 * </p>
 *
 * This counts to see how many out of 100 traces should be retained. This means that it is
 * accurate in units of 100 traces.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 * @since 1.0.0
 */
public class ProbabilityBasedSampler extends Sampler {

    private final AtomicInteger counter = new AtomicInteger(0);

    private final BitSet sampleDecisions;

    private final Supplier<Float> probability;

    /**
     * @param probability supplier of probability
     */
    public ProbabilityBasedSampler(Supplier<Float> probability) {
        if (probability == null) {
            throw new IllegalArgumentException("probability property is required for ProbabilityBasedSampler");
        }
        this.probability = probability;
        int outOf100 = (int) (probability.get() * 100.0f);
        this.sampleDecisions = randomBitSet(100, outOf100, new Random());
    }

    /**
     * Reservoir sampling algorithm borrowed from Stack Overflow.
     *
     * https://stackoverflow.com/questions/12817946/generate-a-random-bitset-with-n-1s
     * @param size size of the bit set
     * @param cardinality cardinality of the bit set
     * @param rnd random generator
     * @return a random bitset
     */
    static BitSet randomBitSet(int size, int cardinality, Random rnd) {
        BitSet result = new BitSet(size);
        int[] chosen = new int[cardinality];
        int i;
        for (i = 0; i < cardinality; ++i) {
            chosen[i] = i;
            result.set(i);
        }
        for (; i < size; ++i) {
            int j = rnd.nextInt(i + 1);
            if (j < cardinality) {
                result.clear(chosen[j]);
                result.set(i);
                chosen[j] = i;
            }
        }
        return result;
    }

    @Override
    public boolean isSampled(long traceId) {
        if (this.probability.get() == 0) {
            return false;
        }
        else if (this.probability.get() == 1.0f) {
            return true;
        }
        synchronized (this) {
            final int i = this.counter.getAndIncrement();
            boolean result = this.sampleDecisions.get(i);
            if (i == 99) {
                this.counter.set(0);
            }
            return result;
        }
    }

}
