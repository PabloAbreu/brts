package org.brts.common.m2ts;

/**
 * Converts between audio channel counts and Blu-ray channel layout codes.
 */
public final class AudioChannelLayoutConverter {

	private static final int LAYOUT_MONO = 0x01;

	private static final int LAYOUT_STEREO = 0x03;

	private static final int LAYOUT_MULTI_5_1 = 0x06;

	private static final int LAYOUT_MULTI_7_1 = 0x0C;

	private AudioChannelLayoutConverter() {
	}

	/**
	 * Maps a channel count to a Blu-ray {@code audio_channel_layout} code.
	 */
	public static int channelsToLayout(Integer channels) {
		if (channels == null || channels <= 0) {
			return LAYOUT_STEREO;
		}
		if (channels == 1) {
			return LAYOUT_MONO;
		}
		if (channels == 2) {
			return LAYOUT_STEREO;
		}
		if (channels <= 6) {
			return LAYOUT_MULTI_5_1;
		}
		return LAYOUT_MULTI_7_1;
	}

	public static int channelsToLayout(int channels) {
		return channelsToLayout(Integer.valueOf(channels));
	}

	/**
	 * Maps a Blu-ray {@code audio_channel_layout} code to a canonical channel count.
	 */
	public static Integer layoutToChannels(int layoutCode) {
		return switch (layoutCode) {
		case LAYOUT_MONO -> 1;
		case LAYOUT_STEREO -> 2;
		case LAYOUT_MULTI_5_1 -> 6;
		case LAYOUT_MULTI_7_1 -> 8;
		default -> null;
		};
	}

	/**
	 * Returns a human-readable channel layout label for a Blu-ray layout code.
	 */
	public static String layoutDisplayName(int layoutCode) {
		return switch (layoutCode) {
		case LAYOUT_MONO -> "Mono";
		case LAYOUT_STEREO -> "2.0";
		case LAYOUT_MULTI_5_1 -> "5.1";
		case LAYOUT_MULTI_7_1 -> "7.1";
		default -> "Unknown(" + layoutCode + ")";
		};
	}

	/**
	 * Returns a human-readable channel layout label for a raw channel count.
	 */
	public static String channelsDisplayName(int channels) {
		return switch (channels) {
		case 1 -> "Mono";
		case 2 -> "2.0";
		case 6 -> "5.1";
		case 8 -> "7.1";
		default -> channels + "ch";
		};
	}
}