/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.tracing.annotation;

import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.internal.SpanNameUtil;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of the {@link NewSpanParser} that parses only the span name.
 *
 * Code ported from Spring Cloud Sleuth.
 *
 * @author Christian Schwerdtfeger
 * @since 1.1.0
 */
public class DefaultNewSpanParser implements NewSpanParser {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(DefaultNewSpanParser.class);

    @Override
    public void parse(MethodInvocation pjp, @Nullable NewSpan newSpan, Span span) {
        String name = spanName(newSpan, pjp);
        String changedName = SpanNameUtil.toLowerHyphen(name);
        if (log.isDebugEnabled()) {
            log.debug("For the class [" + pjp.getThis().getClass() + "] method " + "[" + pjp.getMethod().getName()
                    + "] will name the span [" + changedName + "]");
        }
        span.name(changedName);
    }

    private String spanName(@Nullable NewSpan newSpan, MethodInvocation pjp) {
        if (newSpan == null) {
            return pjp.getMethod().getName();
        }
        String name = newSpan.name();
        String value = newSpan.value();
        boolean nameEmpty = StringUtils.isEmpty(name);
        if (nameEmpty && StringUtils.isEmpty(value)) {
            return pjp.getMethod().getName();
        }
        return nameEmpty ? value : name;
    }

}
