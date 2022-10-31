package io.micrometer.tracing.instrument.grpc;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.binder.grpc.GrpcServerObservationContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;

import java.net.URI;

/**
 * {@link TracingObservationHandler} for gRPC in server side.
 *
 * @author Tadaya Tsuyukubo
 */
public class GrpcServerTracingObservationHandler
        extends PropagatingReceiverTracingObservationHandler<GrpcServerObservationContext> {

    private static final InternalLogger log = InternalLoggerFactory
            .getInstance(GrpcServerTracingObservationHandler.class);

    public GrpcServerTracingObservationHandler(Tracer tracer, Propagator propagator) {
        super(tracer, propagator);
    }

    @Override
    public Span.Builder customizeExtractedSpan(GrpcServerObservationContext context, Span.Builder builder) {
        String authority = context.getAuthority();
        if (authority != null) {
            try {
                URI uri = new URI(null, authority, null, null, null);
                builder.remoteIpAndPort(uri.getHost(), uri.getPort());
            }
            catch (Exception ex) {
                log.warn("Exception [{}], occurred while trying to parse the authority [{}] to host and port.", ex,
                        authority);
            }
        }
        return builder;
    }

}
