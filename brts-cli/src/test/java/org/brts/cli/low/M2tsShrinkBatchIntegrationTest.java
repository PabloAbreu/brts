/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/M2tsShrinkBatchIntegrationTest.java' is part of BRTS.
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

package org.brts.cli.low;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.SourceFingerprint;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.utils.ProcessUtils;
import org.brts.common.utils.TsMuxerUtils;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.clpi.ClpiRegenOptions;
import org.brts.lowlevel.clpi.M2tsClpiRegenBuilder;
import org.brts.lowlevel.m2ts.PreservingM2tsClipWriter;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.pgs.PgsGenerator;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.shrink.M2tsShrinkPlan;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real extraction/encoding/mux/publication; uses normal BrtsFileConfig tool resolution, not PATH alone. */
class M2tsShrinkBatchIntegrationTest {

	@TempDir
	Path temp;

	@ParameterizedTest(name = "actual AVC + sparse AC3, PGS={0}")
	@ValueSource(booleans = { false, true })
	void publishesActualBatchWithinBudgetWithOriginalPidsAndCopiedTracks(boolean subtitles) throws Exception {
		String ffmpeg = ProcessUtils.findFullPath("ffmpeg");
		assumeTrue(ffmpeg != null, "FFmpeg fixture generator unavailable");
		assumeTrue(TsMuxerUtils.isTsMuxeRAvailable(), "Configured tsMuxeR unavailable");
		String tsmuxer = TsMuxerUtils.resolveTsMuxeRBinary();
		Path video = temp.resolve("video.h264");
		Path audio = temp.resolve("audio.ac3");
		run("fixture-video", ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-f", "lavfi", "-i",
				"testsrc2=size=1280x720:rate=24", "-t", "8", "-c:v", "libx264", "-threads", "1", "-preset", "fast",
				"-b:v", "8000k", "-bf", "0", "-g", "24", "-pix_fmt", "yuv420p", "-color_primaries", "bt709",
				"-color_trc", "bt709", "-colorspace", "bt709", "-color_range", "tv", "-x264-params",
				"aud=1:repeat-headers=1:scenecut=0:slices=4", "-f", "h264", video.toString());
		run("fixture-audio", ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-f", "lavfi", "-i",
				"sine=frequency=440:sample_rate=48000", "-t", "8", "-ac", "2", "-c:a", "ac3", "-b:a", "192k", "-f",
				"ac3", audio.toString());
		Path secondAudio = temp.resolve("second-audio.ac3");
		Files.copy(audio, secondAudio);
		String meta = "MUXOPT --blu-ray --new-audio-pes --vbr --vbv-len=500\n" + "V_MPEG4/ISO/AVC, \"" + video
				+ "\", fps=24\n" + "A_AC3, \"" + audio + "\", lang=fra\n" + "A_AC3, \"" + secondAudio
				+ "\", lang=eng\n";
		if (subtitles) {
			Path srt = temp.resolve("subtitles.srt");
			Files.writeString(srt, "1\n00:00:00,500 --> 00:00:01,500\nBatch preservation\n\n");
			var config = new PgsRenderConfig();
			config.setScreenWidth(1280);
			config.setScreenHeight(720);
			config.setFrameRateCode(2);
			Path sup = temp.resolve("subtitles.sup");
			new PgsGenerator(config).generate(srt, sup);
			meta += "S_HDMV/PGS, \"" + sup + "\", lang=eng\n";
		}
		Path metaFile = temp.resolve("source.meta");
		Files.writeString(metaFile, meta);
		Path sourceRoot = temp.resolve("source");
		run("fixture-mux", tsmuxer, metaFile.toString(), sourceRoot.toString());
		Path source = sourceRoot.resolve("BDMV/STREAM/00000.m2ts");
		Path sourceClpi = sourceRoot.resolve("BDMV/CLIPINF/00000.clpi");
		var muxClip = new ClipInfoParser().parse(sourceClpi);
		assertThat(muxClip.getStreams()).hasSize(subtitles ? 4 : 3);
		// Keep the native tsMuxeR source pair untouched, including its exclusive end.
		// The PMT does not carry aspect ratio; provide only the native stream metadata
		// to the independent scans. Neither scan is written over the source CLPI.
		var regenOptions = new ClpiRegenOptions();
		var muxVideo = muxClip.getStreams().stream().filter(s -> s.getCodingType().isVideo()).findFirst().orElseThrow();
		var videoMetadata = new ClpiRegenOptions.VideoOverrides();
		videoMetadata.setVideoFormat(muxVideo.getVideoFormat());
		videoMetadata.setFrameRate(muxVideo.getFrameRate());
		videoMetadata.setAspectRatio(muxVideo.getAspectRatio());
		regenOptions.getVideoStreams().put(muxVideo.getPid(), videoMetadata);
		var sourceScan = new M2tsClpiRegenBuilder().build(source, "00000", regenOptions);
		assertThat(muxClip.getTsRecordingEndPts().getTicks())
				.isEqualTo(sourceScan.getTsRecordingEndPts().getTicks() + 90_000 / 24);
		String sourceHash = SourceFingerprint.sha256(source);
		String clpiHash = SourceFingerprint.sha256(sourceClpi);
		Path playlist = sourceRoot.resolve("BDMV/PLAYLIST/00000.mpls");
		String playlistHash = SourceFingerprint.sha256(playlist);
		Path selection = temp.resolve("shrink-config.json");
		Files.writeString(selection, "{\"audioPids\":[4353],\"subtitlePids\":" + (subtitles ? "[4608]" : "[]")
				+ ",\"audioDowngrades\":{}," + "\"minimumAudioBitrateKbps\":{},\"fixedAudioBitrateKbps\":{}}");
		Path destination = temp.resolve("destination");
		Path work = temp.resolve("work");
		Path report = temp.resolve("report.json");
		// ABR needs several GOPs to converge; tiny two-second encodes can legitimately exceed the estimate.
		long target = 6_000_000;
		new M2tsShrinkCli.Shrink().run(new String[] { "--disc-root", sourceRoot.toString(), "--clip-id", "00000",
				"--config", selection.toString(), "--target-bytes", Long.toString(target), "--engine", "FFMPEG",
				"--output", destination.toString(), "--work-dir", work.toString(), "--report", report.toString() });
		assertThat(JsonMapperFactory.get().readValue(report.toFile(), M2tsShrinkPlan.class).decision())
				.isEqualTo(M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED);
		var batch = JsonMapperFactory.get().readValue(work.resolve("batch-run.json").toFile(),
				BatchRunDescriptor.class);
		assertThat(batch.getSteps()).extracting(s -> s.getCommand()).containsExactly("m2ts-extract", "video-convert",
				"m2ts-create");
		Path published = destination.resolve("BDMV/STREAM/00000.m2ts");
		Path publishedClpi = destination.resolve("BDMV/CLIPINF/00000.clpi");
		assertThat(published).isNotEmptyFile();
		assertThat(publishedClpi).isNotEmptyFile();
		assertThat(Files.size(published)).isLessThanOrEqualTo(target).isLessThan(Files.size(source));
		var info = new M2tsParser().parse(published);
		var retained = new ArrayList<>(List.of(0x1011, 0x1101));
		if (subtitles)
			retained.add(0x1200);
		assertThat(info.getStreams()).extracting(s -> s.getPid()).containsExactlyElementsOf(retained);
		var clip = new ClipInfoParser().parse(publishedClpi);
		var originalClip = new ClipInfoParser().parse(sourceClpi);
		assertThat(clip.getStreams()).extracting(s -> s.getPid()).containsExactlyElementsOf(retained);
		assertThat(clip.getTsRecordingStartPts()).isEqualTo(originalClip.getTsRecordingStartPts());
		assertThat(clip.getTsRecordingEndPts()).isEqualTo(originalClip.getTsRecordingEndPts());
		assertThat(clip.getDuration()).isEqualTo(originalClip.getDuration());
		assertThat(clip.getNumSourcePackets()).isEqualTo(Files.size(published) / 192);
		assertThat(clip.getNumSourcePackets()).isNotEqualTo(originalClip.getNumSourcePackets());
		var publishedScan = new M2tsClpiRegenBuilder().build(published, "00000", regenOptions);
		assertThat(publishedScan.getTsRecordingStartPts()).isEqualTo(sourceScan.getTsRecordingStartPts());
		assertThat(publishedScan.getTsRecordingEndPts()).isEqualTo(sourceScan.getTsRecordingEndPts());
		assertThat(clip.getEpMap().getStreams()).anySatisfy(s -> assertThat(s.getEntries()).isNotEmpty());
		var entries = clip.getEpMap().getStreams().getFirst().getEntries();
		assertThat(entries).allSatisfy(e -> assertThat(e.getSpn()).isBetween(0L, Files.size(published) / 192 - 1));
		assertThat(entries.stream().map(e -> e.getSpn()).toList()).isNotEqualTo(
				originalClip.getEpMap().getStreams().getFirst().getEntries().stream().map(e -> e.getSpn()).toList());
		Path check = temp.resolve("published-es");
		new M2tsExtractor().extract(published, info, check, null, true);
		var original = M2tsTiming.read(work.resolve("extracted/" + M2tsTimingManifest.FILE_NAME));
		var result = M2tsTiming.read(check.resolve(M2tsTimingManifest.FILE_NAME));
		for (var timing : original.streams()) {
			var actual = result.streams().stream().filter(s -> s.pid() == timing.pid()).findFirst().orElseThrow();
			assertThat(actual.firstPts90k()).isEqualTo(timing.firstPts90k());
			if (!timing.codingType().isVideo()) {
				assertThat(Files.mismatch(work.resolve("extracted").resolve(timing.fileName()),
						check.resolve(actual.fileName()))).isEqualTo(-1);
				assertThat(actual.segments()).isEqualTo(timing.segments());
			} else {
				assertThat(Files.mismatch(work.resolve("extracted").resolve(timing.fileName()),
						check.resolve(actual.fileName()))).isNotEqualTo(-1);
				assertThat(actual.pesTimings().stream().map(p -> p.pts90k()).sorted().toList())
						.isEqualTo(timing.pesTimings().stream().map(p -> p.pts90k()).sorted().toList());
			}
		}
		// Mutate only disposable CLPI copies, never the native source. Valid AU grids
		// must not make an incompatible start or unproven two-frame end acceptable.
		assertInvalidBoundsRejected(work, sourceClpi, "start", 18, 2, "presentation start/range is incompatible");
		assertInvalidBoundsRejected(work, sourceClpi, "end", 22, 90_000 / 24,
				"unproven presentation padding is unsupported");
		assertThat(SourceFingerprint.sha256(source)).isEqualTo(sourceHash);
		assertThat(SourceFingerprint.sha256(sourceClpi)).isEqualTo(clpiHash);
		assertThat(SourceFingerprint.sha256(playlist)).isEqualTo(playlistHash);
		System.out.printf("Actual batch published PGS=%s: %,d -> %,d bytes (target %,d)%n", subtitles,
				Files.size(source), Files.size(published), target);
		// Both fixtures fit the parser's full-file scan (50,000 packets). PCR is a
		// transport/decode clock, not proof that the last picture's display has ended.
		var sourceInfo = new M2tsParser().parse(source);
		assertThat(sourceInfo.getTotalPackets()).isLessThan(50_000L);
		assertThat(info.getTotalPackets()).isLessThan(50_000L);
		System.out.printf("Native CLPI PGS=%s: start=%d end=%d last-picture=%d source-PCR90k=%d output-PCR90k=%d%n",
				subtitles, muxClip.getTsRecordingStartPts().getTicks(), muxClip.getTsRecordingEndPts().getTicks(),
				sourceScan.getTsRecordingEndPts().getTicks(), sourceInfo.getLastPcr27MHz() / 300,
				info.getLastPcr27MHz() / 300);
	}

	private void assertInvalidBoundsRejected(Path work, Path sourceClpi, String name, int fieldOffset, int delta90k,
			String message) throws Exception {
		byte[] bytes = Files.readAllBytes(sourceClpi);
		ByteBuffer data = ByteBuffer.wrap(bytes);
		int sequenceOffset = data.getInt(8);
		// Native fixture has one ATC/STC sequence; presentation fields are 45 kHz.
		assertThat(bytes[sequenceOffset + 5]).isEqualTo((byte) 1);
		assertThat(bytes[sequenceOffset + 10]).isEqualTo((byte) 1);
		int position = sequenceOffset + fieldOffset;
		data.putInt(position, data.getInt(position) + delta90k / 2);
		Path badClpi = temp.resolve("invalid-" + name + "-source/00000.clpi");
		Files.createDirectories(badClpi.getParent());
		Files.write(badClpi, bytes);
		var descriptor = JsonMapperFactory.get().readValue(work.resolve("m2ts-descriptor.json").toFile(),
				M2tsDescriptor.class);
		descriptor.setSourceClpi(badClpi.toString());
		descriptor.setSourceClpiSha256(SourceFingerprint.sha256(badClpi));
		Path rejected = temp.resolve("invalid-" + name + "-output");
		Path output = rejected.resolve("00000.m2ts");
		Path outputClpi = rejected.resolve("00000.clpi");
		assertThatThrownBy(() -> new PreservingM2tsClipWriter().write(descriptor, output, outputClpi))
				.isInstanceOf(IOException.class).hasMessageContaining(message);
		assertThat(output).doesNotExist();
		assertThat(outputClpi).doesNotExist();
	}

	private void run(String name, String... command) throws Exception {
		Path log = temp.resolve(name + ".log");
		Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			assertThat(process.waitFor(90, TimeUnit.SECONDS)).as("%s timed out; %s", name, log).isTrue();
			assertThat(process.exitValue()).as("%s: %s", name, Files.readString(log)).isZero();
		} finally {
			process.destroyForcibly();
		}
	}
}
