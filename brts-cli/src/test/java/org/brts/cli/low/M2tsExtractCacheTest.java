/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/M2tsExtractCacheTest.java' is part of BRTS.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.TargetRootWorkspace;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

class M2tsExtractCacheTest {

	@TempDir
	Path temp;

	@Test
	void validatesPortableCacheWithoutParsingFakeMediaOrWritingAnything() throws Exception {
		var opts = fixture();
		var before = identities();
		new M2tsCli.Extract().execute(opts);
		assertThat(identities()).isEqualTo(before);
		// Input and output are identity assertions, not a request for another extraction.
		Path sourceAlias = Files.createSymbolicLink(temp.resolve("source-alias.m2ts"), opts.input.toPath());
		Path outputAlias = Files.createSymbolicLink(temp.resolve("cache-alias"), opts.outputDir.toPath());
		opts.input = sourceAlias.toFile();
		opts.outputDir = outputAlias.toFile();
		new M2tsCli.Extract().execute(opts);
		assertThat(ExtractionCache.validate(
				JsonMapperFactory.get().readValue(opts.cacheReference.toFile(), ExtractionCache.Reference.class)))
				.isNotNull();
	}

	@Test
	void requiresExplicitCacheValidationAndPreservationTogether() {
		List<Consumer<M2tsCli.ExtractOptions>> combinations = List.of(
				o -> o.cacheReference = temp.resolve("missing-reference.json"), o -> o.validateCacheOnly = true, o -> {
					o.cacheReference = temp.resolve("missing-reference.json");
					o.validateCacheOnly = true;
				}, o -> {
					o.cacheReference = temp.resolve("missing-reference.json");
					o.preserveTiming = true;
				}, o -> {
					o.validateCacheOnly = true;
					o.preserveTiming = true;
				});
		for (var combination : combinations) {
			var opts = new M2tsCli.ExtractOptions();
			opts.input = temp.resolve("missing.m2ts").toFile();
			opts.outputDir = temp.resolve("not-created").toFile();
			combination.accept(opts);
			assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("required together");
			assertThat(opts.outputDir.toPath()).doesNotExist();
		}
	}

	@Test
	void rejectsEveryExtractionAndFingerprintOverrideBeforeLoadingReference() {
		List<Consumer<M2tsCli.ExtractOptions>> incompatible = List.of(o -> o.pids = "", o -> o.igsDemux = true,
				o -> o.sourceSha256 = "a".repeat(64), o -> o.sourceClpi = temp.resolve("source.clpi").toFile(),
				o -> o.sourceClpiSha256 = "b".repeat(64));
		for (var option : incompatible) {
			var opts = new M2tsCli.ExtractOptions();
			opts.input = temp.resolve("missing.m2ts").toFile();
			opts.outputDir = temp.resolve("not-created").toFile();
			opts.cacheReference = temp.resolve("missing-reference.json");
			opts.validateCacheOnly = true;
			opts.preserveTiming = true;
			option.accept(opts);
			assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("does not permit");
			assertThat(opts.outputDir.toPath()).doesNotExist();
		}
	}

	@Test
	void rejectsDifferentSourceOrOutputAndNeverCreatesMissingOutput() throws Exception {
		var opts = fixture();
		var originalInput = opts.input;
		opts.input = Files.write(temp.resolve("other.m2ts"), new byte[192]).toFile();
		assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must match");
		opts.input = originalInput;
		opts.outputDir = Files.createDirectory(temp.resolve("other-output")).toFile();
		assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must match");
		opts.outputDir = temp.resolve("not-created").toFile();
		assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IOException.class);
		assertThat(opts.outputDir.toPath()).doesNotExist();
	}

	@Test
	void rejectsStaleCacheRatherThanReextractingOrRepairingIt() throws Exception {
		var opts = fixture();
		Path elementary = opts.outputDir.toPath().resolve("pid_1011.h264");
		Files.writeString(elementary, "changed");
		var before = identities();
		assertThatThrownBy(() -> new M2tsCli.Extract().execute(opts)).isInstanceOf(IOException.class)
				.hasMessageContaining("Invalid extraction cache");
		assertThat(identities()).isEqualTo(before);
	}

	@Test
	void parsesPathReferenceAndRetainsRequiredInputAndOutput() throws Exception {
		var opts = new M2tsCli.ExtractOptions();
		new CmdLineParser(opts).parseArgument("--input", "source.m2ts", "--output", "cache", "--preserve-timing",
				"--cache-reference", "reference.json", "--validate-cache-only");
		assertThat(opts.cacheReference).isEqualTo(Path.of("reference.json"));
		assertThat(opts.validateCacheOnly).isTrue();
		assertThat(opts.preserveTiming).isTrue();
		assertThatThrownBy(() -> new CmdLineParser(new M2tsCli.ExtractOptions()).parseArgument("--cache-reference",
				"reference.json", "--validate-cache-only", "--preserve-timing")).isInstanceOf(CmdLineException.class);
		var defaults = new M2tsCli.ExtractOptions();
		new CmdLineParser(defaults).parseArgument("--input", "source.m2ts", "--output", "out");
		assertThat(defaults.cacheReference).isNull();
		assertThat(defaults.validateCacheOnly).isFalse();
	}

	private M2tsCli.ExtractOptions fixture() throws IOException {
		Path sourceRoot = Files.createDirectory(temp.resolve("source"));
		Path target = TargetRootWorkspace.prepare(sourceRoot, temp.resolve("target"), null);
		// Deliberately not valid transport/CLPI/AVC bytes: validation must use only saved metadata and attributes.
		Path source = Files.write(sourceRoot.resolve("00001.m2ts"), new byte[192]).toRealPath();
		Path clpi = Files.writeString(sourceRoot.resolve("00001.clpi"), "not parsed");
		Path directory = Files.createDirectory(target.resolve("cache"));
		Path elementary = Files.writeString(directory.resolve("pid_1011.h264"), "not parsed either");
		var stream = new M2tsStreamInfo();
		stream.setPid(0x1011);
		stream.setCodingType(StreamCodingType.H264_AVC);
		stream.setStreamTypeByte(0x1b);
		var info = new M2tsInfo();
		info.setSourcePath(source.toString());
		info.setTotalPackets(1);
		info.setStreams(List.of(stream));
		Path timing = directory.resolve(M2tsTimingManifest.FILE_NAME);
		M2tsTiming.write(timing,
				new M2tsTimingManifest(2, source.toString(), 192, null,
						List.of(new StreamTiming(0x1011, elementary.getFileName().toString(), StreamCodingType.H264_AVC,
								0L, null, List.of(new PesTiming(0, 0, 0L, null)), List.of())),
						null, Map.of(0x1011, 1L)));
		Path marker = directory.resolve(ExtractionCache.FILE_NAME);
		ExtractionCache.write(marker,
				new ExtractionCache.Manifest(ExtractionCache.VERSION, target.toString(),
						ExtractionCache.identity(source), ExtractionCache.identity(clpi), info, List.of(0x1011),
						Map.of(elementary.getFileName().toString(), relative(elementary), M2tsTimingManifest.FILE_NAME,
								relative(timing)),
						90_000));
		Path reference = temp.resolve("reference.json");
		JsonMapperFactory.get().writeValue(reference.toFile(), ExtractionCache.reference(marker));
		var opts = new M2tsCli.ExtractOptions();
		opts.input = source.toFile();
		opts.outputDir = directory.toFile();
		opts.cacheReference = reference;
		opts.validateCacheOnly = true;
		opts.preserveTiming = true;
		return opts;
	}

	private static ExtractionCache.Identity relative(Path path) throws IOException {
		var identity = ExtractionCache.identity(path);
		return new ExtractionCache.Identity(path.getFileName().toString(), identity.size(), identity.modified(),
				identity.fileKey());
	}

	private Map<Path, ExtractionCache.Identity> identities() throws IOException {
		var result = new HashMap<Path, ExtractionCache.Identity>();
		try (var paths = Files.walk(temp)) {
			for (Path path : paths.filter(Files::isRegularFile).toList())
				result.put(path, ExtractionCache.identity(path));
		}
		return result;
	}
}
