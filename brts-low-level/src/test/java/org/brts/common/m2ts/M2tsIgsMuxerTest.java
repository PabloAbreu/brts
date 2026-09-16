package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsIgsMuxerTest {

	@TempDir
	Path tempDir;

	@Test
	void mux_rejectsSegmentWhosePesPacketLengthExceedsUnsignedShort() throws Exception {
		byte[] igs = new byte[3 + 0xFFFF];
		igs[0] = 0x15;
		igs[1] = (byte) 0xFF;
		igs[2] = (byte) 0xFF;
		Path input = tempDir.resolve("oversized.igs");
		Files.write(input, igs);

		assertThatThrownBy(() -> new M2tsIgsMuxer().mux(input, tempDir.resolve("output.m2ts")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("PES packet length 65551 exceeds maximum 65535 for OBJECT_DEFINITION segment");
	}

}