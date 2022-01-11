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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TestConfigAccessor;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingRecordingHandlerSpanCustomizer;
import io.micrometer.tracing.test.reporter.wavefront.WavefrontBraveSetup;
import io.micrometer.tracing.test.reporter.wavefront.WavefrontOtelSetup;
import io.micrometer.tracing.test.reporter.zipkin.ZipkinBraveSetup;
import io.micrometer.tracing.test.reporter.zipkin.ZipkinOtelSetup;
import io.micrometer.tracing.util.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import zipkin2.CheckResult;
import zipkin2.reporter.Sender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Prepares the required tracing setup and reporters / exporters. The user
 * needs to just provide the code to test and that way all the combinations
 * of tracers and exporters will be automatically applied. It also sets up the
 * {@link MeterRegistry} in such a way that it consists all {@link io.micrometer.tracing.handler.TracingRecordingHandler}
 * injected into {@link io.micrometer.core.instrument.MeterRegistry.Config}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public abstract class SampleTestRunner {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SampleTestRunner.class);

    protected final SamplerRunnerConfig samplerRunnerConfig;

    protected final MeterRegistry meterRegistry;

    private final List<TimerRecordingHandler<?>> timerRecordingHandlersCopy;

    /**
     * Creates a new instance of the {@link SampleTestRunner}.
     *
     * @param samplerRunnerConfig configuration for the SampleTestRunner
     * @param meterRegistry       provided {@link MeterRegistry} instance
     */
    public SampleTestRunner(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry) {
        this.samplerRunnerConfig = samplerRunnerConfig;
        this.meterRegistry = meterRegistry;
        this.timerRecordingHandlersCopy = new ArrayList<>(TestConfigAccessor.getHandlers(this.meterRegistry.config()));
    }

    /**
     * Creates a new instance of the {@link SampleTestRunner} with a default {@link MeterRegistry}.
     *
     * @param samplerRunnerConfig configuration for the SampleTestRunner
     */
    public SampleTestRunner(SamplerRunnerConfig samplerRunnerConfig) {
        this(samplerRunnerConfig, new SimpleMeterRegistry());
    }

    @ParameterizedTest
    @EnumSource(TracingSetup.class)
    void run(TracingSetup tracingSetup) {
        tracingSetup.run(this.samplerRunnerConfig, this.meterRegistry, this);
    }

    @AfterEach
    void setUp() {
        TestConfigAccessor.clearHandlers(this.meterRegistry.config());
        this.timerRecordingHandlersCopy.forEach(handler -> this.meterRegistry.config().timerRecordingHandler(handler));
        this.meterRegistry.clear();
    }

    @AfterEach
    void printMetrics() {
        StringBuilder stringBuilder = new StringBuilder();
        this.meterRegistry.forEachMeter(meter -> stringBuilder.append("\tMeter with name <")
                .append(meter.getId().getName()).append(">")
                .append(" and type <").append(meter.getId().getType()).append(">")
                .append(" has the following measurements \n\t\t<").append(meter.measure()).append(">")
                .append(" \n\t\tand has the following tags <")
                .append(meter.getId().getTags()).append(">\n"));
        log.info("Gathered the following metrics\n" + stringBuilder);
        this.meterRegistry.close();
    }

    /**
     * Code that you want to measure and run.
     *
     * @return your code with access to the current tracing and measuring infrastructure
     */
    public abstract BiConsumer<Tracer, MeterRegistry> yourCode();

    /**
     * Override this to add additional span customizers.
     *
     * @return span customizers
     */
    public List<TracingRecordingHandlerSpanCustomizer> getTracingRecordingHandlerSpanCustomizers() {
        return new ArrayList<>();
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
         * Zipkin Exporter with OTel Tracer.
         */
        ZIPKIN_OTEL {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(ZIPKIN_OTEL, sampleTestRunner.getTracingSetup());
                ZipkinOtelSetup setup = ZipkinOtelSetup.builder()
                        .tracingRecordingHandlerSpanCustomizers(sampleTestRunner.getTracingRecordingHandlerSpanCustomizers()).zipkinUrl(samplerRunnerConfig.zipkinUrl).register(meterRegistry);
                checkZipkinAssumptions(setup.getBuildingBlocks().sender);
                ZipkinOtelSetup.run(setup, __ -> runTraced(samplerRunnerConfig, ZIPKIN_OTEL, setup.getBuildingBlocks().otelTracer, meterRegistry, sampleTestRunner.yourCode()));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Zipkin with id <{}>", traceId);
                log.info("{}/zipkin/traces/{}", samplerRunnerConfig.zipkinUrl, traceId);
            }
        },

        /**
         * Zipkin Reporter with Brave Tracer.
         */
        ZIPKIN_BRAVE {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(ZIPKIN_BRAVE, sampleTestRunner.getTracingSetup());
                ZipkinBraveSetup setup = ZipkinBraveSetup.builder()
                        .tracingRecordingHandlerSpanCustomizers(sampleTestRunner.getTracingRecordingHandlerSpanCustomizers()).zipkinUrl(samplerRunnerConfig.zipkinUrl).register(meterRegistry);
                checkZipkinAssumptions(setup.getBuildingBlocks().sender);
                ZipkinBraveSetup.run(setup, __ -> runTraced(samplerRunnerConfig, ZIPKIN_BRAVE, setup.getBuildingBlocks().tracer, meterRegistry, sampleTestRunner.yourCode()));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Zipkin with id <{}>", traceId);
                log.info("{}/zipkin/traces/{}", samplerRunnerConfig.zipkinUrl, traceId);
            }
        },

        /**
         * Wavefront Exporter with OTel Tracer.
         */
        WAVEFRONT_OTEL {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(WAVEFRONT_OTEL, sampleTestRunner.getTracingSetup());
                checkWavefrontAssumptions(samplerRunnerConfig);
                WavefrontOtelSetup setup = WavefrontOtelSetup.builder(samplerRunnerConfig.wavefrontServerUrl, samplerRunnerConfig.wavefrontToken)
                        .applicationName(samplerRunnerConfig.wavefrontApplicationName)
                        .serviceName(samplerRunnerConfig.wavefrontServiceName)
                        .source(samplerRunnerConfig.wavefrontSource)
                        .tracingRecordingHandlerSpanCustomizers(sampleTestRunner.getTracingRecordingHandlerSpanCustomizers())
                        .register(meterRegistry);
                WavefrontOtelSetup.run(setup, __ -> runTraced(samplerRunnerConfig, WAVEFRONT_OTEL, setup.getBuildingBlocks().otelTracer, meterRegistry, sampleTestRunner.yourCode()));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Wavefront with id <{}>", traceId);
                String wavefrontUrl = samplerRunnerConfig.wavefrontServerUrl.endsWith("/") ? samplerRunnerConfig.wavefrontServerUrl.substring(0, samplerRunnerConfig.wavefrontServerUrl.length() - 1) : samplerRunnerConfig.wavefrontServerUrl;
                log.info("{}/tracing/search?sortBy=MOST_RECENT&traceID={}", wavefrontUrl, traceId);
            }
        },

        /**
         * Wavefront Exporter with Brave Tracer.
         */
        WAVEFRONT_BRAVE {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner) {
                checkTracingSetupAssumptions(WAVEFRONT_BRAVE, sampleTestRunner.getTracingSetup());
                checkWavefrontAssumptions(samplerRunnerConfig);
                WavefrontBraveSetup setup = WavefrontBraveSetup.builder(samplerRunnerConfig.wavefrontServerUrl, samplerRunnerConfig.wavefrontToken)
                        .applicationName(samplerRunnerConfig.wavefrontApplicationName)
                        .serviceName(samplerRunnerConfig.wavefrontServiceName)
                        .source(samplerRunnerConfig.wavefrontSource)
                        .tracingRecordingHandlerSpanCustomizers(sampleTestRunner.getTracingRecordingHandlerSpanCustomizers())
                        .register(meterRegistry);
                WavefrontBraveSetup.run(setup, __ -> runTraced(samplerRunnerConfig, WAVEFRONT_BRAVE, setup.getBuildingBlocks().tracer, meterRegistry, sampleTestRunner.yourCode()));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Wavefront with id <{}>", traceId);
                String wavefrontUrl = samplerRunnerConfig.wavefrontServerUrl.endsWith("/") ? samplerRunnerConfig.wavefrontServerUrl.substring(0, samplerRunnerConfig.wavefrontServerUrl.length() - 1) : samplerRunnerConfig.wavefrontServerUrl;
                log.info("{}/tracing/search?sortBy=MOST_RECENT&traceID={}", wavefrontUrl, traceId);
            }
        };

        private static void runTraced(SamplerRunnerConfig samplerRunnerConfig, TracingSetup tracingSetup, Tracer tracer, MeterRegistry meterRegistry, BiConsumer<Tracer, MeterRegistry> runnable) {
            Span span = tracer.nextSpan().name(tracingSetup.name());
            String traceId = span.context().traceId();
            try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
                runnable.accept(tracer, meterRegistry);
            } finally {
                tracingSetup.printTracingLink(samplerRunnerConfig, traceId);
                span.end();
            }
        }

        private static void checkTracingSetupAssumptions(TracingSetup tracingSetup, TracingSetup[] chosenSetups) {
            Assumptions.assumeTrue(Arrays.asList(chosenSetups).contains(tracingSetup), tracingSetup.name() + " not found in the list of tracing setups to run " + Arrays.toString(chosenSetups));
        }

        private static void checkZipkinAssumptions(Sender sender) {
            CheckResult checkResult = sender.check();
            Assumptions.assumeTrue(checkResult.ok(), "There was a problem with connecting to Zipkin. Will NOT run any tests");
        }

        private static void checkWavefrontAssumptions(SamplerRunnerConfig samplerRunnerConfig) {
            Assumptions.assumeTrue(StringUtils.isNotBlank(samplerRunnerConfig.wavefrontServerUrl), "To run tests against Tanzu Observability by Wavefront you need to set the Wavefront server url");
            Assumptions.assumeTrue(StringUtils.isNotBlank(samplerRunnerConfig.wavefrontToken), "To run tests against Tanzu Observability by Wavefront you need to set the Wavefront token");
        }

        abstract void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, SampleTestRunner sampleTestRunner);

        abstract void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId);
    }

    /**
     * {@link SampleTestRunner} configuration.
     */
    public static class SamplerRunnerConfig {

        public String wavefrontToken;

        public String wavefrontServerUrl;

        public String zipkinUrl;

        public String wavefrontApplicationName;

        public String wavefrontServiceName;

        public String wavefrontSource;

        SamplerRunnerConfig(String wavefrontToken, String wavefrontServerUrl, String wavefrontApplicationName, String wavefrontServiceName, String wavefrontSource, String zipkinUrl) {
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
         * Builder for {@link SamplerRunnerConfig}.
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
            public SampleTestRunner.SamplerRunnerConfig build() {
                return new SampleTestRunner.SamplerRunnerConfig(this.wavefrontToken, this.wavefrontUrl, this.wavefrontApplicationName, this.wavefrontServiceName, this.wavefrontSource, this.zipkinUrl);
            }
        }
    }
}
