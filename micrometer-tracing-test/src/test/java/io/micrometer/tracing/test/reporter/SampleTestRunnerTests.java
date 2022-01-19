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
import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingRecordingHandler;
import io.micrometer.tracing.test.SampleTestRunner;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;

@Tag("docker")
@WireMockTest
class SampleTestRunnerTests extends SampleTestRunner {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SampleTestRunnerTests.class);

    @Container
    private static final GenericContainer zipkin = new GenericContainer(DockerImageName.parse("openzipkin/zipkin"))
            .withExposedPorts(9411);

    @RegisterExtension
    WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private static final Queue<String> traces = new LinkedList<>();

    @Override protected SampleRunnerConfig getSampleRunnerConfig() {
        return SampleRunnerConfig.builder()
                .wavefrontUrl(wireMock.baseUrl()).zipkinUrl("http://localhost:" + zipkin.getFirstMappedPort()).wavefrontToken("foo").build();
    }

    @Override protected MeterRegistry getMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    Deque<TimerRecordingHandler> handlers;

    @Override
    public BiConsumer<BuildingBlocks, Deque<TimerRecordingHandler>> customizeTimerRecordingHandlers() {
        return (buildingBlocks, timerRecordingHandlers) -> {
            timerRecordingHandlers.addFirst(new MyRecordingHandler());
            this.handlers = timerRecordingHandlers;
        };
    }

    @BeforeEach
    void start() throws IOException {
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    }

    @BeforeAll
    static void setup() {
        zipkin.start();
    }

    @AfterAll
    static void cleanup() {
        zipkin.stop();
    }

    @AfterEach
    void assertions(TestInfo testInfo) throws InterruptedException, IOException {
        String testName = testInfo.getDisplayName().toLowerCase();
        String lastTrace = traces.remove();
        if (testName.contains("zipkin")) {
            assertThatZipkinRegisteredATrace(lastTrace);
        }
        else {
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                wireMock.verify(anyRequestedFor(urlMatching(".*/report\\?f=trace.*")))
            );
        }
        then(handlers.getFirst()).isInstanceOf(MyRecordingHandler.class);
        then(handlers.getLast()).isInstanceOf(DefaultTracingRecordingHandler.class);
    }

    private void assertThatZipkinRegisteredATrace(String lastTrace) throws IOException {
        URL url = new URL(getSampleRunnerConfig().zipkinUrl + "/api/v2/trace/" + lastTrace);
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

    static class MyRecordingHandler implements TimerRecordingHandler {
        @Override
        public void onScopeOpened(Timer.Sample sample, Timer.HandlerContext context) {

        }

        @Override
        public void onScopeClosed(Timer.Sample sample, Timer.HandlerContext context) {

        }

        @Override
        public void onStart(Timer.Sample sample, Timer.HandlerContext context) {

        }

        @Override
        public void onError(Timer.Sample sample, Timer.HandlerContext context, Throwable throwable) {

        }

        @Override
        public void onStop(Timer.Sample sample, Timer.HandlerContext context, Timer timer, Duration duration) {

        }

        @Override
        public boolean supportsContext(Timer.HandlerContext handlerContext) {
            return false;
        }
    }
}
