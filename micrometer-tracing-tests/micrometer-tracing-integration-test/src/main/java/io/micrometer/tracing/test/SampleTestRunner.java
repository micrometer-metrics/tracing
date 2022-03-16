/*
 * Copyright 2013-2020 the original author or authors.
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

package io.micrometer.tracing.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.TimerObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.TestConfigAccessor;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.micrometer.tracing.test.reporter.inmemory.InMemoryBraveSetup;
import io.micrometer.tracing.test.reporter.inmemory.InMemoryOtelSetup;
import io.micrometer.tracing.test.reporter.wavefront.WavefrontBraveSetup;
import io.micrometer.tracing.test.reporter.wavefront.WavefrontOtelSetup;
import io.micrometer.tracing.test.reporter.zipkin.ZipkinBraveSetup;
import io.micrometer.tracing.test.reporter.zipkin.ZipkinOtelSetup;
import io.micrometer.tracing.util.StringUtils;
import io.micrometer.tracing.util.logging.InternalLogger;
import io.micrometer.tracing.util.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import zipkin2.CheckResult;
import zipkin2.reporter.Sender;

/**
 * Prepares the required tracing setup and reporters / exporters. The user
 * needs to just provide the code to test and that way all the combinations
 * of tracers and exporters will be automatically applied. It also sets up the
 * {@link MeterRegistry} in such a way that it consists all {@link TracingObservationHandler}
 * injected into {@link ObservationRegistry#observationConfig()}.
 *
 * When extending this class you can either eagerly pass the {@link MeterRegistry} and
 * {@link SampleRunnerConfig} by calling this class' constructors. Another option is to lazilly
 * load those objects. To do it use the default constructor but override {@link SampleTestRunner#getMeterRegistry()}
 * and {@link SampleTestRunner#getSampleRunnerConfig()} methods and provide your own ways of retrieving
 * {@link MeterRegistry} and {@link SampleRunnerConfig}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public abstract class SampleTestRunner {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SampleTestRunner.class);

    private static final List<MeterRegistry> meterRegistries = new CopyOnWriteArrayList<>();

    private SampleRunnerConfig sampleRunnerConfig;

    private ObservationRegistry observationRegistry;

    private MeterRegistry meterRegistry;

    private final List<ObservationHandler<?>> observationHandlersCopy;

    /**
     * Creates a new instance of the {@link SampleTestRunner} with a pre-created configuration and {@link MeterRegistry}.
     *
     * @param sampleRunnerConfig configuration for the SampleTestRunner
     * @param meterRegistry       provided {@link MeterRegistry} instance
     */
    public SampleTestRunner(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.sampleRunnerConfig = sampleRunnerConfig;
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
        this.observationHandlersCopy = new ArrayList<>();
    }

    /**
     * Creates a new instance of the {@link SampleTestRunner} with a pre-created configuration and a default {@link MeterRegistry}.
     *
     * @param sampleRunnerConfig configuration for the SampleTestRunner
     */
    public SampleTestRunner(SampleRunnerConfig sampleRunnerConfig) {
        this(sampleRunnerConfig, ObservationRegistry.create(), new SimpleMeterRegistry());
        getObservationRegistry().observationConfig().observationHandler(new TimerObservationHandler(getMeterRegistry()));
    }

    /**
     * Creates a new instance of the {@link SampleTestRunner} that will have configuration and {@link MeterRegistry}
     * resolved lazilly at runtime. Remember that if you don't override the {@link SampleTestRunner#getMeterRegistry()} and
     * {@link SampleTestRunner#getSampleRunnerConfig()} methods you will get NPEs.
     */
    public SampleTestRunner() {
        this.sampleRunnerConfig = SampleRunnerConfig.builder().build();
        this.meterRegistry = null;
        this.observationHandlersCopy = new ArrayList<>();
    }

    /**
     * Override this to resolve the {@link MeterRegistry} at runtime. If not overridden
     * will return the passed {@link MeterRegistry} from the constructor.
     *
     * @return meter registry to be used in tests
     */
    protected MeterRegistry getMeterRegistry() {
        return this.meterRegistry;
    }

    /**
     * Override this to resolve the {@link ObservationRegistry} at runtime. If not overridden
     * will return the passed {@link ObservationRegistry} from the constructor.
     *
     * @return observation registry to be used in tests
     */
    protected ObservationRegistry getObservationRegistry() {
        return this.observationRegistry;
    }

    /**
     * Override this to resolve the {@link SampleRunnerConfig} at runtime. If not overridden
     *      * will return the passed {@link MeterRegistry} from the constructor.
     *
     * @return sampler config to be used in tests
     */
    protected SampleRunnerConfig getSampleRunnerConfig() {
        return this.sampleRunnerConfig;
    }

    @ParameterizedTest
    @EnumSource(TracingSetup.class)
    void run(TracingSetup tracingSetup) {
        tracingSetup.run(getSampleRunnerConfig(), getObservationRegistry(), getMeterRegistry(), this);
    }

    @BeforeEach
    void setupRegistry() {
        this.observationHandlersCopy.addAll(TestConfigAccessor.getHandlers(getObservationRegistry().observationConfig()));
    }

    @AfterEach
    void clearMeterRegistry() {
        TestConfigAccessor.clearHandlers(getObservationRegistry().observationConfig());
        this.observationHandlersCopy.forEach(handler -> getObservationRegistry().observationConfig().observationHandler(handler));
        getMeterRegistry().clear();
        meterRegistries.add(getMeterRegistry());
    }

    private void printMetrics() {
        StringBuilder stringBuilder = new StringBuilder();
        getMeterRegistry().forEachMeter(meter -> stringBuilder.append("\tMeter with name <")
                .append(meter.getId().getName()).append(">")
                .append(" and type <").append(meter.getId().getType()).append(">")
                .append(" has the following measurements \n\t\t<").append(meter.measure()).append(">")
                .append(" \n\t\tand has the following tags <")
                .append(meter.getId().getTags()).append(">\n"));
        log.info("Gathered the following metrics\n" + stringBuilder);
    }

    @AfterAll
    static void closeAll() {
        meterRegistries.forEach(MeterRegistry::close);
    }

    /**
     * Code that you want to measure and run.
     *
     * @return your code with access to the current tracing and measuring infrastructure
     */
    public abstract SampleTestRunnerConsumer yourCode() throws Exception;

    private SampleTestRunnerConsumer runWithMetricsPrinting() {
        return (bb, registry) -> {
            try {
                yourCode().accept(bb, registry);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            finally {
                printMetrics();
            }
        };
    }

    public interface SampleTestRunnerConsumer {
        /**
         *
         * @param bb - building blocks
         * @param meterRegistry - meter registry
         * @throws Exception exception
         */
        void accept(BuildingBlocks bb, MeterRegistry meterRegistry) throws Exception;
    }

    /**
     * Override this to customize the list of timer recording handlers.
     *
     * @return timer recording handler customizing function
     */
    @SuppressWarnings("rawtypes")
    public BiConsumer<BuildingBlocks, Deque<ObservationHandler>> customizeObservationHandlers() {
        return (tracer, ObservationHandlers) -> {

        };
    }

    /**
     * Override this to just run a subset of tracing setups to run.
     * @return array of tracing setups to run
     */
    public TracingSetup[] getTracingSetup() {
        return TracingSetup.values();
    }

    /**
     * Tracing setups. Contains various combinations of tracers and reporters.
     */
    public enum TracingSetup {
        /**
         * In memory with OTel Tracer.
         */
        IN_MEMORY_OTEL {
            @Override
            void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(IN_MEMORY_OTEL, sampleTestRunner.getTracingSetup());
                InMemoryOtelSetup setup = InMemoryOtelSetup.builder()
                        .observationHandlerCustomizer(sampleTestRunner.customizeObservationHandlers()).register(observationRegistry);
                InMemoryOtelSetup.run(setup, __ -> runTraced(sampleRunnerConfig, IN_MEMORY_OTEL, setup.getBuildingBlocks(), observationRegistry, meterRegistry, sampleTestRunner.runWithMetricsPrinting()));
            }

            @Override
            void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId) {
                log.info("In memory tracing used. All spans should have trace id <{}>", traceId);
            }
        },

        /**
         * In memory with Brave Tracer.
         */
        IN_MEMORY_BRAVE {
            @Override
            void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(IN_MEMORY_BRAVE, sampleTestRunner.getTracingSetup());
                InMemoryBraveSetup setup = InMemoryBraveSetup.builder()
                        .observationHandlerCustomizer(sampleTestRunner.customizeObservationHandlers()).register(observationRegistry);
                InMemoryBraveSetup.run(setup, __ -> runTraced(sampleRunnerConfig, IN_MEMORY_BRAVE, setup.getBuildingBlocks(), observationRegistry, meterRegistry, sampleTestRunner.runWithMetricsPrinting()));
            }

            @Override
            void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId) {
                log.info("In memory tracing used. All spans should have trace id <{}>", traceId);
            }
        },

        /**
         * Zipkin Exporter with OTel Tracer.
         */
        ZIPKIN_OTEL {
            @Override
            void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(ZIPKIN_OTEL, sampleTestRunner.getTracingSetup());
                ZipkinOtelSetup setup = ZipkinOtelSetup.builder()
                        .observationHandlerCustomizer(sampleTestRunner.customizeObservationHandlers()).zipkinUrl(
                                sampleRunnerConfig.zipkinUrl).register(observationRegistry);
                checkZipkinAssumptions(setup.getBuildingBlocks().sender);
                ZipkinOtelSetup.run(setup, __ -> runTraced(sampleRunnerConfig, ZIPKIN_OTEL, setup.getBuildingBlocks(), observationRegistry, meterRegistry, sampleTestRunner.runWithMetricsPrinting()));
            }

            @Override
            void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Zipkin with id <{}>", traceId);
                log.info("{}/zipkin/traces/{}", sampleRunnerConfig.zipkinUrl, traceId);
            }
        },

        /**
         * Zipkin Reporter with Brave Tracer.
         */
        ZIPKIN_BRAVE {
            @Override
            void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(ZIPKIN_BRAVE, sampleTestRunner.getTracingSetup());
                ZipkinBraveSetup setup = ZipkinBraveSetup.builder()
                        .observationHandlerCustomizer(sampleTestRunner.customizeObservationHandlers()).zipkinUrl(
                                sampleRunnerConfig.zipkinUrl).register(observationRegistry);
                checkZipkinAssumptions(setup.getBuildingBlocks().sender);
                ZipkinBraveSetup.run(setup, __ -> runTraced(sampleRunnerConfig, ZIPKIN_BRAVE, setup.getBuildingBlocks(), observationRegistry, meterRegistry, sampleTestRunner.runWithMetricsPrinting()));
            }

            @Override
            void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Zipkin with id <{}>", traceId);
                log.info("{}/zipkin/traces/{}", sampleRunnerConfig.zipkinUrl, traceId);
            }
        },

        /**
         * Wavefront Exporter with OTel Tracer.
         */
        WAVEFRONT_OTEL {
            @Override
            void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(WAVEFRONT_OTEL, sampleTestRunner.getTracingSetup());
                checkWavefrontAssumptions(sampleRunnerConfig);
                WavefrontOtelSetup setup = WavefrontOtelSetup.builder(sampleRunnerConfig.wavefrontServerUrl, sampleRunnerConfig.wavefrontToken)
                        .applicationName(sampleRunnerConfig.wavefrontApplicationName)
                        .serviceName(sampleRunnerConfig.wavefrontServiceName)
                        .source(sampleRunnerConfig.wavefrontSource)
                        .observationHandlerCustomizer(sampleTestRunner.customizeObservationHandlers())
                        .register(observationRegistry, meterRegistry);
                WavefrontOtelSetup.run(setup, __ -> runTraced(sampleRunnerConfig, WAVEFRONT_OTEL, setup.getBuildingBlocks(), observationRegistry, meterRegistry, sampleTestRunner.runWithMetricsPrinting()));
            }

            @Override
            void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Wavefront with id <{}>", traceId);
                String wavefrontUrl = sampleRunnerConfig.wavefrontServerUrl.endsWith("/") ? sampleRunnerConfig.wavefrontServerUrl.substring(0, sampleRunnerConfig.wavefrontServerUrl.length() - 1) : sampleRunnerConfig.wavefrontServerUrl;
                log.info("{}/tracing/search?sortBy=MOST_RECENT&traceID={}", wavefrontUrl, traceId);
            }
        },

        /**
         * Wavefront Exporter with Brave Tracer.
         */
        WAVEFRONT_BRAVE {
            @Override
            void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(WAVEFRONT_BRAVE, sampleTestRunner.getTracingSetup());
                checkWavefrontAssumptions(sampleRunnerConfig);
                WavefrontBraveSetup setup = WavefrontBraveSetup.builder(sampleRunnerConfig.wavefrontServerUrl, sampleRunnerConfig.wavefrontToken)
                        .applicationName(sampleRunnerConfig.wavefrontApplicationName)
                        .serviceName(sampleRunnerConfig.wavefrontServiceName)
                        .source(sampleRunnerConfig.wavefrontSource)
                        .observationHandlerCustomizer(sampleTestRunner.customizeObservationHandlers())
                        .register(meterRegistry, observationRegistry);
                WavefrontBraveSetup.run(setup, __ -> runTraced(sampleRunnerConfig, WAVEFRONT_BRAVE, setup.getBuildingBlocks(), observationRegistry, meterRegistry, sampleTestRunner.runWithMetricsPrinting()));
            }

            @Override
            void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Wavefront with id <{}>", traceId);
                String wavefrontUrl = sampleRunnerConfig.wavefrontServerUrl.endsWith("/") ? sampleRunnerConfig.wavefrontServerUrl.substring(0, sampleRunnerConfig.wavefrontServerUrl.length() - 1) : sampleRunnerConfig.wavefrontServerUrl;
                log.info("{}/tracing/search?sortBy=MOST_RECENT&traceID={}", wavefrontUrl, traceId);
            }
        };

        private static void runTraced(SampleRunnerConfig sampleRunnerConfig, TracingSetup tracingSetup, BuildingBlocks bb, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunnerConsumer runnable) {
            Observation observation = Observation.start(tracingSetup.name().toLowerCase(Locale.ROOT), observationRegistry);
            String traceId = "";
            try (Observation.Scope ws = observation.openScope()) {
                Tracer tracer = bb.getTracer();
                traceId = tracer.currentSpan().context().traceId();
                runnable.accept(bb, meterRegistry);
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            finally {
                tracingSetup.printTracingLink(sampleRunnerConfig, traceId);
                observation.stop();
            }
        }

        private static void checkTracingSetupAssumptions(TracingSetup tracingSetup, TracingSetup[] chosenSetups) {
            Assumptions.assumeTrue(Arrays.asList(chosenSetups)
                    .contains(tracingSetup), tracingSetup.name() + " not found in the list of tracing setups to run " + Arrays.toString(chosenSetups));
        }

        private static void checkZipkinAssumptions(Sender sender) {
            CheckResult checkResult = sender.check();
            Assumptions.assumeTrue(checkResult.ok(), "There was a problem with connecting to Zipkin. Will NOT run any tests");
        }

        private static void checkWavefrontAssumptions(SampleRunnerConfig sampleRunnerConfig) {
            Assumptions.assumeTrue(StringUtils.isNotBlank(sampleRunnerConfig.wavefrontServerUrl), "To run tests against Tanzu Observability by Wavefront you need to set the Wavefront server url");
            Assumptions.assumeTrue(StringUtils.isNotBlank(sampleRunnerConfig.wavefrontToken), "To run tests against Tanzu Observability by Wavefront you need to set the Wavefront token");
        }

        abstract void run(SampleRunnerConfig sampleRunnerConfig, ObservationRegistry observationRegistry, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner);

        abstract void printTracingLink(SampleRunnerConfig sampleRunnerConfig, String traceId);
    }

    /**
     * {@link SampleTestRunner} configuration.
     */
    public static class SampleRunnerConfig {

        public String wavefrontToken;

        public String wavefrontServerUrl;

        public String zipkinUrl;

        public String wavefrontApplicationName;

        public String wavefrontServiceName;

        public String wavefrontSource;

        SampleRunnerConfig(String wavefrontToken, String wavefrontServerUrl, String wavefrontApplicationName, String wavefrontServiceName, String wavefrontSource, String zipkinUrl) {
            this.wavefrontToken = wavefrontToken;
            this.wavefrontServerUrl = wavefrontServerUrl != null ? wavefrontServerUrl : "https://vmware.wavefront.com";
            this.wavefrontApplicationName = wavefrontApplicationName != null ? wavefrontApplicationName : "test-application";
            this.wavefrontServiceName = wavefrontServiceName != null ? wavefrontServiceName : "test-service";
            this.wavefrontSource = wavefrontSource != null ? wavefrontSource : "test-source";
            this.zipkinUrl = zipkinUrl != null ? zipkinUrl : "http://localhost:9411";
        }

        /**
         * @return {@link SampleTestRunner} builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for {@link SampleRunnerConfig}.
         */
        public static class Builder {
            private String wavefrontToken;

            private String wavefrontUrl;

            private String wavefrontApplicationName;

            private String wavefrontServiceName;

            private String wavefrontSource;

            private String zipkinUrl;

            /**
             * Token required to connect to Tanzu Observability by Wavefront.
             *
             * @param wavefrontToken wavefront token
             * @return this
             */
            public Builder wavefrontToken(String wavefrontToken) {
                this.wavefrontToken = wavefrontToken;
                return this;
            }

            /**
             * URL of your Tanzu Observability by Wavefront installation.
             *
             * @param wavefrontUrl wavefront URL.
             * @return this
             */
            public Builder wavefrontUrl(String wavefrontUrl) {
                this.wavefrontUrl = wavefrontUrl;
                return this;
            }

            /**
             * URL of your Zipkin installation.
             *
             * @param zipkinUrl zipkin URL
             * @return this
             */
            public Builder zipkinUrl(String zipkinUrl) {
                this.zipkinUrl = zipkinUrl;
                return this;
            }

            /**
             * Name of the application grouping in Tanzu Observability by Wavefront.
             *
             * @param wavefrontApplicationName wavefront application name
             * @return this
             */
            public Builder wavefrontApplicationName(String wavefrontApplicationName) {
                this.wavefrontApplicationName = wavefrontApplicationName;
                return this;
            }

            /**
             * Name of this service in Tanzu Observability by Wavefront.
             *
             * @param wavefrontServiceName wavefront service name
             * @return this
             */
            public Builder wavefrontServiceName(String wavefrontServiceName) {
                this.wavefrontServiceName = wavefrontServiceName;
                return this;
            }

            /**
             * Name of the source to be presented in Tanzu Observability by Wavefront.
             *
             * @param wavefrontSource wavefront source
             * @return this
             */
            public Builder wavefrontSource(String wavefrontSource) {
                this.wavefrontSource = wavefrontSource;
                return this;
            }

            /**
             * Builds the configuration.
             *
             * @return built configuration
             */
            public SampleRunnerConfig build() {
                return new SampleRunnerConfig(this.wavefrontToken, this.wavefrontUrl, this.wavefrontApplicationName, this.wavefrontServiceName, this.wavefrontSource, this.zipkinUrl);
            }
        }
    }
}
