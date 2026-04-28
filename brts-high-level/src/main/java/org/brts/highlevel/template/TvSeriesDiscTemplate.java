package org.brts.highlevel.template;

import org.brts.highlevel.descriptor.TvSeriesDiscDescriptor;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.TitleDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Template for a TV series Blu-ray disc.
 * <p>
 * Each episode in the glob-expanded or explicit file list becomes a separate title. Titles are numbered from 1 upward
 * in sorted file-name order.
 */
public class TvSeriesDiscTemplate implements DiscTemplate<TvSeriesDiscDescriptor> {

	private static final Logger log = LoggerFactory.getLogger(TvSeriesDiscTemplate.class);

	@Override
	public String templateType() {
		return "TV_SERIES";
	}

	@Override
	public DiscDescriptor expand(TvSeriesDiscDescriptor descriptor) throws IOException {
		log.info("Expanding TV_SERIES template: {} S{:02d}", descriptor.getSeriesName(), descriptor.getSeasonNumber());

		List<String> episodePaths = resolveEpisodePaths(descriptor);
		log.info("Found {} episodes", episodePaths.size());

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName(descriptor.getDiscTitle());
		disc.setHasTopMenu(descriptor.isGenerateEpisodeMenu());

		List<TitleDescriptor> titles = new ArrayList<>();
		int titleId = 1;

		for (String episodePath : episodePaths) {
			TitleDescriptor td = new TitleDescriptor();
			td.setTitleId(titleId++);
			td.setSourceMkv(episodePath);
			td.setAudioLanguages(descriptor.getAudioLanguages());
			td.setSubtitleLanguages(descriptor.getSubtitleLanguages());
			// Auto-chapter at start; episode chapters can be added via metadata in future
			TitleDescriptor.ChapterMarker start = new TitleDescriptor.ChapterMarker();
			start.setTimeSeconds(0.0);
			td.setChapters(List.of(start));
			titles.add(td);
		}

		disc.setTitles(titles);
		return disc;
	}

	private List<String> resolveEpisodePaths(TvSeriesDiscDescriptor descriptor) throws IOException {
		// Explicit list takes priority
		if (descriptor.getEpisodeFiles() != null && !descriptor.getEpisodeFiles().isEmpty()) {
			return descriptor.getEpisodeFiles();
		}
		// Glob expansion
		if (descriptor.getEpisodesGlob() != null) {
			String glob = descriptor.getEpisodesGlob();
			// Split into base directory and glob pattern
			Path globPath = Path.of(glob);
			Path baseDir;
			String pattern;
			if (glob.contains("*") || glob.contains("?") || glob.contains("{")) {
				// Find last non-glob segment as base dir
				int lastSep = Math.max(glob.lastIndexOf('/'), glob.lastIndexOf('\\'));
				baseDir = Path.of(lastSep > 0 ? glob.substring(0, lastSep) : ".");
				pattern = "glob:" + glob;
			} else {
				baseDir = globPath.getParent() != null ? globPath.getParent() : Path.of(".");
				pattern = "glob:" + glob;
			}
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
			try (var stream = Files.walk(baseDir, 1)) {
				return stream.filter(p -> !Files.isDirectory(p)).filter(matcher::matches)
						.sorted(Comparator.comparing(Path::getFileName)).map(p -> p.toAbsolutePath().toString())
						.collect(Collectors.toList());
			}
		}
		throw new IllegalArgumentException("TvSeriesDiscDescriptor must specify either episodesGlob or episodeFiles");
	}

}
