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

package io.micrometer.tracing.reporter.wavefront;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class WavefrontBraveSetupTests {

    private static InternalLogger log = InternalLoggerFactory.getInstance(WavefrontBraveSetupTests.class);

    SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();

    MockWebServer server = new MockWebServer();

    @BeforeEach
    void setup() throws IOException {
        server.start();
    }

    @Test
    void should_register_a_span_in_wavefront() throws InterruptedException {
        WavefrontBraveSetup setup = WavefrontBraveSetup.builder(this.server.url("/").toString(), "token")
                .applicationName("app-name")
                .serviceName("service-name")
                .source("source")
                .register(this.simpleMeterRegistry);

        WavefrontBraveSetup.run(setup, __ -> {
            Timer.Sample sample = Timer.start(simpleMeterRegistry);
            log.info("New sample created");
            sample.stop(Timer.builder("the-name"));
        });

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(0));

        RecordedRequest request = this.server.takeRequest(2, TimeUnit.SECONDS);
        then(request).isNotNull();
        then(request.getPath()).isEqualTo("/report?f=trace");
    }
}
