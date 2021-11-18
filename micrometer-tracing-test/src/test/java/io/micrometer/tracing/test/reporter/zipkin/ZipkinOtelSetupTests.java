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

package io.micrometer.tracing.test.reporter.zipkin;

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

class ZipkinOtelSetupTests {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ZipkinOtelSetupTests.class);

    SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();

    MockWebServer server = new MockWebServer();

    @BeforeEach
    void setup() throws IOException {
        server.start();
    }

    @Test
    void should_register_a_span_in_zipkin() throws InterruptedException {
        ZipkinOtelSetup setup = ZipkinOtelSetup.builder().zipkinUrl(this.server.url("/").toString()).register(this.simpleMeterRegistry);

        ZipkinOtelSetup.run(setup, __ -> {
            Timer.Sample sample = Timer.start(simpleMeterRegistry);
            log.info("New sample created");
            sample.stop(Timer.builder("the-name"));
        });

        Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(0));

        RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
        then(request).isNotNull();
        then(request.getPath()).isEqualTo("/api/v2/spans");
    }

}
