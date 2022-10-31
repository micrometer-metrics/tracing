package io.micrometer.tracing.instrument.grpc;

import io.micrometer.core.instrument.binder.grpc.GrpcServerObservationContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GrpcServerTracingObservationHandler}.
 *
 * @author Tadaya Tsuyukubo
 */
class GrpcServerTracingObservationHandlerTests {

    @ParameterizedTest
    @MethodSource
    void customizeExtractedSpan(String authority, String host, int port) {
        Tracer tracer = mock(Tracer.class);
        Propagator propagator = mock(Propagator.class);
        GrpcServerTracingObservationHandler handler = new GrpcServerTracingObservationHandler(tracer, propagator);

        GrpcServerObservationContext context = new GrpcServerObservationContext((carrier, key) -> null);
        context.setAuthority(authority);

        Span.Builder builder = mock(Span.Builder.class);
        handler.customizeExtractedSpan(context, builder);

        verify(builder).remoteIpAndPort(host, port);
    }

    @Test
    void customizeExtractedSpanWithNull() {
        Tracer tracer = mock(Tracer.class);
        Propagator propagator = mock(Propagator.class);
        GrpcServerTracingObservationHandler handler = new GrpcServerTracingObservationHandler(tracer, propagator);

        GrpcServerObservationContext context = new GrpcServerObservationContext((carrier, key) -> null);
        context.setAuthority(null);

        Span.Builder builder = mock(Span.Builder.class);
        handler.customizeExtractedSpan(context, builder);

        verifyNoInteractions(builder);
    }

    static Stream<Arguments> customizeExtractedSpan() {
        // @formatter:off
        return Stream.of(
                arguments("io.micrometer.grpc:12345", "io.micrometer.grpc", 12345),
                arguments("localhost:12345", "localhost", 12345),
                arguments("localhost", "localhost", -1)
        );
        // @formatter:on
    }

}
