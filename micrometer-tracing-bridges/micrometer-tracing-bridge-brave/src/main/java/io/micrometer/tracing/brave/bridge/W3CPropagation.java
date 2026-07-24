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
import org.jspecify.annotations.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
public class W3CPropagation extends Propagation.Factory implements Propagation<String> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(W3CPropagation.class.getName());

    private static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList(TRACEPARENT, TRACESTATE));

    private final @Nullable W3CBaggagePropagator baggagePropagator;

    private final @Nullable BaggageManager braveBaggageManager;

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

    private <R> void addTraceState(Setter<R, String> setter, TraceContext context, @Nullable R carrier) {
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

    private <R> TraceContextOrSamplingFlags withBaggage(TraceContextOrSamplingFlags context, @Nullable R carrier,
            Getter<R, String> getter) {
        if (context.context() == null) {
            return context;
        }
        return Objects.requireNonNull(this.baggagePropagator).contextWithBaggage(carrier, context, getter);
    }

    TraceContextOrSamplingFlags context(TraceContext contextFromParentHeader, @Nullable String traceStateHeader) {
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
class W3CBaggagePropagator {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(W3CBaggagePropagator.class);

    private static final String TRACE_STATE = "tracestate";

    private static final String FIELD = "baggage";

    private static final List<String> FIELDS = singletonList(FIELD);

    // https://www.w3.org/TR/baggage/#limits
    private static final int MAX_BAGGAGE_ENTRIES = 64;

    // https://www.w3.org/TR/baggage/#limits
    private static final int MAX_BAGGAGE_BYTES = 8192;

    private final BaggageManager braveBaggageManager;

    private final String[] localFieldsArray;

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private static final long INVALID_KEY_MASK_LOW;

    private static final long INVALID_KEY_MASK_HIGH;

    static {
        long low = 0;
        long high = 0;
        // Characters 0 to 32 are invalid
        for (int i = 0; i <= 32; i++) {
            low |= (1L << i);
        }
        // DEL (127) is invalid
        high |= (1L << (127 - 64));
        char[] delimiters = "\"(),/:;<=>?@[\\]{}".toCharArray();
        for (char c : delimiters) {
            if (c < 64) {
                low |= (1L << c);
            }
            else {
                high |= (1L << (c - 64));
            }
        }
        INVALID_KEY_MASK_LOW = low;
        INVALID_KEY_MASK_HIGH = high;
    }

    /**
     * Check a baggage key against the specification of valid characters. <pre>
     * {@code
     * key            =  token ; as defined in RFC 7230, Section 3.2.6
     *
     * token          = 1*tchar
     *
     * tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
     *                / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
     *                / DIGIT / ALPHA
     *                ; any VCHAR, except delimiters
     *
     * Delimiters are chosen from the set of US-ASCII visual characters not allowed in a token
     *    (DQUOTE and "(),/:;<=>?@[\]{}").
     * }
     * </pre>
     * @param key baggage key to check
     * @return whether the key is invalid
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-3.2.6">Section
     * 3.2.6 of RFC7230</a>
     */
    private static boolean isInvalidBaggageKey(String key) {
        if (key.isEmpty()) {
            return true;
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            // CTL are invalid, DEL and non-ASCII chars are invalid
            if (ch <= 32 || ch >= 127) {
                return true;
            }
            if (ch < 64) {
                if (((INVALID_KEY_MASK_LOW >>> ch) & 1L) != 0) {
                    return true;
                }
            }
            else {
                if (((INVALID_KEY_MASK_HIGH >>> (ch - 64)) & 1L) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String percentEncode(String rawValue) {
        byte[] bytes = rawValue.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            if (isBaggageOctet(b) && b != '%' && b != '=') {
                sb.append((char) b);
            }
            else {
                sb.append('%');
                sb.append(HEX_DIGITS[(b >> 4) & 0xF]);
                sb.append(HEX_DIGITS[b & 0xF]);
            }
        }
        return sb.toString();
    }

    /**
     * Check a character against the W3C Baggage specification definition for valid
     * baggage characters. <pre>
     * {@code
     * baggage-octet          =  %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
     *                           ; US-ASCII characters excluding CTLs,
     *                           ; whitespace, DQUOTE, comma, semicolon,
     *                           ; and backslash
     * }
     * </pre>
     * @param b UTF-8 character as a byte
     * @return whether it is a valid baggage character
     * @see <a href="https://www.w3.org/TR/baggage/#definition">W3C Baggage
     * specification</a>
     */
    private static boolean isBaggageOctet(byte b) {
        // excludes CTL = %x00-1F / %x7F, space %x20, non-ASCII chars
        if (b < 0x21 || b > 0x7E) {
            return false;
        }
        if (b == 0x22 // double quote '"'
                || b == 0x2C // comma ','
                || b == 0x3B // semicolon ';'
                || b == 0x5C // backslash '\'
        ) {
            return false;
        }
        return true;
    }

    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                byte b = bytes[i];
                if (b == '%') {
                    if (i + 2 < bytes.length) {
                        int hi = Character.digit((char) bytes[i + 1], 16);
                        int lo = Character.digit((char) bytes[i + 2], 16);
                        if (hi >= 0 && lo >= 0) {
                            out.write((hi << 4) | lo);
                            i += 2;
                            continue;
                        }
                    }
                }
                out.write(b);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
        catch (Exception e) {
            return value;
        }
    }

    W3CBaggagePropagator(BaggageManager baggageManager, List<String> localFields) {
        this.braveBaggageManager = baggageManager;
        this.localFieldsArray = localFields.toArray(new String[0]);
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
            Map<String, String> filtered = extra.toMapFilteringFieldNames(this.localFieldsArray);
            int entryCount = 0;
            for (Map.Entry<String, String> entry : filtered.entrySet()) {
                if (TRACE_STATE.equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                if (entryCount >= MAX_BAGGAGE_ENTRIES) {
                    break;
                }
                String key = entry.getKey();
                if (isInvalidBaggageKey(key)) {
                    continue;
                }
                String value = percentEncode(entry.getValue());
                // note: we do not support metadata currently
                int entryLength = key.length() + 1 + value.length() + 1; // "key=value,"
                if (headerContent.length() + entryLength - 1 > MAX_BAGGAGE_BYTES) {
                    break;
                }
                headerContent.append(key).append('=').append(value).append(',');
                entryCount++;
            }
            if (headerContent.length() > 0) {
                headerContent.setLength(headerContent.length() - 1);
                setter.put(carrier, FIELD, headerContent.toString());
            }
        };
    }

    <R> TraceContextOrSamplingFlags contextWithBaggage(@Nullable R carrier, TraceContextOrSamplingFlags flags,
            Propagation.Getter<R, String> getter) {
        String baggageHeader = getter.get(carrier, FIELD);
        if (baggageHeader == null || baggageHeader.isEmpty()) {
            return flags.toBuilder().addExtra(new BraveBaggageFields(Collections.emptyList())).build();
        }
        if (baggageHeader.length() > MAX_BAGGAGE_BYTES) {
            if (log.isDebugEnabled()) {
                log.debug("Baggage header length (" + baggageHeader.length()
                        + ") exceeds W3C limit of 8192 bytes. Truncating header.");
            }
            baggageHeader = baggageHeader.substring(0, MAX_BAGGAGE_BYTES);
        }
        return flags.toBuilder().addExtra(new BraveBaggageFields(addBaggageToContext(baggageHeader))).build();
    }

    List<AbstractMap.SimpleEntry<Baggage, String>> addBaggageToContext(String baggageHeader) {
        String[] entries = baggageHeader.split(",", MAX_BAGGAGE_ENTRIES + 1);
        List<AbstractMap.SimpleEntry<Baggage, String>> pairs = new ArrayList<>(entries.length);
        int maxToInspect = Math.min(entries.length, MAX_BAGGAGE_ENTRIES);
        for (int i = 0; i < maxToInspect; i++) {
            String entry = entries[i];
            int beginningOfMetadata = entry.indexOf(";");
            if (beginningOfMetadata > 0) {
                entry = entry.substring(0, beginningOfMetadata);
            }
            String[] keyAndValue = entry.split("=", 2);
            boolean hasValue = keyAndValue.length == 2 && !keyAndValue[1].isEmpty();
            if (hasValue) {
                try {
                    String key = keyAndValue[0].trim();
                    if (isInvalidBaggageKey(key)) {
                        continue;
                    }
                    String value = percentDecode(keyAndValue[1].trim());
                    @SuppressWarnings("deprecation")
                    Baggage baggage = this.braveBaggageManager.createBaggage(key);
                    pairs.add(new AbstractMap.SimpleEntry<>(baggage, value));
                }
                catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Exception occurred while trying to parse baggage with key value "
                                + Arrays.toString(keyAndValue) + ". Will ignore that entry.", e);
                    }
                }
            }
            else if (log.isDebugEnabled()) {
                log.debug(
                        "Unable to to parse baggage with key value since it seems something is not in key=value format: "
                                + Arrays.toString(keyAndValue) + ". Will ignore that entry.");
            }
        }
        return pairs;
    }

}
