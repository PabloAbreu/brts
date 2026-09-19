package org.brts.common.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/AudioChannelLayoutConverter.java' is part of BRTS.
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
