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

package io.micrometer.tracing.internal;

import java.nio.CharBuffer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * Adopted from OpenTelemetry API.
 *
 * See
 * https://github.com/open-telemetry/opentelemetry-java/blob/v1.9.1/api/all/src/test/java/io/opentelemetry/api/internal/EncodingUtilsTest.java
 * .
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class EncodingUtilsTests {

	private static final long FIRST_LONG = 0x1213141516171819L;

	private static final char[] FIRST_CHAR_ARRAY = new char[] { '1', '2', '1', '3', '1', '4', '1', '5', '1', '6', '1',
			'7', '1', '8', '1', '9' };

	private static final long SECOND_LONG = 0xFFEEDDCCBBAA9988L;

	private static final char[] SECOND_CHAR_ARRAY = new char[] { 'f', 'f', 'e', 'e', 'd', 'd', 'c', 'c', 'b', 'b', 'a',
			'a', '9', '9', '8', '8' };

	private static final char[] BOTH_CHAR_ARRAY = new char[] { '1', '2', '1', '3', '1', '4', '1', '5', '1', '6', '1',
			'7', '1', '8', '1', '9', 'f', 'f', 'e', 'e', 'd', 'd', 'c', 'c', 'b', 'b', 'a', 'a', '9', '9', '8', '8' };

	@Test
	void longToBase16String() {
		char[] chars1 = new char[EncodingUtils.LONG_BASE16];
		EncodingUtils.longToBase16String(FIRST_LONG, chars1, 0);
		then(chars1).isEqualTo(FIRST_CHAR_ARRAY);

		char[] chars2 = new char[EncodingUtils.LONG_BASE16];
		EncodingUtils.longToBase16String(SECOND_LONG, chars2, 0);
		then(chars2).isEqualTo(SECOND_CHAR_ARRAY);

		char[] chars3 = new char[2 * EncodingUtils.LONG_BASE16];
		EncodingUtils.longToBase16String(FIRST_LONG, chars3, 0);
		EncodingUtils.longToBase16String(SECOND_LONG, chars3, EncodingUtils.LONG_BASE16);
		then(chars3).isEqualTo(BOTH_CHAR_ARRAY);
	}

	@Test
	void longFromBase16String_InputTooSmall() {
		// Valid base16 strings always have an even length.
		thenThrownBy(() -> EncodingUtils.longFromBase16String("12345678", 1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void longFromBase16String_UnrecognizedCharacters() {
		// These contain bytes not in the decoding.
		thenThrownBy(() -> EncodingUtils.longFromBase16String("0123456789gbcdef", 0))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("invalid character g");
	}

	@Test
	void validHex() {
		then(EncodingUtils.isValidBase16String("abcdef1234567890")).isTrue();
		then(EncodingUtils.isValidBase16String("abcdefg1234567890")).isFalse();
		then(EncodingUtils.isValidBase16String("<abcdef1234567890")).isFalse();
		then(EncodingUtils.isValidBase16String("(abcdef1234567890")).isFalse();
		then(EncodingUtils.isValidBase16String("abcdef1234567890B")).isFalse();
	}

	@Test
	void longFromBase16String() {
		then(EncodingUtils.longFromBase16String(CharBuffer.wrap(FIRST_CHAR_ARRAY), 0)).isEqualTo(FIRST_LONG);

		then(EncodingUtils.longFromBase16String(CharBuffer.wrap(SECOND_CHAR_ARRAY), 0)).isEqualTo(SECOND_LONG);

		then(EncodingUtils.longFromBase16String(CharBuffer.wrap(BOTH_CHAR_ARRAY), 0)).isEqualTo(FIRST_LONG);

		then(EncodingUtils.longFromBase16String(CharBuffer.wrap(BOTH_CHAR_ARRAY), EncodingUtils.LONG_BASE16))
				.isEqualTo(SECOND_LONG);

		long id = EncodingUtils.longFromBase16String("0b6aaf642574edd3dcebed0be190402d");
		// unsigned long version of hex 0b6aaf642574edd3
		then(id).isEqualTo(822662726608547283L);
	}

	@Test
	void fromStringEmptyValues() {
		then(EncodingUtils.fromString(null)).isEqualTo(new long[] { 0 });
		then(EncodingUtils.fromString("")).isEqualTo(new long[] { 0 });
	}

	@Test
	void fromString64bit() {
		long[] ids = EncodingUtils.fromString("0b6aaf642574edd3");
		then(ids).hasSize(1);
		// unsigned long version of hex 0b6aaf642574edd3
		then(ids[0]).isEqualTo(822662726608547283L);
	}

	@Test
	void fromString128bit() {
		long[] ids = EncodingUtils.fromString("0b6aaf642574edd3dcebed0be190402d");
		then(ids).hasSize(2);
		// unsigned long version of hex 0b6aaf642574edd3
		then(ids[0]).isEqualTo(822662726608547283L);
		// unsigned long version of hex dcebed0be190402d
		then(ids[1]).isEqualTo(-2527666130553651155L);
	}

	@Test
	void toFromBase16String() {
		toFromBase16StringValidate(0x8000000000000000L);
		toFromBase16StringValidate(-1);
		toFromBase16StringValidate(0);
		toFromBase16StringValidate(1);
		toFromBase16StringValidate(0x7FFFFFFFFFFFFFFFL);
	}

	private static void toFromBase16StringValidate(long value) {
		char[] dest = new char[EncodingUtils.LONG_BASE16];
		EncodingUtils.longToBase16String(value, dest, 0);
		then(EncodingUtils.longFromBase16String(CharBuffer.wrap(dest), 0)).isEqualTo(value);
	}

}
