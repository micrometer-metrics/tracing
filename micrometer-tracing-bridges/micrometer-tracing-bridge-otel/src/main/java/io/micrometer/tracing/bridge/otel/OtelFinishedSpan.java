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

package org.springframework.cloud.sleuth.otel.bridge;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;

/**
 * OpenTelemetry implementation of a {@link FinishedSpan}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelFinishedSpan implements FinishedSpan {

	private final SpanData spanData;

	private final Map<String, String> tags = new HashMap<>();

	private volatile String linkLocalIp;

	OtelFinishedSpan(SpanData spanData) {
		this.spanData = spanData;
	}

	public static FinishedSpan fromOtel(SpanData span) {
		return new OtelFinishedSpan(span);
	}

	public static SpanData toOtel(FinishedSpan span) {
		return ((OtelFinishedSpan) span).spanData;
	}

	@Override
	public String getName() {
		return this.spanData.getName();
	}

	@Override
	public long getStartTimestamp() {
		return this.spanData.getStartEpochNanos();
	}

	@Override
	public long getEndTimestamp() {
		return this.spanData.getEndEpochNanos();
	}

	@Override
	public Map<String, String> getTags() {
		if (this.tags.isEmpty()) {
			this.spanData.getAttributes().forEach((key, value) -> tags.put(key.getKey(), String.valueOf(value)));
		}
		return this.tags;
	}

	@Override
	public Collection<Map.Entry<Long, String>> getEvents() {
		return this.spanData.getEvents().stream()
				.map(e -> new AbstractMap.SimpleEntry<>(e.getEpochNanos(), e.getName())).collect(Collectors.toList());
	}

	@Override
	public String getSpanId() {
		return this.spanData.getSpanId();
	}

	@Override
	public String getParentId() {
		return this.spanData.getParentSpanId();
	}

	@Override
	public String getRemoteIp() {
		return getTags().get("net.peer.ip");
	}

	@Override
	public String getLocalIp() {
		// taken from Brave
		// uses synchronized variant of double-checked locking as getting the endpoint can
		// be expensive
		if (this.linkLocalIp != null) {
			return this.linkLocalIp;
		}
		synchronized (this) {
			if (this.linkLocalIp == null) {
				this.linkLocalIp = produceLinkLocalIp();
			}
		}
		return this.linkLocalIp;
	}

	private String produceLinkLocalIp() {
		try {
			Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
			while (nics.hasMoreElements()) {
				NetworkInterface nic = nics.nextElement();
				Enumeration<InetAddress> addresses = nic.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					if (address.isSiteLocalAddress()) {
						return address.getHostAddress();
					}
				}
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	@Override
	public int getRemotePort() {
		return Integer.parseInt(getTags().get("net.peer.port"));
	}

	@Override
	public String getTraceId() {
		return this.spanData.getTraceId();
	}

	@Override
	public Throwable getError() {
		Attributes attributes = this.spanData.getEvents().stream().filter(e -> e.getName().equals("exception"))
				.findFirst().map(EventData::getAttributes).orElse(null);
		if (attributes != null) {
			return new AssertingThrowable(attributes);
		}
		return null;
	}

	@Override
	public Span.Kind getKind() {
		if (this.spanData.getKind() == SpanKind.INTERNAL) {
			return null;
		}
		return Span.Kind.valueOf(this.spanData.getKind().name());
	}

	@Override
	public String getRemoteServiceName() {
		return this.spanData.getAttributes().get(AttributeKey.stringKey("peer.service"));
	}

	@Override
	public String toString() {
		return "SpanDataToReportedSpan{" + "spanData=" + spanData + ", tags=" + tags + '}';
	}

	/**
	 * {@link Throwable} with attributes.
	 */
	public static class AssertingThrowable extends Throwable {

		/**
		 * Attributes set on the span.
		 */
		public final Attributes attributes;

		AssertingThrowable(Attributes attributes) {
			super(attributes.get(AttributeKey.stringKey("exception.message")));
			this.attributes = attributes;
		}

	}

}
