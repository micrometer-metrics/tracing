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

import java.util.function.BiConsumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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

/**
 *
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public abstract class SampleTestRunner {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(SampleTestRunner.class);

    protected final SamplerRunnerConfig samplerRunnerConfig;

    protected final MeterRegistry meterRegistry;

    public SampleTestRunner(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry) {
        this.samplerRunnerConfig = samplerRunnerConfig;
        this.meterRegistry = meterRegistry;
    }

    public SampleTestRunner(SamplerRunnerConfig samplerRunnerConfig) {
        this(samplerRunnerConfig, new SimpleMeterRegistry());
    }

    @ParameterizedTest
    @EnumSource(TracingSetup.class)
    void run(TracingSetup tracingSetup) {
        tracingSetup.run(this.samplerRunnerConfig, this.meterRegistry, yourCode());
    }

    @AfterEach
    void printMetrics() {
        StringBuilder stringBuilder = new StringBuilder();
        this.meterRegistry.forEachMeter(meter -> stringBuilder.append("\tMeter with name <").append(meter.getId().getName()).append("> has the following tags ").append(meter.getId().getTags()).append("\n"));
        log.info("Gathered the following metrics\n" + stringBuilder);
        this.meterRegistry.close();
    }

    public abstract BiConsumer<Tracer, MeterRegistry> yourCode();

    public enum TracingSetup {
        ZIPKIN_OTEL {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, BiConsumer<Tracer, MeterRegistry> runnable) {
                ZipkinOtelSetup setup = ZipkinOtelSetup.builder().zipkinUrl(samplerRunnerConfig.zipkinUrl).register(meterRegistry);
                checkZipkinAssumptions(setup.getBuildingBlocks().sender);
                ZipkinOtelSetup.run(setup, __ -> runTraced(samplerRunnerConfig, ZIPKIN_OTEL, setup.getBuildingBlocks().otelTracer, meterRegistry, runnable));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Zipkin with id <{}>", traceId);
                log.info("{}/zipkin/traces/{}", samplerRunnerConfig.zipkinUrl, traceId);
            }
        },

        ZIPKIN_BRAVE {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, BiConsumer<Tracer, MeterRegistry> runnable) {
                ZipkinBraveSetup setup = ZipkinBraveSetup.builder().zipkinUrl(samplerRunnerConfig.zipkinUrl).register(meterRegistry);
                checkZipkinAssumptions(setup.getBuildingBlocks().sender);
                ZipkinBraveSetup.run(setup, __ -> runTraced(samplerRunnerConfig, ZIPKIN_BRAVE, setup.getBuildingBlocks().tracer, meterRegistry, runnable));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Zipkin with id <{}>", traceId);
                log.info("{}/zipkin/traces/{}", samplerRunnerConfig.zipkinUrl, traceId);
            }
        },

        WAVEFRONT_OTEL {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, BiConsumer<Tracer, MeterRegistry> runnable) {
                checkWavefrontAssumptions(samplerRunnerConfig);
                WavefrontOtelSetup setup = WavefrontOtelSetup.builder(samplerRunnerConfig.wavefrontServerUrl, samplerRunnerConfig.wavefrontToken)
                        .applicationName(samplerRunnerConfig.wavefrontApplicationName)
                        .serviceName(samplerRunnerConfig.wavefrontServiceName)
                        .source(samplerRunnerConfig.wavefrontSource)
                        .register(meterRegistry);
                WavefrontOtelSetup.run(setup, __ -> runTraced(samplerRunnerConfig, WAVEFRONT_OTEL, setup.getBuildingBlocks().otelTracer, meterRegistry, runnable));
            }

            @Override
            void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId) {
                log.info("Below you can find the link to the trace in Wavefront with id <{}>", traceId);
                String wavefrontUrl = samplerRunnerConfig.wavefrontServerUrl.endsWith("/") ? samplerRunnerConfig.wavefrontServerUrl.substring(0, samplerRunnerConfig.wavefrontServerUrl.length() - 1) : samplerRunnerConfig.wavefrontServerUrl;
                log.info("{}/tracing/search?sortBy=MOST_RECENT&traceID={}", wavefrontUrl, traceId);
            }
        },

        WAVEFRONT_BRAVE {
            @Override
            void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, BiConsumer<Tracer, MeterRegistry> runnable) {
                checkWavefrontAssumptions(samplerRunnerConfig);
                WavefrontBraveSetup setup = WavefrontBraveSetup.builder(samplerRunnerConfig.wavefrontServerUrl, samplerRunnerConfig.wavefrontToken)
                        .applicationName(samplerRunnerConfig.wavefrontApplicationName)
                        .serviceName(samplerRunnerConfig.wavefrontServiceName)
                        .source(samplerRunnerConfig.wavefrontSource)
                        .register(meterRegistry);
                WavefrontBraveSetup.run(setup, __ -> runTraced(samplerRunnerConfig, WAVEFRONT_BRAVE, setup.getBuildingBlocks().tracer, meterRegistry, runnable));
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
            }
            finally {
                tracingSetup.printTracingLink(samplerRunnerConfig, traceId);
                span.end();
            }
        }

        private static void checkZipkinAssumptions(Sender sender) {
            CheckResult checkResult = sender.check();
            Assumptions.assumeTrue(checkResult.ok(), "There was a problem with connecting to Zipkin. Will NOT run any tests");
        }

        private static void checkWavefrontAssumptions(SamplerRunnerConfig samplerRunnerConfig) {
            Assumptions.assumeTrue(StringUtils.isNotBlank(samplerRunnerConfig.wavefrontServerUrl), "To run tests against Tanzu Observability by Wavefront you need to set the Wavefront server url");
            Assumptions.assumeTrue(StringUtils.isNotBlank(samplerRunnerConfig.wavefrontToken), "To run tests against Tanzu Observability by Wavefront you need to set the Wavefront token");
        }

        abstract void run(SamplerRunnerConfig samplerRunnerConfig, MeterRegistry meterRegistry, BiConsumer<Tracer, MeterRegistry> runnable);

        abstract void printTracingLink(SamplerRunnerConfig samplerRunnerConfig, String traceId);
    }

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

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String wavefrontToken;

            private String wavefrontUrl;

            private String wavefrontApplicationName;

            private String wavefrontServiceName;

            private String wavefrontSource;

            private String zipkinUrl;

            public Builder wavefrontToken(String wavefrontToken) {
                this.wavefrontToken = wavefrontToken;
                return this;
            }

            public Builder wavefrontUrl(String wavefrontUrl) {
                this.wavefrontUrl = wavefrontUrl;
                return this;
            }

            public Builder zipkinUrl(String zipkinUrl) {
                this.zipkinUrl = zipkinUrl;
                return this;
            }

            public Builder wavefrontApplicationName(String wavefrontApplicationName) {
                this.wavefrontApplicationName = wavefrontApplicationName;
                return this;
            }

            public Builder wavefrontServiceName(String wavefrontServiceName) {
                this.wavefrontServiceName = wavefrontServiceName;
                return this;
            }

            public Builder wavefrontSource(String wavefrontSource) {
                this.wavefrontSource = wavefrontSource;
                return this;
            }

            public SampleTestRunner.SamplerRunnerConfig build() {
                return new SampleTestRunner.SamplerRunnerConfig(this.wavefrontToken, this.wavefrontUrl, this.wavefrontApplicationName, this.wavefrontServiceName, this.wavefrontSource, this.zipkinUrl);
            }
        }
    }
}
