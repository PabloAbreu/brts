package org.brts.highlevel.template;



import org.brts.highlevel.descriptor.MovieDiscDescriptor;
import org.brts.highlevel.style.StyleTemplate;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.TitleDescriptor;
import org.brts.middle.descriptor.TitleMenuConfig;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Template for a single-feature-film Blu-ray disc, with optional bonus tracks.
 * <p>
 * Title numbering:
 * <ul>
 * <li>Title 1 — main feature</li>
 * <li>Title 2, 3, … — bonus tracks in order</li>
 * </ul>
 * A title menu is generated only when a style template providing a title menu background is set and the disc has more
 * than one title.
 */
@Slf4j
public class MovieDiscTemplate implements DiscTemplate<MovieDiscDescriptor> {

	@Override
	public String templateType() {
		return "MOVIE";
	}

	@Override
	public DiscDescriptor expand(MovieDiscDescriptor descriptor, StyleTemplate style) {
		log.info("Expanding MOVIE template: {}", descriptor.getDiscTitle());

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName(descriptor.getDiscTitle());
		disc.setOutputFolder(descriptor.getOutputDirectory());
		disc.setHasTopMenu(false);
		if (style != null) {
			style.applyTo(disc);
		}

		List<TitleDescriptor> titles = new ArrayList<>();
		int titleId = 1;

		// Main feature
		if (descriptor.getMainFeature() != null) {
			TitleDescriptor main = toTitleDescriptor(titleId++, descriptor.getMainFeature());
			titles.add(main);
		}

		// Bonus tracks
		if (descriptor.getBonusTracks() != null) {
			for (MovieDiscDescriptor.MovieFeature bonus : descriptor.getBonusTracks()) {
				titles.add(toTitleDescriptor(titleId++, bonus));
			}
		}

		disc.setTitles(titles);

		if (style != null && style.hasTitleMenuBackground() && titles.size() > 1) {
			TitleMenuConfig menuConfig = new TitleMenuConfig();
			style.applyTo(menuConfig);
			disc.setTitleMenuConfig(menuConfig);
			disc.setHasTopMenu(true);
		}
		return disc;
	}

	private TitleDescriptor toTitleDescriptor(int id, MovieDiscDescriptor.MovieFeature feature) {
		TitleDescriptor td = new TitleDescriptor();
		td.setTitleId(id);
		td.setSourceMkv(feature.getSourceMkv());
		td.setDisplayName(feature.getLabel());
		td.setAudioLanguages(feature.getAudioLanguages());
		td.setSubtitleLanguages(feature.getSubtitleLanguages());
		// Default: single chapter at 00:00
		TitleDescriptor.ChapterMarker start = new TitleDescriptor.ChapterMarker();
		start.setTimeSeconds(0.0);
		td.setChapters(List.of(start));
		return td;
	}

}
