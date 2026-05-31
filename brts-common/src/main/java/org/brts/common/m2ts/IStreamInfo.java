package org.brts.common.m2ts;

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
