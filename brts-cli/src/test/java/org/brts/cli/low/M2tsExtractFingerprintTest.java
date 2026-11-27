/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/M2tsExtractFingerprintTest.java' is part of BRTS.
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

package org.brts.cli.low;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.m2ts.SourceFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.args4j.CmdLineParser;

class M2tsExtractFingerprintTest {
	@TempDir
	Path temp;

	@Test
	void staleM2tsAndClpiFailBeforeParsingOrCreatingOutput() throws Exception {
		Path input = Files.writeString(temp.resolve("source.m2ts"), "original");
		Path clpi = Files.writeString(temp.resolve("source.clpi"), "original");
		String m2tsDigest = SourceFingerprint.sha256(input);
		String clpiDigest = SourceFingerprint.sha256(clpi);
		for (Path changed : new Path[] { input, clpi }) {
			Files.writeString(changed, "modified");
			var opts = options(input, clpi, m2tsDigest, clpiDigest);
			assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IOException.class)
					.hasMessageContaining("SHA-256 mismatch").hasMessageContaining(changed.toString());
			assertThat(opts.outputDir.toPath()).doesNotExist();
			Files.writeString(changed, "original");
		}
	}

	@Test
	void clpiPathAndDigestMustBeSuppliedTogether() throws Exception {
		Path input = Files.writeString(temp.resolve("source.m2ts"), "unparsed");
		var opts = options(input, input, null, "0".repeat(64));
		opts.sourceClpi = null;
		assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be supplied together");
		opts.sourceClpi = input.toFile();
		opts.sourceClpiSha256 = null;
		assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IllegalArgumentException.class);
		assertThat(opts.outputDir.toPath()).doesNotExist();
	}

	@Test
	void extractionOptionsParseWithoutChangingDefaults() throws Exception {
		var opts = new M2tsCli.ExtractOptions();
		new CmdLineParser(opts).parseArgument("--input", "source.m2ts", "--output", "out", "--source-sha256",
				"a".repeat(64), "--source-clpi", "source.clpi", "--source-clpi-sha256", "b".repeat(64));
		assertThat(opts.sourceSha256).isEqualTo("a".repeat(64));
		assertThat(opts.sourceClpiSha256).isEqualTo("b".repeat(64));
		assertThat(opts.preserveTiming).isFalse();
	}

	private M2tsCli.ExtractOptions options(Path input, Path clpi, String sourceDigest, String clpiDigest) {
		var opts = new M2tsCli.ExtractOptions();
		opts.input = input.toFile();
		opts.outputDir = temp.resolve("out").toFile();
		opts.sourceSha256 = sourceDigest;
		opts.sourceClpi = clpi.toFile();
		opts.sourceClpiSha256 = clpiDigest;
		return opts;
	}
}
