package io.micrometer.tracing.instrument.grpc;

import io.micrometer.core.instrument.binder.grpc.GrpcClientObservationContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link GrpcClientTracingObservationHandler}.
 *
 * @author Tadaya Tsuyukubo
 */
class GrpcClientTracingObservationHandlerTests {

    @ParameterizedTest
    @MethodSource
    void customizeSenderSpan(String authority, String host, int port) {
        Tracer tracer = mock(Tracer.class);
        Propagator propagator = mock(Propagator.class);
        GrpcClientTracingObservationHandler handler = new GrpcClientTracingObservationHandler(tracer, propagator);

        GrpcClientObservationContext context = new GrpcClientObservationContext((carrier, key, value) -> {
        });
        context.setAuthority(authority);

        Span span = mock(Span.class);
        handler.customizeSenderSpan(context, span);

        verify(span).remoteIpAndPort(host, port);
    }

    static Stream<Arguments> customizeSenderSpan() {
        // @formatter:off
        return Stream.of(
                arguments("io.micrometer.grpc:12345", "io.micrometer.grpc", 12345),
                arguments("localhost:12345", "localhost", 12345),
                arguments("localhost", "localhost", -1)
        );
        // @formatter:on
    }

}
