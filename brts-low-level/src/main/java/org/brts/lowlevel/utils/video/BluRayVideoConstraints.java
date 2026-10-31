/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/BluRayVideoConstraints.java' is part of BRTS.
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

import org.brts.common.video.FrameRate;

import java.util.List;

import org.brts.common.model.StreamCodingType;

/**
 * Conservative progressive subset, not a disc/mux conformance validator. AVC: High@4.1, 40 Mbit/s peak, 30 Mbit CPB
 * (x264 Blu-ray authoring recommendations). HEVC: Main10@5.1 high tier, conservative 40 Mbit/s peak and CPB (within
 * HEVC level 5.1 limits). UHD color/closed-GOP constraints follow x265's encoder.cpp uhdBluray policy; native uhd-bd is
 * experimental. Sources: https://www.x264bluray.com/ and
 * https://x265.readthedocs.io/en/master/cli.html#profile-level-tier UHD disc capacity/total mux budgets are
 * deliberately outside this utility.
 */
public final class BluRayVideoConstraints {

	public record Format(int width, int height, List<FrameRate> frameRates) {
		public Format {
			frameRates = List.copyOf(frameRates);
		}
	}

	private BluRayVideoConstraints() {
	}

	/** Only square-pixel progressive targets are exposed in this first version. */
	public static List<Format> formats(StreamCodingType codec) {
		if (codec == StreamCodingType.H264_AVC)
			return List.of(
					new Format(1280, 720,
							List.of(FrameRate.FPS_23976, FrameRate.FPS_24, FrameRate.FPS_50, FrameRate.FPS_5994)),
					new Format(1920, 1080, List.of(FrameRate.FPS_23976, FrameRate.FPS_24)));
		if (codec == StreamCodingType.H265_HEVC)
			return List.of(new Format(1920, 1080, FrameRate.supportedRates()),
					new Format(3840, 2160, FrameRate.supportedRates()));
		throw new IllegalArgumentException("VideoConverter supports only H264_AVC and H265_HEVC");
	}

	static boolean supports(StreamCodingType codec, int width, int height, FrameRate frameRate) {
		return formats(codec).stream()
				.anyMatch(f -> f.width() == width && f.height() == height && f.frameRates().contains(frameRate));
	}

	static Geometry geometry(StreamCodingType codec, VideoStreamInfo source, Integer width, Integer height,
			FrameRate frameRate) {
		if ((width == null) != (height == null))
			throw new IllegalArgumentException("Target width and height must be supplied together");
		boolean explicit = width != null;
		int targetWidth = explicit ? width : source.width();
		int targetHeight = explicit ? height : source.height();
		if (!explicit && !supports(codec, targetWidth, targetHeight, frameRate)) {
			List<Format> candidates = formats(codec).stream()
					.filter(f -> f.frameRates().contains(frameRate) && f.width() >= source.width()
							&& f.height() >= source.height()
							&& (f.width() == source.width() || f.height() == source.height()))
					.toList();
			if (candidates.size() != 1)
				throw new IllegalArgumentException("No unique padding-only target for " + source.width() + "x"
						+ source.height() + " at " + frameRate + "; specify a compatible target resolution/frame rate");
			targetWidth = candidates.getFirst().width();
			targetHeight = candidates.getFirst().height();
		}
		if (!supports(codec, targetWidth, targetHeight, frameRate))
			throw new IllegalArgumentException("Unsupported progressive " + codec + " target " + targetWidth + "x"
					+ targetHeight + " at " + frameRate);
		int contentWidth = source.width(), contentHeight = source.height();
		if (!explicit && source.sarNumerator() != source.sarDenominator())
			throw new IllegalArgumentException("Anamorphic input needs an explicit square-pixel target resolution");
		if (explicit) {
			double dar = (double) source.width() * source.sarNumerator() / source.height() / source.sarDenominator();
			if (dar >= (double) targetWidth / targetHeight) {
				contentWidth = targetWidth;
				contentHeight = even(targetWidth / dar);
			} else {
				contentHeight = targetHeight;
				contentWidth = even(targetHeight * dar);
			}
		}
		if (contentWidth < 2 || contentHeight < 2 || (contentWidth & 1) != 0 || (contentHeight & 1) != 0)
			throw new IllegalArgumentException("Content dimensions must be positive even values for 4:2:0");
		int horizontal = targetWidth - contentWidth, vertical = targetHeight - contentHeight;
		// Chroma aligned; any unavoidable two-pixel remainder is assigned to right/bottom.
		int left = horizontal / 4 * 2, top = vertical / 4 * 2;
		return new Geometry(targetWidth, targetHeight, contentWidth, contentHeight, left, top, horizontal - left,
				vertical - top);
	}

	private static int even(double value) {
		return (int) Math.floor(value / 2) * 2;
	}

	record Geometry(int width, int height, int contentWidth, int contentHeight, int left, int top, int right,
			int bottom) {
	}
}
