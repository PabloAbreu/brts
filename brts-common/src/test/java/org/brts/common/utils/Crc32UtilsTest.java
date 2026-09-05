package org.brts.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Crc32UtilsTest {

	@Test
	void calculatesMpeg2Crc32() {
		byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

		assertThat(Crc32Utils.crc32(data, 0, data.length)).isEqualTo(0x0376E6E7L);
	}

	@Test
	void calculatesCrcForRequestedRange() {
		byte[] data = "prefix123456789suffix".getBytes(StandardCharsets.US_ASCII);

		assertThat(Crc32Utils.crc32(data, 6, 9)).isEqualTo(0x0376E6E7L);
	}
}