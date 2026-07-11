package org.brts.common.m2ts;

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
