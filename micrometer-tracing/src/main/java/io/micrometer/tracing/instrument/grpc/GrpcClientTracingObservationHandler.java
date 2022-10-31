package io.micrometer.tracing.instrument.grpc;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.binder.grpc.GrpcClientObservationContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;

import java.net.URI;

/**
 * {@link TracingObservationHandler} for gRPC in client side.
 *
 * @author Tadaya Tsuyukubo
 */
public class GrpcClientTracingObservationHandler
        extends PropagatingSenderTracingObservationHandler<GrpcClientObservationContext> {

    private static final InternalLogger log = InternalLoggerFactory
            .getInstance(GrpcClientTracingObservationHandler.class);

    public GrpcClientTracingObservationHandler(Tracer tracer, Propagator propagator) {
        super(tracer, propagator);
    }

    @Override
    public void customizeSenderSpan(GrpcClientObservationContext context, Span span) {
        String authority = context.getAuthority();
        try {
            URI uri = new URI(null, authority, null, null, null);
            span.remoteIpAndPort(uri.getHost(), uri.getPort());
        }
        catch (Exception ex) {
            log.warn("Exception [{}], occurred while trying to parse the authority [{}] to host and port.", ex,
                    authority);
        }
    }

}
