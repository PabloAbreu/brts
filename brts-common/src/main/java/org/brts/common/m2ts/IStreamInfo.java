package org.brts.common.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/IStreamInfo.java' is part of BRTS.
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

public interface IStreamInfo {
	int VIDEO_FORMAT_480I = 1;
	int VIDEO_FORMAT_480P = 2;
	int VIDEO_FORMAT_720P = 3;
	int VIDEO_FORMAT_1080I = 4;
	int VIDEO_FORMAT_1080P = 6;

	int VIDEO_FRAME_RATE_23_976 = 1;
	int VIDEO_FRAME_RATE_24 = 2;
	int VIDEO_FRAME_RATE_29_97 = 4;

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
		case 1 -> "480i";
		case 2 -> "480p";
		case 3 -> "720p";
		case 4 -> "1080i";
		case 6 -> "1080p";
		default -> "unknown video format " + getVideoFormat();
		};
	}

	default Integer getWidthPixels() {
		if (getVideoFormat() == null) {
			return null;
		}
		return switch (getVideoFormat()) {
		case 1, 2 -> 720;
		case 3 -> 1280;
		case 4, 6 -> 1920;
		default -> null;
		};
	}

	default Integer getHeightPixels() {
		if (getVideoFormat() == null) {
			return null;
		}
		return switch (getVideoFormat()) {
		case 1, 2 -> 480;
		case 3 -> 720;
		case 4, 6 -> 1080;
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
		return switch (frameRate) {
		case 1 -> 23.976;
		case 2 -> 24.0;
		case 4 -> 29.97;
		default -> null;
		};
	}

	static int frameRateFromFps(Double fps) {
		if (fps == null || fps == 23.976) {
			return 1;
		} else if (fps == 24.0) {
			return 2;
		} else if (fps == 29.97) {
			return 4;
		} else {
			throw new IllegalArgumentException("Unsupported frame rate: " + fps);
		}
	}
}
