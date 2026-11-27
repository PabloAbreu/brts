/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/ClipRegenCliTest.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.clpi.ClpiRegenOptions.ProbeMode;
import org.brts.lowlevel.clpi.ClpiRegenOptions.VideoOverrides;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the real CLI JSON handler without running the builder or loading FFmpeg natives. */
class ClipRegenCliTest {

	@TempDir
	Path tempDir;

	private static class CapturingRegen extends ClipRegenCli.Regen {

		ClipRegenCli.RegenOptions parsed;

		@Override
		protected void execute(ClipRegenCli.RegenOptions opts) {
			parsed = opts;
		}

	}

	private ClipRegenCli.RegenOptions parse(String... extra) throws Exception {
		List<String> args = new ArrayList<>(List.of("--input", tempDir.resolve("00001.m2ts").toString(), "--output",
				tempDir.resolve("CLIPINF").toString()));
		args.addAll(List.of(extra));
		CapturingRegen runner = new CapturingRegen();
		runner.run(args.toArray(String[]::new));
		assertThat(runner.parsed).isNotNull();
		return runner.parsed;
	}

	private ClipRegenCli.RegenOptions parseJson(String json) throws Exception {
		Path options = tempDir.resolve("regen options.json");
		Files.writeString(options, json);
		return parse("--options", options.toString());
	}

	@Test
	void oldRequiredArgumentsStillWorkWithoutOptions() throws Exception {
		ClipRegenCli.RegenOptions opts = parse();

		assertThat(opts.input.toPath()).isEqualTo(tempDir.resolve("00001.m2ts"));
		assertThat(opts.outputDir.toPath()).isEqualTo(tempDir.resolve("CLIPINF"));
		assertThat(opts.clipName).isNull();
		assertThat(opts.writeJson).isFalse();
		assertThat(opts.regenOptions).isNull();
	}

	@Test
	void oldOptionalArgumentsStillWorkWithoutOptions() throws Exception {
		ClipRegenCli.RegenOptions opts = parse("--clip-name", "00042", "--json");

		assertThat(opts.clipName).isEqualTo("00042");
		assertThat(opts.writeJson).isTrue();
		assertThat(opts.regenOptions).isNull();
	}

	@Test
	void emptyJsonOptionsDefaultToBestEffort() throws Exception {
		ClipRegenCli.RegenOptions opts = parseJson("{}");

		assertThat(opts.regenOptions.getProbeMode()).isEqualTo(ProbeMode.BEST_EFFORT);
		assertThat(opts.regenOptions.getVideoStreams()).isEmpty();
	}

	@Test
	void jsonOptionsDeserializeAllOverridesUsingDecimalPids() throws Exception {
		ClipRegenCli.RegenOptions opts = parseJson("""
				{
				  "probeMode": "STRICT",
				  "videoStreams": {
				    "4113": {
				      "videoFormat": 8,
				      "frameRate": 1,
				      "aspectRatio": 3,
				      "ocFlag": true,
				      "crFlag": true,
				      "dynamicRangeType": 1,
				      "colorSpace": 2,
				      "hdrPlusFlag": true
				    },
				    "4114": { "dynamicRangeType": 2, "colorSpace": 1 }
				  }
				}
				""");

		assertThat(opts.regenOptions.getProbeMode()).isEqualTo(ProbeMode.STRICT);
		assertThat(opts.regenOptions.getVideoStreams()).containsOnlyKeys(4113, 4114);
		VideoOverrides video = opts.regenOptions.getVideoStreams().get(4113);
		assertThat(video.getVideoFormat()).isEqualTo(8);
		assertThat(video.getFrameRate()).isEqualTo(1);
		assertThat(video.getAspectRatio()).isEqualTo(3);
		assertThat(video.getOcFlag()).isTrue();
		assertThat(video.getCrFlag()).isTrue();
		assertThat(video.getDynamicRangeType()).isEqualTo(1);
		assertThat(video.getColorSpace()).isEqualTo(2);
		assertThat(video.getHdrPlusFlag()).isTrue();
		assertThat(opts.regenOptions.getVideoStreams().get(4114).getDynamicRangeType()).isEqualTo(2);
		assertThat(opts.regenOptions.getVideoStreams().get(4114).getColorSpace()).isEqualTo(1);
	}

	@Test
	void explicitFalseAndZeroOverridesAreNotTreatedAsMissing() throws Exception {
		ClipRegenCli.RegenOptions opts = parseJson("""
				{
				  "videoStreams": {
				    "4113": {
				      "videoFormat": 0,
				      "frameRate": 0,
				      "aspectRatio": 0,
				      "ocFlag": false,
				      "crFlag": false,
				      "dynamicRangeType": 0,
				      "colorSpace": 0,
				      "hdrPlusFlag": false
				    }
				  }
				}
				""");

		VideoOverrides video = opts.regenOptions.getVideoStreams().get(4113);
		assertThat(video.getVideoFormat()).isZero();
		assertThat(video.getFrameRate()).isZero();
		assertThat(video.getAspectRatio()).isZero();
		assertThat(video.getDynamicRangeType()).isZero();
		assertThat(video.getColorSpace()).isZero();
		assertThat(video.getOcFlag()).isFalse();
		assertThat(video.getCrFlag()).isFalse();
		assertThat(video.getHdrPlusFlag()).isFalse();
	}

	@Test
	void disabledModeAndNullableOverridesParseWithoutNativeProcessing() throws Exception {
		ClipRegenCli.RegenOptions opts = parseJson("""
				{
				  "probeMode": "DISABLED",
				  "videoStreams": { "4113": { "ocFlag": null, "dynamicRangeType": null } }
				}
				""");

		assertThat(opts.regenOptions.getProbeMode()).isEqualTo(ProbeMode.DISABLED);
		VideoOverrides video = opts.regenOptions.getVideoStreams().get(4113);
		assertThat(video.getVideoFormat()).isNull();
		assertThat(video.getFrameRate()).isNull();
		assertThat(video.getAspectRatio()).isNull();
		assertThat(video.getOcFlag()).isNull();
		assertThat(video.getCrFlag()).isNull();
		assertThat(video.getDynamicRangeType()).isNull();
		assertThat(video.getColorSpace()).isNull();
		assertThat(video.getHdrPlusFlag()).isNull();
	}

}
