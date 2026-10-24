/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/clpi/M2tsClpiRegenBuilderMetadataTest.java' is part of BRTS.
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

package org.brts.lowlevel.clpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.junit.jupiter.api.Test;

class M2tsClpiRegenBuilderMetadataTest {

	private static final Path INPUT = Path.of("synthetic.m2ts");

	@Test
	void autoSelects0300ForHevcEvenWhenMetadataProbeFails() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> {
			throw new IOException("synthetic probe failure");
		});

		ClipInfo clip = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), null);

		assertThat(clip.getVersion()).isEqualTo("0300");
		assertThat(clip.getStreams().getFirst().getCodingType()).isEqualTo(StreamCodingType.H265_HEVC);
	}

	@Test
	void autoSelects0300ForPmt4kWithoutLoadingProbeWhenDisabled() throws Exception {
		AtomicBoolean probed = new AtomicBoolean();
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> {
			probed.set(true);
			return Map.of();
		});
		ClpiRegenOptions options = new ClpiRegenOptions();
		options.setProbeMode(ClpiRegenOptions.ProbeMode.DISABLED);

		ClipInfo clip = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H264_AVC, 8)), options);

		assertThat(clip.getVersion()).isEqualTo("0300");
		assertThat(probed).isFalse();
	}

	@Test
	void videoFormatOverrideUpgradesHdStreamTo0300() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> Map.of());
		ClpiRegenOptions options = options(ClpiRegenOptions.ProbeMode.DISABLED);
		ClpiRegenOptions.VideoOverrides override = new ClpiRegenOptions.VideoOverrides();
		override.setVideoFormat(8);
		options.setVideoStreams(Map.of(0x1011, override));

		ClipInfo clip = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H264_AVC, 6)), options);

		assertThat(clip.getVersion()).isEqualTo("0300");
		assertThat(clip.getStreams().getFirst().getVideoFormat()).isEqualTo(8);
	}

	@Test
	void autoSelects0300ForSecondaryProbeDetected4kStream() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder(
				(path, videos) -> Map.of(0x1012, new M2tsVideoMetadataProbe.Metadata(8, true, null, null, null)));

		ClipInfo clip = builder.prepareMetadata(INPUT,
				info(stream(0x1011, StreamCodingType.H264_AVC, 6), stream(0x1012, StreamCodingType.H264_AVC, 6)), null);

		assertThat(clip.getVersion()).isEqualTo("0300");
		assertThat(clip.getStreams().get(1).getVideoFormat()).isEqualTo(8);
	}

	@Test
	void regularHdAndAudioOnlyClipsRemain0200() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> Map.of());

		ClipInfo hd = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H264_AVC, 6)), null);
		ClipInfo audio = builder.prepareMetadata(INPUT, info(stream(0x1100, StreamCodingType.DOLBY_AC3, null)), null);

		assertThat(hd.getVersion()).isEqualTo("0200");
		assertThat(audio.getVersion()).isEqualTo("0200");
	}

	@Test
	void overridesTakePrecedenceAndSurviveBinaryRoundTrip() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder(
				(path, videos) -> Map.of(0x1011, new M2tsVideoMetadataProbe.Metadata(6, false, 1, 2, true)));
		ClpiRegenOptions options = options(ClpiRegenOptions.ProbeMode.BEST_EFFORT);
		ClpiRegenOptions.VideoOverrides override = new ClpiRegenOptions.VideoOverrides();
		override.setDynamicRangeType(0);
		override.setColorSpace(1);
		override.setHdrPlusFlag(false);
		override.setOcFlag(false);
		override.setCrFlag(false);
		options.setVideoStreams(Map.of(0x1011, override));

		ClipInfo clip = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), options);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		new ClipInfoWriter().write(clip, output);
		ClipInfo parsed = new ClipInfoParser().parse(new ByteArrayInputStream(output.toByteArray()));
		ClipStream video = parsed.getStreams().getFirst();

		assertThat(parsed.getVersion()).isEqualTo("0300");
		assertThat(video.getDynamicRangeType()).isZero();
		assertThat(video.getColorSpace()).isEqualTo(1);
		assertThat(video.getHdrPlusFlag()).isFalse();
		assertThat(video.getOcFlag()).isFalse();
		assertThat(video.getCrFlag()).isFalse();
	}

	@Test
	void strictModeAcceptsProbeResultsAndFailsWhenColorIsUnknown() throws Exception {
		M2tsClpiRegenBuilder known = new M2tsClpiRegenBuilder(
				(path, videos) -> Map.of(0x1011, new M2tsVideoMetadataProbe.Metadata(6, false, 1, 2, null)));
		M2tsClpiRegenBuilder unknownColor = new M2tsClpiRegenBuilder(
				(path, videos) -> Map.of(0x1011, new M2tsVideoMetadataProbe.Metadata(6, false, 1, null, null)));
		ClpiRegenOptions strict = options(ClpiRegenOptions.ProbeMode.STRICT);

		assertThat(
				known.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), strict).getVersion())
				.isEqualTo("0300");
		assertThatThrownBy(
				() -> unknownColor.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), strict))
				.isInstanceOf(IOException.class).hasMessageContaining("PID 4113").hasMessageContaining("colorSpace");
	}

	@Test
	void strictModeAllowsOverridesWhenProbeFails() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> {
			throw new IOException("probe unavailable");
		});
		ClpiRegenOptions options = options(ClpiRegenOptions.ProbeMode.STRICT);
		ClpiRegenOptions.VideoOverrides override = new ClpiRegenOptions.VideoOverrides();
		override.setDynamicRangeType(2);
		override.setColorSpace(2);
		options.setVideoStreams(Map.of(0x1011, override));

		ClipInfo clip = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), options);

		assertThat(clip.getVersion()).isEqualTo("0300");
		assertThat(clip.getStreams().getFirst().getDynamicRangeType()).isEqualTo(2);
	}

	@Test
	void bestEffortFallsBackWhenFfmpegNativeLibraryCannotLoad() throws Exception {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> {
			throw new UnsatisfiedLinkError("synthetic native unavailable");
		});

		ClipInfo clip = builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), null);

		assertThat(clip.getVersion()).isEqualTo("0300");
	}

	@Test
	void rejectsUnknownPidsNonHevcAttributesAndOutOfRangeValues() {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder((path, videos) -> Map.of());
		ClpiRegenOptions unknownPid = options(ClpiRegenOptions.ProbeMode.DISABLED);
		unknownPid.setVideoStreams(Map.of(7, new ClpiRegenOptions.VideoOverrides()));
		assertThatThrownBy(
				() -> builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H264_AVC, 6)), unknownPid))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a known video PID");

		ClpiRegenOptions nonHevc = options(ClpiRegenOptions.ProbeMode.DISABLED);
		ClpiRegenOptions.VideoOverrides hdr = new ClpiRegenOptions.VideoOverrides();
		hdr.setColorSpace(2);
		nonHevc.setVideoStreams(Map.of(0x1011, hdr));
		assertThatThrownBy(
				() -> builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H264_AVC, 6)), nonHevc))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HEVC-only");

		ClpiRegenOptions outOfRange = options(ClpiRegenOptions.ProbeMode.DISABLED);
		ClpiRegenOptions.VideoOverrides invalid = new ClpiRegenOptions.VideoOverrides();
		invalid.setDynamicRangeType(16);
		outOfRange.setVideoStreams(Map.of(0x1011, invalid));
		assertThatThrownBy(
				() -> builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H265_HEVC, 6)), outOfRange))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0 and 15");
	}

	@Test
	void refusesToDowngradeDetected4kThroughOverride() {
		M2tsClpiRegenBuilder builder = new M2tsClpiRegenBuilder(
				(path, videos) -> Map.of(0x1011, new M2tsVideoMetadataProbe.Metadata(8, true, null, null, null)));
		ClpiRegenOptions options = options(ClpiRegenOptions.ProbeMode.BEST_EFFORT);
		ClpiRegenOptions.VideoOverrides override = new ClpiRegenOptions.VideoOverrides();
		override.setVideoFormat(6);
		options.setVideoStreams(Map.of(0x1011, override));

		assertThatThrownBy(
				() -> builder.prepareMetadata(INPUT, info(stream(0x1011, StreamCodingType.H264_AVC, 6)), options))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conflicts with detected 4K");
	}

	private static ClpiRegenOptions options(ClpiRegenOptions.ProbeMode mode) {
		ClpiRegenOptions options = new ClpiRegenOptions();
		options.setProbeMode(mode);
		return options;
	}

	private static M2tsInfo info(M2tsStreamInfo... streams) {
		M2tsInfo info = new M2tsInfo();
		info.setStreams(List.of(streams));
		info.setTotalPackets(0);
		return info;
	}

	private static M2tsStreamInfo stream(int pid, StreamCodingType codingType, Integer videoFormat) {
		M2tsStreamInfo info = new M2tsStreamInfo();
		info.setPid(pid);
		info.setCodingType(codingType);
		info.setVideoFormat(videoFormat);
		info.setFrameRate(2);
		info.setAspectRatio(3);
		return info;
	}
}
