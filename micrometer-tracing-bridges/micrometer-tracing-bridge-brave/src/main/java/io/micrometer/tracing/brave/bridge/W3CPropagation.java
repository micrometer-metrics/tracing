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
package io.micrometer.tracing.brave.bridge;

import brave.Span;
import brave.Tracer;
import brave.internal.baggage.BaggageFields;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.tracecontext.TraceparentFormat;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;

import java.util.*;

import static brave.propagation.tracecontext.TraceContextPropagation.TRACEPARENT;
import static brave.propagation.tracecontext.TraceContextPropagation.TRACESTATE;
import static java.util.Collections.singletonList;

/**
 * Adopted from OpenTelemetry API.
 * <p>
 * Implementation of the TraceContext propagation protocol. See <a
 * href=https://github.com/w3c/distributed-tracing>w3c/distributed-tracing</a>.
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings({ "unchecked", "deprecation" })
public class W3CPropagation extends Propagation.Factory implements Propagation<String> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(W3CPropagation.class.getName());

    private static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList(TRACEPARENT, TRACESTATE));

    @Nullable
    private final W3CBaggagePropagator baggagePropagator;

    @Nullable
    private final BaggageManager braveBaggageManager;

    /**
     * Creates an instance of {@link W3CPropagation} with baggage support.
     * @param baggageManager baggage manager
     * @param localFields local fields to be registered as baggage
     */
    public W3CPropagation(BaggageManager baggageManager, List<String> localFields) {
        this.baggagePropagator = new W3CBaggagePropagator(baggageManager, localFields);
        this.braveBaggageManager = baggageManager;
    }

    /**
     * Creates an instance of {@link W3CPropagation} without baggage support.
     */
    public W3CPropagation() {
        this.baggagePropagator = null;
        this.braveBaggageManager = null;
    }

    @Override
    public Propagation<String> get() {
        return this;
    }

    @Override
    public List<String> keys() {
        return FIELDS;
    }

    @Override
    public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
        return (context, carrier) -> {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(setter, "setter");
            setter.put(carrier, TRACEPARENT, TraceparentFormat.get().write(context));
            addTraceState(setter, context, carrier);
            if (this.baggagePropagator != null) {
                this.baggagePropagator.injector(setter).inject(context, carrier);
            }
        };
    }

    private <R> void addTraceState(Setter<R, String> setter, TraceContext context, R carrier) {
        if (carrier != null && this.braveBaggageManager != null) {
            Baggage baggage = this.braveBaggageManager.getBaggage(BraveTraceContext.fromBrave(context), TRACESTATE);
            if (baggage == null) {
                return;
            }
            String traceState = baggage.get(BraveTraceContext.fromBrave(context));
            if (StringUtils.isNotBlank(traceState)) {
                setter.put(carrier, TRACESTATE, traceState);
            }
        }
    }

    /**
     * <strong>This does not set the shared flag when extracting headers</strong>
     *
     * <p>
     * {@link brave.propagation.TraceContext#shared()} is not set here because it is not a
     * remote propagation field. {@code shared} is a field in the Zipkin JSON v2 format
     * only set <em>after</em> header extraction, for {@link Span.Kind#SERVER} spans
     * implicitly via {@link brave.Tracer#joinSpan(TraceContext)}.
     *
     * <p>
     * Blindly setting {@code shared} regardless of this is harmful when
     * {@link Tracer#currentSpan()} or similar are used, as any data tagged with these
     * could also set the shared flag when reporting. Particularly, this can cause
     * problems for multi- {@linkplain Span.Kind#CONSUMER} spans. Regardless, setting
     * invalid flags add overhead.
     *
     * <p>
     * In summary, while {@code shared} is propagated in-process, it has never been
     * propagated out of process, and so should never be set when extracting headers.
     * Hence, this code will not set {@link brave.propagation.TraceContext#shared()}.
     */
    @Override
    public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
        Objects.requireNonNull(getter, "getter");
        return carrier -> {
            String traceParent = getter.get(carrier, TRACEPARENT);
            if (traceParent == null) {
                return withBaggage(TraceContextOrSamplingFlags.EMPTY, carrier, getter);
            }
            TraceContext contextFromParentHeader = TraceparentFormat.get().parse(traceParent);
            if (contextFromParentHeader == null) {
                return withBaggage(TraceContextOrSamplingFlags.EMPTY, carrier, getter);
            }
            String traceStateHeader = getter.get(carrier, TRACESTATE);
            TraceContextOrSamplingFlags context = context(contextFromParentHeader, traceStateHeader);
            if (this.baggagePropagator == null || this.braveBaggageManager == null) {
                return context;
            }
            return withBaggage(context, carrier, getter);
        };
    }

    private <R> TraceContextOrSamplingFlags withBaggage(TraceContextOrSamplingFlags context, R carrier,
            Getter<R, String> getter) {
        if (context.context() == null) {
            return context;
        }
        return this.baggagePropagator.contextWithBaggage(carrier, context, getter);
    }

    TraceContextOrSamplingFlags context(TraceContext contextFromParentHeader, String traceStateHeader) {
        if (!StringUtils.isNotBlank(traceStateHeader)) {
            return TraceContextOrSamplingFlags.create(contextFromParentHeader);
        }
        try {
            return TraceContextOrSamplingFlags
                .newBuilder(TraceContext.newBuilder()
                    .traceId(contextFromParentHeader.traceId())
                    .traceIdHigh(contextFromParentHeader.traceIdHigh())
                    .spanId(contextFromParentHeader.spanId())
                    .sampled(contextFromParentHeader.sampled())
                    .build())
                .build();
        }
        catch (IllegalArgumentException e) {
            logger.info("Unparseable tracestate header. Returning span context without state.");
            return TraceContextOrSamplingFlags.create(contextFromParentHeader);
        }
    }

}

/**
 * Taken from OpenTelemetry API.
 */
@SuppressWarnings("deprecation")
class W3CBaggagePropagator {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(W3CBaggagePropagator.class);

    private static final String TRACE_STATE = "tracestate";

    private static final String FIELD = "baggage";

    private static final List<String> FIELDS = singletonList(FIELD);

    private final BaggageManager braveBaggageManager;

    private final List<String> localFields;

    W3CBaggagePropagator(BaggageManager baggageManager, List<String> localFields) {
        this.braveBaggageManager = baggageManager;
        this.localFields = localFields;
    }

    public List<String> keys() {
        return FIELDS;
    }

    public <R> TraceContext.Injector<R> injector(Propagation.Setter<R, String> setter) {
        return (context, carrier) -> {
            BaggageFields extra = context.findExtra(BaggageFields.class);
            if (extra == null || extra.getAllFields().isEmpty()) {
                return;
            }
            StringBuilder headerContent = new StringBuilder();
            // We ignore local keys - they won't get propagated
            String[] strings = this.localFields.toArray(new String[0]);
            Map<String, String> filtered = extra.toMapFilteringFieldNames(strings);
            for (Map.Entry<String, String> entry : filtered.entrySet()) {
                if (TRACE_STATE.equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                headerContent.append(entry.getKey()).append("=").append(entry.getValue());
                // TODO: [OTEL] No metadata support
                // String metadataValue = entry.getEntryMetadata().getValue();
                // if (metadataValue != null && !metadataValue.isEmpty()) {
                // headerContent.append(";").append(metadataValue);
                // }
                headerContent.append(",");
            }
            if (headerContent.length() > 0) {
                headerContent.setLength(headerContent.length() - 1);
                setter.put(carrier, FIELD, headerContent.toString());
            }
        };
    }

    <R> TraceContextOrSamplingFlags contextWithBaggage(R carrier, TraceContextOrSamplingFlags flags,
            Propagation.Getter<R, String> getter) {
        String baggageHeader = getter.get(carrier, FIELD);
        List<AbstractMap.SimpleEntry<Baggage, String>> pairs = baggageHeader == null || baggageHeader.isEmpty()
                ? Collections.emptyList() : addBaggageToContext(baggageHeader);
        return flags.toBuilder().addExtra(new BraveBaggageFields(pairs)).build();
    }

    List<AbstractMap.SimpleEntry<Baggage, String>> addBaggageToContext(String baggageHeader) {
        List<AbstractMap.SimpleEntry<Baggage, String>> pairs = new ArrayList<>();
        String[] entries = baggageHeader.split(",");
        for (String entry : entries) {
            int beginningOfMetadata = entry.indexOf(";");
            if (beginningOfMetadata > 0) {
                entry = entry.substring(0, beginningOfMetadata);
            }
            String[] keyAndValue = entry.split("=");
            for (int i = 0; i < keyAndValue.length; i += 2) {
                try {
                    String key = keyAndValue[i].trim();
                    String value = keyAndValue[i + 1].trim();
                    Baggage baggage = this.braveBaggageManager.createBaggage(key);
                    pairs.add(new AbstractMap.SimpleEntry<>(baggage, value));
                }
                catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Exception occurred while trying to parse baggage with key value ["
                                + Arrays.toString(keyAndValue) + "]. Will ignore that entry.", e);
                    }
                }
            }
        }
        return pairs;
    }

}
