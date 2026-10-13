/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/VideoConverterExecutionTest.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */
package org.brts.lowlevel.utils.video;

import static org.brts.lowlevel.utils.video.VideoTestSupport.completed;
import static org.brts.lowlevel.utils.video.VideoTestSupport.preview;
import static org.brts.lowlevel.utils.video.VideoTestSupport.request;
import static org.brts.lowlevel.utils.video.VideoTestSupport.sdr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.VideoConversionResult.Compliance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class VideoConverterExecutionTest {

	@TempDir
	Path directory;

	private void executable(VideoConversionParams params) throws IOException {
		Path binary = Files.writeString(directory.resolve("fake encoder with spaces"), "never executed");
		assertTrue(binary.toFile().setExecutable(true));
		params.setExecutablePath(binary.toString());
	}

	private void assertNoTemporaryFiles() throws IOException {
		try (var paths = Files.list(directory)) {
			assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".brts-video-")));
		}
	}

	@Test
	void fakeConversionPublishesOnlyValidatedSiblingOutputAndNeverChangesInput() throws Exception {
		var params = request(directory);
		executable(params);
		Process process = completed(0, "encoded frames\n", "");
		List<Path> probes = new ArrayList<>();
		AtomicReference<List<String>> launched = new AtomicReference<>();
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			probes.add(path);
			if (!path.equals(params.getInputPath())) {
				assertFalse(Files.exists(params.getOutputPath()),
						"Output cannot be published before metadata validation");
			}
			return sdr(1920, 1080, FrameRate.FPS_24);
		}, new EncoderProcessRunner(command -> {
			launched.set(command);
			Files.writeString(Path.of(command.getLast()), "tiny encoded content");
			return process;
		}));
		var result = converter.convert(params);
		assertEquals("tiny encoded content", Files.readString(params.getOutputPath()));
		assertEquals("not real video", Files.readString(params.getInputPath()));
		assertEquals(params.getOutputPath(), result.outputPath());
		assertEquals(Compliance.TARGETED, result.compliance());
		assertTrue(result.warnings().isEmpty());
		assertEquals(2, probes.size());
		assertNotEquals(result.outputPath().toString(), launched.get().getLast());
		assertEquals(directory, Path.of(launched.get().getLast()).getParent());
		assertTrue(launched.get().getLast().endsWith(".264"));
		assertEquals(result.plan().outputPath().toString(), result.plan().command().getLast());
		assertThrows(UnsupportedOperationException.class, () -> launched.get().add("evil"));
		assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("evil"));
		assertNoTemporaryFiles();
	}

	@Test
	void overwritePublishesNewBytesOnlyAfterSuccessfulValidation() throws Exception {
		var params = request(directory);
		executable(params);
		params.setOverwrite(true);
		Files.writeString(params.getOutputPath(), "original");
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			assertEquals("original", Files.readString(params.getOutputPath()));
			return sdr(1920, 1080, FrameRate.FPS_24);
		}, new EncoderProcessRunner(command -> {
			Files.writeString(Path.of(command.getLast()), "replacement");
			return process;
		}));
		converter.convert(params);
		assertEquals("replacement", Files.readString(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@Test
	void processDowngradeWarningsMakeResultBestEffortAndAreImmutable() throws Exception {
		var params = request(directory);
		executable(params);
		Process process = completed(0, "", "uhd-bd disabled by encoder\nuhd-bd disabled by encoder\n");
		var converter = new VideoConverter((key, fallback) -> fallback, path -> sdr(1920, 1080, FrameRate.FPS_24),
				new EncoderProcessRunner(command -> {
					Files.writeString(Path.of(command.getLast()), "success");
					return process;
				}));
		var result = converter.convert(params);
		assertEquals(Compliance.BEST_EFFORT, result.compliance());
		assertEquals(1, result.warnings().size(), "Conversion result deduplicates repeated diagnostics");
		assertTrue(result.warnings().getFirst().contains("disabled"));
		assertThrows(UnsupportedOperationException.class, () -> result.warnings().clear());
		assertNoTemporaryFiles();
	}

	@Test
	void nonzeroExitKeepsOldOutputAndDeletesOwnedTemporary() throws Exception {
		var params = request(directory);
		executable(params);
		params.setOverwrite(true);
		Files.writeString(params.getOutputPath(), "original");
		Process process = completed(7, "partial frames", "encoder cannot continue");
		AtomicInteger probes = new AtomicInteger();
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			probes.incrementAndGet();
			return sdr(1920, 1080, FrameRate.FPS_24);
		}, new EncoderProcessRunner(command -> {
			Files.writeString(Path.of(command.getLast()), "partial replacement");
			return process;
		}));
		IOException failure = assertThrows(IOException.class, () -> converter.convert(params));
		assertTrue(failure.getMessage().contains("status 7"));
		assertTrue(failure.getMessage().contains("encoder cannot continue"));
		assertEquals(1, probes.get(), "Failed process output is never probed");
		assertEquals("original", Files.readString(params.getOutputPath()));
		assertEquals("not real video", Files.readString(params.getInputPath()));
		assertNoTemporaryFiles();
	}

	@ParameterizedTest
	@ValueSource(strings = { "codec", "width", "height", "progressive", "timing", "sar", "range", "primaries",
			"transfer", "matrix", "missing-timing" })
	void metadataMismatchFailsWithoutPublishingOrDamagingOldOutput(String mismatch) throws Exception {
		var params = request(directory);
		executable(params);
		params.setOverwrite(true);
		Files.writeString(params.getOutputPath(), "original");
		var output = new VideoStreamInfo(mismatch.equals("width") ? 1280 : 1920, mismatch.equals("height") ? 720 : 1080,
				mismatch.equals("missing-timing") ? null
						: mismatch.equals("timing") ? FrameRate.FPS_23976 : FrameRate.FPS_24,
				mismatch.equals("sar") ? 4 : 1, 1, !mismatch.equals("progressive"),
				mismatch.equals("codec") ? "hevc" : "h264", mismatch.equals("primaries") ? "bt2020" : "bt709",
				mismatch.equals("transfer") ? "smpte2084" : "bt709", mismatch.equals("matrix") ? "bt2020nc" : "bt709",
				mismatch.equals("range"), null, null, 8, 1, 1, 100, 41, 1, true);
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback,
				path -> path.equals(params.getInputPath()) ? sdr(1920, 1080, FrameRate.FPS_24) : output,
				new EncoderProcessRunner(command -> {
					Files.writeString(Path.of(command.getLast()), "invalid output");
					return process;
				}));
		assertThrows(IOException.class, () -> converter.convert(params));
		assertEquals("original", Files.readString(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@ParameterizedTest
	@ValueSource(strings = { "master-display", "max-cll" })
	void staticHdrMetadataMismatchFailsValidation(String mismatch) throws Exception {
		var params = request(directory);
		executable(params);
		params.setCodec(StreamCodingType.H265_HEVC);
		String mastering = "G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,50)";
		var source = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "hevc", "bt2020", "smpte2084",
				"bt2020nc", false, mastering, "1000,400");
		var output = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "hevc", "bt2020", "smpte2084",
				"bt2020nc", false, mismatch.equals("master-display") ? null : mastering,
				mismatch.equals("max-cll") ? null : "1000,400", 10, 1, 1, 2, 153, 3, true);
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback,
				path -> path.equals(params.getInputPath()) ? source : output, new EncoderProcessRunner(command -> {
					assertTrue(command.getLast().endsWith(".hevc"));
					Files.writeString(Path.of(command.getLast()), "invalid HDR");
					return process;
				}));
		IOException failure = assertThrows(IOException.class, () -> converter.convert(params));
		assertTrue(failure.getMessage().contains("HDR10"));
		assertFalse(Files.exists(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	private static VideoStreamInfo encodedMetadata(String format, String mismatch) {
		boolean hevc = !format.equals("avc");
		boolean hdr = format.equals("hdr10");
		boolean bt2020 = hdr || format.equals("bt2020-sdr");
		String primaries = bt2020 ? "bt2020" : "bt709";
		String transfer = hdr ? "smpte2084" : "bt709";
		String matrix = bt2020 ? "bt2020nc" : "bt709";
		String mastering = hdr ? "G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,50)" : null;
		String maxCll = hdr ? "1000,400" : null;
		if (mismatch.equals("legacy")) {
			return new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, hevc ? "hevc" : "h264", primaries,
					transfer, matrix, false, mastering, maxCll);
		}
		return new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, hevc ? "hevc" : "h264", primaries,
				transfer, matrix, mismatch.equals("full-range"), mastering, maxCll,
				mismatch.equals("missing-depth") ? -1
						: mismatch.equals("wrong-depth") ? (hevc ? 8 : 10) : (hevc ? 10 : 8),
				mismatch.equals("missing-chroma-width") ? -1 : mismatch.equals("wrong-chroma-width") ? 0 : 1,
				mismatch.equals("missing-chroma-height") ? -1 : mismatch.equals("wrong-chroma-height") ? 0 : 1,
				mismatch.equals("missing-profile") ? -1
						: mismatch.equals("wrong-profile") ? (hevc ? 1 : 77) : (hevc ? 2 : 100),
				mismatch.equals("missing-level") ? -1
						: mismatch.equals("lower-level") ? (hevc ? 150 : 40)
								: mismatch.equals("higher-level") ? (hevc ? 156 : 42) : (hevc ? 153 : 41),
				mismatch.equals("missing-chroma-location") ? -1
						: mismatch.equals("unspecified-chroma-location") ? 0
								: mismatch.equals("wrong-chroma-location") ? (bt2020 ? 1 : 3) : (bt2020 ? 3 : 1),
				!mismatch.equals("unknown-range"));
	}

	static Stream<Arguments> invalidOutputMetadata() {
		return Stream.of("avc", "hevc", "hdr10", "bt2020-sdr")
				.flatMap(format -> Stream
						.of("legacy", "missing-depth", "wrong-depth", "missing-chroma-width", "wrong-chroma-width",
								"missing-chroma-height", "wrong-chroma-height", "missing-profile", "wrong-profile",
								"missing-level", "lower-level", "higher-level", "unknown-range", "full-range",
								"missing-chroma-location", "unspecified-chroma-location", "wrong-chroma-location")
						.map(mismatch -> Arguments.of(format, mismatch)));
	}

	@ParameterizedTest
	@MethodSource("invalidOutputMetadata")
	void missingOrWrongOutputEncodingMetadataIsRejected(String format, String mismatch) throws Exception {
		var params = request(directory);
		executable(params);
		params.setCodec(format.equals("avc") ? StreamCodingType.H264_AVC : StreamCodingType.H265_HEVC);
		params.setOverwrite(true);
		Files.writeString(params.getOutputPath(), "original");
		var source = encodedMetadata(format, "valid");
		var output = encodedMetadata(format, mismatch);
		Process process = completed(0, "", "");
		AtomicInteger probes = new AtomicInteger();
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			probes.incrementAndGet();
			return path.equals(params.getInputPath()) ? source : output;
		}, new EncoderProcessRunner(command -> {
			Files.writeString(Path.of(command.getLast()), "invalid metadata");
			return process;
		}));
		assertThrows(IOException.class, () -> converter.convert(params));
		assertEquals(2, probes.get(), "Rejection must come from post-encode output validation");
		assertEquals("original", Files.readString(params.getOutputPath()));
		assertEquals("not real video", Files.readString(params.getInputPath()));
		assertNoTemporaryFiles();
	}

	@ParameterizedTest
	@ValueSource(strings = { "avc", "hevc", "hdr10", "bt2020-sdr" })
	void validOutputMetadataCanBePublishedEvenWhenSourceEncodingMetadataIsUnknown(String format) throws Exception {
		var params = request(directory);
		executable(params);
		params.setCodec(format.equals("avc") ? StreamCodingType.H264_AVC : StreamCodingType.H265_HEVC);
		var source = encodedMetadata(format, "legacy");
		var output = encodedMetadata(format, "valid");
		assertEquals(-1, source.bitDepth());
		assertFalse(source.rangeKnown());
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback,
				path -> path.equals(params.getInputPath()) ? source : output, new EncoderProcessRunner(command -> {
					Files.writeString(Path.of(command.getLast()), "valid encoding");
					return process;
				}));
		var result = converter.convert(params);
		assertEquals(params.getCodec(), result.plan().codec());
		assertEquals("valid encoding", Files.readString(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void zeroOrMissingEncoderOutputFailsBeforePostProbe(boolean removeOutput) throws Exception {
		var params = request(directory);
		executable(params);
		Process process = completed(0, "", "");
		AtomicInteger probes = new AtomicInteger();
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			probes.incrementAndGet();
			return sdr(1920, 1080, FrameRate.FPS_24);
		}, new EncoderProcessRunner(command -> {
			if (removeOutput) {
				Files.delete(Path.of(command.getLast()));
			}
			return process;
		}));
		IOException failure = assertThrows(IOException.class, () -> converter.convert(params));
		assertTrue(failure.getMessage().contains("no nonempty regular output"));
		assertEquals(1, probes.get());
		assertFalse(Files.exists(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@Test
	void missingBinaryFailsBeforeLauncherAndDoesNotCreateTemporaryOutput() throws IOException {
		var params = request(directory);
		params.setExecutablePath(directory.resolve("missing-encoder").toString());
		var converter = preview(sdr(1920, 1080, FrameRate.FPS_24));
		assertEquals(params.getExecutablePath(), converter.prepare(params).command().getFirst());
		IOException failure = assertThrows(IOException.class, () -> converter.convert(params));
		assertTrue(failure.getMessage().contains("executable"));
		assertFalse(Files.exists(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@Test
	void runnerTimeoutPreservesDestinationAndCleansTemporary() throws Exception {
		var params = request(directory);
		executable(params);
		params.setTimeout(Duration.ofNanos(1));
		params.setOverwrite(true);
		Files.writeString(params.getOutputPath(), "original");
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback, path -> sdr(1920, 1080, FrameRate.FPS_24),
				new EncoderProcessRunner(command -> {
					Files.writeString(Path.of(command.getLast()), "partial");
					return process;
				}));
		IOException failure = assertThrows(IOException.class, () -> converter.convert(params));
		assertTrue(failure.getMessage().contains("Timed out"));
		assertEquals("original", Files.readString(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@Test
	void probeIoFailureAfterEncodingDoesNotPublish() throws Exception {
		var params = request(directory);
		executable(params);
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			if (!path.equals(params.getInputPath())) {
				throw new IOException("invalid elementary-stream headers");
			}
			return sdr(1920, 1080, FrameRate.FPS_24);
		}, new EncoderProcessRunner(command -> {
			Files.writeString(Path.of(command.getLast()), "garbage");
			return process;
		}));
		assertThrows(IOException.class, () -> converter.convert(params));
		assertFalse(Files.exists(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@Test
	void existingOutputSameInputHardlinkAndEmptyInputAreRejected() throws IOException {
		var params = request(directory);
		var converter = preview(sdr(1920, 1080, FrameRate.FPS_24));
		Files.writeString(params.getOutputPath(), "old");
		assertThrows(IOException.class, () -> converter.prepare(params));
		assertEquals("old", Files.readString(params.getOutputPath()));
		params.setOutputPath(params.getInputPath());
		params.setOverwrite(true);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		Path alias = Files.createLink(directory.resolve("source-alias.264"), params.getInputPath());
		params.setOutputPath(alias);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		params.setOutputPath(directory.resolve("new.264"));
		Files.writeString(params.getInputPath(), "");
		assertThrows(IOException.class, () -> converter.prepare(params));
	}

	@Test
	void outputDirectoriesAndSymlinksAreNotReplaceableEvenWithOverwrite() throws IOException {
		var params = request(directory);
		params.setOverwrite(true);
		var converter = preview(sdr(1920, 1080, FrameRate.FPS_24));
		params.setOutputPath(Files.createDirectory(directory.resolve("not-a-file")));
		assertThrows(IOException.class, () -> converter.prepare(params));
		Path old = Files.writeString(directory.resolve("old.264"), "old");
		params.setOutputPath(Files.createSymbolicLink(directory.resolve("link.264"), old));
		assertThrows(IOException.class, () -> converter.prepare(params));
		assertEquals("old", Files.readString(old));
	}

	@Test
	void destinationCreatedDuringEncodingIsPreservedWhenOverwriteIsFalse() throws Exception {
		var params = request(directory);
		executable(params);
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback, path -> sdr(1920, 1080, FrameRate.FPS_24),
				new EncoderProcessRunner(command -> {
					Files.writeString(Path.of(command.getLast()), "encoded");
					Files.writeString(params.getOutputPath(), "another writer");
					return process;
				}));
		assertThrows(IOException.class, () -> converter.convert(params));
		assertEquals("another writer", Files.readString(params.getOutputPath()));
		assertNoTemporaryFiles();
	}

	@Test
	void interruptionAfterProbePreventsPublishingAndCleansTemporary() throws Exception {
		var params = request(directory);
		executable(params);
		Process process = completed(0, "", "");
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			if (!path.equals(params.getInputPath())) {
				Thread.currentThread().interrupt();
			}
			return sdr(1920, 1080, FrameRate.FPS_24);
		}, new EncoderProcessRunner(command -> {
			Files.writeString(Path.of(command.getLast()), "encoded");
			return process;
		}));
		try {
			IOException failure = assertThrows(IOException.class, () -> converter.convert(params));
			assertTrue(failure.getMessage().contains("interrupted"));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertFalse(Files.exists(params.getOutputPath()));
		assertNoTemporaryFiles();
	}
}
