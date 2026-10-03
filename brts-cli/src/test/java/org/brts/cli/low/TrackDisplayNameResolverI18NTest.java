package org.brts.cli.low;



import static org.assertj.core.api.Assertions.assertThat;

import org.brts.common.mkv.SourceMediaInfo.SourceTrack;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.BrtsI18NLabels;
import org.brts.lowlevel.popupmenu.TrackDisplayNameResolver;
import org.junit.jupiter.api.Test;

class TrackDisplayNameResolverI18NTest {

	@Test
	void resolvesPackagedEnglishLanguageName() {
		assertThat(BrtsI18NLabels.getLanguageName("fra", "fallback")).isEqualTo("French");
	}

	@Test
	void synthesizesUnnamedTrackUsingPackagedResources() {
		SourceTrack track = new SourceTrack();
		track.setTrackNumber(1);
		track.setTrackName("unnamed");
		track.setLanguage("fra");
		track.setCodingType(StreamCodingType.DOLBY_AC3);
		track.setChannels(6);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("French - AC3 5.1");
	}
}
