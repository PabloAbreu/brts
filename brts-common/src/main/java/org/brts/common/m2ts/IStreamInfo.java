/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/IStreamInfo.java' is part of BRTS.
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

package org.brts.common.m2ts;

import org.brts.common.video.FrameRate;

public interface IStreamInfo {
	int VIDEO_FORMAT_480I = 1;
	int VIDEO_FORMAT_480P = 2;
	int VIDEO_FORMAT_720P = 3;
	int VIDEO_FORMAT_1080I = 4;
	int VIDEO_FORMAT_1080P = 6;
	int VIDEO_FORMAT_2160P = 8;

	default void setVfr(int vfr) {

		// video format
		// 1 : 480i
		// 6 : 1080p
		setVideoFormat((vfr >> 4) & 0x0F);
		// frame rate
		// 1 : 23.976 fps
		// 2 : 24 fps
		// 4 : 29.97 fps
		setFrameRate(vfr & 0x0F);
	}

	void setVideoFormat(Integer videoFormat);

	Integer getVideoFormat();

	Integer getFrameRate();

	void setFrameRate(Integer frameRate);

	default String getVideoFormatInfo() {
		if (getVideoFormat() == null) {
			return null;
		}
		return switch (getVideoFormat()) {
		case VIDEO_FORMAT_480I -> "480i";
		case VIDEO_FORMAT_480P -> "480p";
		case VIDEO_FORMAT_720P -> "720p";
		case VIDEO_FORMAT_1080I -> "1080i";
		case VIDEO_FORMAT_1080P -> "1080p";
		case VIDEO_FORMAT_2160P -> "2160p";
		default -> "unknown video format " + getVideoFormat();
		};
	}

	default Integer getWidthPixels() {
		if (getVideoFormat() == null) {
			return null;
		}
		return switch (getVideoFormat()) {
		case VIDEO_FORMAT_480I, VIDEO_FORMAT_480P -> 720;
		case VIDEO_FORMAT_720P -> 1280;
		case VIDEO_FORMAT_1080I, VIDEO_FORMAT_1080P -> 1920;
		case VIDEO_FORMAT_2160P -> 3840;
		default -> null;
		};
	}

	default Integer getHeightPixels() {
		if (getVideoFormat() == null) {
			return null;
		}
		return switch (getVideoFormat()) {
		case VIDEO_FORMAT_480I, VIDEO_FORMAT_480P -> 480;
		case VIDEO_FORMAT_720P -> 720;
		case VIDEO_FORMAT_1080I, VIDEO_FORMAT_1080P -> 1080;
		case VIDEO_FORMAT_2160P -> 2160;
		default -> null;
		};
	}

	default Double getFrameRateFps() {
		if (getFrameRate() == null) {
			return null;
		}
		return fpsFromFrameRate(getFrameRate());
	}

	static Double fpsFromFrameRate(int frameRate) {
		try {
			// Preserve customary decimal aliases used by descriptors and stream metadata.
			return Math.round(FrameRate.fromCode(frameRate).asDouble() * 1000.0) / 1000.0;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static int frameRateFromFps(Double fps) {
		FrameRate rate = fps == null ? FrameRate.FPS_23976 : FrameRate.parse(fps.toString());
		if (rate.frameRateCode() < 0) {
			throw new IllegalArgumentException("Unsupported frame rate: " + fps);
		}
		return rate.frameRateCode();
	}
}
