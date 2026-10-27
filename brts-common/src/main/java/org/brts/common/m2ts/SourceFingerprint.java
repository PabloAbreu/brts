/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/SourceFingerprint.java' is part of BRTS.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Streaming source identity shared by preparation, extraction and publication. */
public final class SourceFingerprint {

	private SourceFingerprint() {
	}

	public static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	/** Computes a lowercase SHA-256 without loading the media into memory. */
	public static String sha256(Path source) throws IOException {
		MessageDigest digest = newDigest();
		try (InputStream in = Files.newInputStream(source)) {
			byte[] buffer = new byte[1024 * 1024];
			int count;
			while ((count = in.read(buffer)) != -1)
				digest.update(buffer, 0, count);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/** Verifies an optional expected digest and returns the actual digest. Null is the legacy opt-out. */
	public static String verify(Path source, String expectedSha256) throws IOException {
		if (expectedSha256 != null && !expectedSha256.matches("[0-9a-fA-F]{64}"))
			throw new IllegalArgumentException(
					"Expected SHA-256 must contain exactly 64 hexadecimal characters: " + source);
		String actual = sha256(source);
		if (expectedSha256 != null && !actual.equalsIgnoreCase(expectedSha256))
			throw new IOException("Source SHA-256 mismatch: " + source);
		return actual;
	}
}
