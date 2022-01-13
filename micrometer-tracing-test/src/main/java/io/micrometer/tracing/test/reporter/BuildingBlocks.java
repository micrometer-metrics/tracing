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

package io.micrometer.tracing.test.reporter;

import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;

import java.util.LinkedList;
import java.util.function.BiConsumer;

/**
 * Building blocks for reporters & tracers.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings("rawtypes")
public interface BuildingBlocks {

    /**
     * @return tracer
     */
    Tracer getTracer();

    /**
     * @return http server handler
     */
    HttpServerHandler getHttpServerHandler();

    /**
     * @return http client handler
     */
    HttpClientHandler getHttpClientHandler();

    /**
     * @return customizers
     */
    BiConsumer<BuildingBlocks, LinkedList<TimerRecordingHandler>> getCustomizers();
}
