package org.brts.common.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/m2ts/AudioChannelLayoutConverterTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioChannelLayoutConverterTest {

	@Test
	void channelsToLayout_null_defaultsToStereo() {
		assertThat(AudioChannelLayoutConverter.channelsToLayout((Integer) null)).isEqualTo(0x03);
	}

	@Test
	void channelsToLayout_mapsCommonChannelCounts() {
		assertThat(AudioChannelLayoutConverter.channelsToLayout(0)).isEqualTo(0x03);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(1)).isEqualTo(0x01);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(2)).isEqualTo(0x03);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(3)).isEqualTo(0x06);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(6)).isEqualTo(0x06);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(7)).isEqualTo(0x0C);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(8)).isEqualTo(0x0C);
		assertThat(AudioChannelLayoutConverter.channelsToLayout(9)).isEqualTo(0x0C);
	}

	@Test
	void layoutToChannels_mapsKnownCodes() {
		assertThat(AudioChannelLayoutConverter.layoutToChannels(0x01)).isEqualTo(1);
		assertThat(AudioChannelLayoutConverter.layoutToChannels(0x03)).isEqualTo(2);
		assertThat(AudioChannelLayoutConverter.layoutToChannels(0x06)).isEqualTo(6);
		assertThat(AudioChannelLayoutConverter.layoutToChannels(0x0C)).isEqualTo(8);
	}

	@Test
	void layoutToChannels_unknownCode_returnsNull() {
		assertThat(AudioChannelLayoutConverter.layoutToChannels(0x07)).isNull();
	}

	@Test
	void layoutDisplayName_mapsKnownAndUnknownCodes() {
		assertThat(AudioChannelLayoutConverter.layoutDisplayName(0x01)).isEqualTo("Mono");
		assertThat(AudioChannelLayoutConverter.layoutDisplayName(0x03)).isEqualTo("2.0");
		assertThat(AudioChannelLayoutConverter.layoutDisplayName(0x06)).isEqualTo("5.1");
		assertThat(AudioChannelLayoutConverter.layoutDisplayName(0x0C)).isEqualTo("7.1");
		assertThat(AudioChannelLayoutConverter.layoutDisplayName(0x09)).isEqualTo("Unknown(9)");
	}

	@Test
	void channelsDisplayName_mapsKnownAndFallbackValues() {
		assertThat(AudioChannelLayoutConverter.channelsDisplayName(1)).isEqualTo("Mono");
		assertThat(AudioChannelLayoutConverter.channelsDisplayName(2)).isEqualTo("2.0");
		assertThat(AudioChannelLayoutConverter.channelsDisplayName(6)).isEqualTo("5.1");
		assertThat(AudioChannelLayoutConverter.channelsDisplayName(8)).isEqualTo("7.1");
		assertThat(AudioChannelLayoutConverter.channelsDisplayName(4)).isEqualTo("4ch");
	}
}
