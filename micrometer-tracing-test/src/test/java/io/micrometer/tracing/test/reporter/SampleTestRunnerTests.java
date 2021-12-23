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

package io.micrometer.tracing.test.reporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.SampleTestRunner;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.BDDAssertions.then;

@Tag("docker")
class SampleTestRunnerTests extends SampleTestRunner {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SampleTestRunnerTests.class);

    @Container
    private static final GenericContainer zipkin = new GenericContainer(DockerImageName.parse("openzipkin/zipkin"))
            .withExposedPorts(9411);

    private static final MockWebServer server = new MockWebServer();

    private static final Queue<String> traces = new LinkedList<>();

    SampleTestRunnerTests() {
        super(SampleTestRunner.SamplerRunnerConfig.builder()
                .wavefrontUrl(server.url("/").toString()).zipkinUrl("http://localhost:" + zipkin.getFirstMappedPort()).wavefrontToken("foo").build());
    }

    @BeforeAll
    static void setup() throws IOException {
        zipkin.start();
        server.start();
    }

    @AfterAll
    static void cleanup() throws IOException {
        zipkin.stop();
        server.shutdown();
    }

    @AfterEach
    void assertions(TestInfo testInfo) throws InterruptedException, IOException {
        String testName = testInfo.getDisplayName().toLowerCase();
        String lastTrace = traces.remove();
        if (testName.contains("zipkin")) {
            assertThatZipkinRegisteredATrace(lastTrace);
        }
        else {
            Awaitility.await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> then(server.getRequestCount()).isGreaterThan(0));
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            then(request).isNotNull();
            then(request.getPath()).isEqualTo("/report?f=trace");
        }
    }

    private void assertThatZipkinRegisteredATrace(String lastTrace) throws IOException {
        URL url = new URL(this.samplerRunnerConfig.zipkinUrl + "/api/v2/trace/" + lastTrace);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        BDDAssertions.then(content).isNotEmpty();
        in.close();
        con.disconnect();
    }

    @Override
    public BiConsumer<Tracer, MeterRegistry> yourCode() {
        return (tracer, meterRegistry) -> {
            BDDAssertions.then(tracer.currentSpan()).isNotNull();
            traces.add(tracer.currentSpan().context().traceId());

            Timer.Sample start = Timer.start(meterRegistry);
            log.info("Hello");
            start.stop(Timer.builder("name"));
        };
    }
}
