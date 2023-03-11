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
package io.micrometer.tracing.test.reporter.zipkin;

import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@WireMockTest
class ZipkinOtelSetupTests {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ZipkinOtelSetupTests.class);

    ObservationRegistry registry = ObservationRegistry.create();

    @Test
    void should_register_a_span_in_zipkin(WireMockRuntimeInfo wmri) throws InterruptedException {
        ZipkinOtelSetup setup = ZipkinOtelSetup.builder().zipkinUrl(wmri.getHttpBaseUrl()).register(this.registry);

        ZipkinOtelSetup.run(setup, __ -> {
            Observation sample = Observation.start("the-name", registry);
            try (Observation.Scope scope = sample.openScope()) {
                log.info("New sample created");
            }
            sample.stop();
        });

        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(
                    () -> wmri.getWireMock().verifyThat(WireMock.anyRequestedFor(urlPathEqualTo("/api/v2/spans"))));
    }

}
