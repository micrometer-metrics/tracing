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

package io.micrometer.tracing.docs;

import java.util.Objects;

import io.micrometer.tracing.Span;

import static java.util.Objects.requireNonNull;

class ImmutableAssertingSpan implements AssertingSpan {

    private final DocumentedSpan documentedSpan;

    private final Span delegate;

    boolean isStarted;

    ImmutableAssertingSpan(DocumentedSpan documentedSpan, Span delegate) {
        requireNonNull(documentedSpan);
        requireNonNull(delegate);
        this.documentedSpan = documentedSpan;
        this.delegate = delegate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImmutableAssertingSpan that = (ImmutableAssertingSpan) o;
        return Objects.equals(documentedSpan, that.documentedSpan) && Objects.equals(delegate, that.delegate);
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentedSpan, delegate);
    }

    @Override
    public DocumentedSpan getDocumentedSpan() {
        return this.documentedSpan;
    }

    @Override
    public Span getDelegate() {
        return this.delegate;
    }

    @Override
    public AssertingSpan start() {
        this.isStarted = true;
        return AssertingSpan.super.start();
    }

    @Override
    public boolean isStarted() {
        return this.isStarted;
    }

}
