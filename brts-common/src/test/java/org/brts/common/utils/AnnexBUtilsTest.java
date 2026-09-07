package org.brts.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class AnnexBUtilsTest {

	@Test
	void recognizesThreeAndFourByteStartCodes() {
		assertThat(AnnexBUtils.isAnnexBFormat(ByteBuffer.wrap(new byte[] { 0x00, 0x00, 0x01, 0x65 }))).isTrue();
		assertThat(AnnexBUtils.isAnnexBFormat(ByteBuffer.wrap(new byte[] { 0x00, 0x00, 0x00, 0x01, 0x65 }))).isTrue();
	}

	@Test
	void respectsBufferPositionWithoutModifyingIt() {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[] { 0x7F, 0x00, 0x00, 0x00, 0x01, 0x65 });
		buffer.position(1);

		assertThat(AnnexBUtils.isAnnexBFormat(buffer)).isTrue();
		assertThat(buffer.position()).isEqualTo(1);
	}

	@Test
	void rejectsDataWithoutAnAnnexBStartCode() {
		assertThat(AnnexBUtils.isAnnexBFormat(ByteBuffer.wrap(new byte[] { 0x00, 0x00, 0x02, 0x65 }))).isFalse();
	}
}