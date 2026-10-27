/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/SourceFingerprintTest.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFingerprintTest {
	@TempDir
	Path temp;

	@Test
	void knownSha256AndCaseInsensitiveVerification() throws Exception {
		Path source = Files.writeString(temp.resolve("source.m2ts"), "abc");
		String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
		assertThat(SourceFingerprint.sha256(source)).isEqualTo(expected);
		assertThat(SourceFingerprint.verify(source, expected.toUpperCase(Locale.ROOT))).isEqualTo(expected);
		assertThat(SourceFingerprint.verify(source, null)).isEqualTo(expected);
	}

	@Test
	void sameLengthChangesAndMalformedDigestsAreRejected() throws Exception {
		Path source = Files.writeString(temp.resolve("source.clpi"), "abc");
		String expected = SourceFingerprint.sha256(source);
		Files.writeString(source, "abd");
		assertThatThrownBy(() -> SourceFingerprint.verify(source, expected)).isInstanceOf(IOException.class)
				.hasMessageContaining("Source SHA-256 mismatch").hasMessageContaining(source.toString());
		for (String invalid : new String[] { "", "bad", "g".repeat(64) })
			assertThatThrownBy(() -> SourceFingerprint.verify(source, invalid))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64 hexadecimal");
	}
}
