package org.brts.common.m2ts;

public interface IStreamInfo {

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
		return switch (getFrameRate()) {
		case 1 -> 23.976;
		case 2 -> 24.0;
		case 4 -> 29.97;
		default -> null;
		};
	}

}
