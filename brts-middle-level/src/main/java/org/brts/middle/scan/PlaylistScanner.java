package org.brts.middle.scan;

import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scans all {@code .mpls} files in a Blu-ray {@code BDMV/PLAYLIST} directory, parses each
 * one using the low-level {@link MoviePlaylistParser}, and applies configurable
 * heuristics to select the "interesting" playlists that most likely represent the main
 * content of the disc.
 */
public class PlaylistScanner {

	private static final Logger log = LoggerFactory.getLogger(PlaylistScanner.class);

	private final MoviePlaylistParser parser = new MoviePlaylistParser();

	/**
	 * Scans the given PLAYLIST directory and returns the curated result.
	 * @param playlistDir path to the {@code BDMV/PLAYLIST} directory
	 * @param config scanning/filtering configuration (use
	 * {@link PlaylistScanConfig#defaults()} for sane defaults)
	 * @return the scan result with detected type and interesting playlists
	 */
	public PlaylistScanResult scan(Path playlistDir, PlaylistScanConfig config) throws IOException {
		List<PlaylistScanResult.AnnotatedPlaylist> allParsed = parseAllPlaylists(playlistDir);

		// Sort by duration descending for easier heuristic processing
		allParsed.sort(Comparator.comparingDouble(PlaylistScanResult.AnnotatedPlaylist::getDurationSeconds).reversed());

		// Optionally exclude menus
		List<PlaylistScanResult.AnnotatedPlaylist> candidates = config.isExcludeMenus()
				? allParsed.stream().filter(a -> !a.isMenu()).toList() : allParsed;

		log.info("Parsed {} playlists ({} after menu exclusion) from {}", allParsed.size(), candidates.size(),
				playlistDir);

		DiscContentType type = detectType(candidates, config);
		List<PlaylistScanResult.AnnotatedPlaylist> interesting = filterInteresting(candidates, type, config);

		PlaylistScanResult result = new PlaylistScanResult();
		result.setDetectedType(type);
		result.setPlaylists(interesting);
		result.setSummary(buildSummary(type, interesting, candidates));
		return result;
	}

	// ─── Parsing ────────────────────────────────────────────────────────────

	private List<PlaylistScanResult.AnnotatedPlaylist> parseAllPlaylists(Path playlistDir) throws IOException {
		List<PlaylistScanResult.AnnotatedPlaylist> result = new ArrayList<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(playlistDir, "*.mpls")) {
			for (Path mplsFile : stream) {
				try {
					MoviePlaylist playlist = parser.parse(mplsFile);
					if (playlist.getPlaylistName() == null) {
						// Derive name from file
						String fname = mplsFile.getFileName().toString();
						playlist.setPlaylistName(fname.replace(".mpls", ""));
					}

					PlaylistScanResult.AnnotatedPlaylist ap = new PlaylistScanResult.AnnotatedPlaylist();
					ap.setPlaylist(playlist);
					ap.setFileName(mplsFile.getFileName().toString());
					ap.setMenu(playlist.isMenu());

					double dur = computeDurationSeconds(playlist);
					ap.setDurationSeconds(dur);
					ap.setDurationFormatted(formatDuration(dur));

					result.add(ap);
				}
				catch (Exception e) {
					log.warn("Skipping unparseable playlist {}: {}", mplsFile.getFileName(), e.getMessage());
				}
			}
		}
		return result;
	}

	// ─── Duration helpers ───────────────────────────────────────────────────

	static double computeDurationSeconds(MoviePlaylist playlist) {
		if (playlist.getPlayItems() == null || playlist.getPlayItems().isEmpty()) {
			return 0;
		}
		long totalTicks = 0;
		for (PlayItem item : playlist.getPlayItems()) {
			totalTicks += item.getOutTimeTicks() - item.getInTimeTicks();
		}
		// MPLS PlayItem IN/OUT times are in the 45 kHz STC domain, not 90 kHz PTS
		return (double) totalTicks / Timestamp.MPLS_TICKS_PER_SECOND;
	}

	static String formatDuration(double seconds) {
		long totalSec = Math.round(seconds);
		long h = totalSec / 3600;
		long m = (totalSec % 3600) / 60;
		long s = totalSec % 60;
		return String.format("%02d:%02d:%02d", h, m, s);
	}

	// ─── Type detection ─────────────────────────────────────────────────────

	private DiscContentType detectType(List<PlaylistScanResult.AnnotatedPlaylist> candidates,
			PlaylistScanConfig config) {
		if (config.getForcedType() != null) {
			log.info("Content type forced to {}", config.getForcedType());
			return config.getForcedType();
		}

		double movieMinSec = config.getMovieMinMinutes() * 60;
		double episodeMinSec = config.getEpisodeMinMinutes() * 60;

		// Candidates above episode min duration
		List<PlaylistScanResult.AnnotatedPlaylist> longEnough = candidates.stream()
			.filter(a -> a.getDurationSeconds() >= episodeMinSec)
			.toList();

		if (longEnough.isEmpty()) {
			log.info("No playlists above {} min; defaulting to MOVIE", config.getEpisodeMinMinutes());
			return DiscContentType.MOVIE;
		}

		// Check for TV-series pattern: multiple episodes of similar duration
		if (longEnough.size() >= config.getEpisodeMinCount()) {
			double maxDur = longEnough.stream()
				.mapToDouble(PlaylistScanResult.AnnotatedPlaylist::getDurationSeconds)
				.max()
				.orElse(0);
			double minDur = longEnough.stream()
				.mapToDouble(PlaylistScanResult.AnnotatedPlaylist::getDurationSeconds)
				.min()
				.orElse(0);

			if (minDur > 0 && (maxDur / minDur) <= config.getEpisodeMaxDurationRatio()) {
				// All "long enough" playlists are within the acceptable ratio → series
				// But if the longest one is also above movie threshold, it could be
				// ambiguous.
				// Use series if we have at least episodeMinCount episodes.
				log.info("Detected TV_SERIES: {} playlists of similar duration ({} – {} min)", longEnough.size(),
						String.format("%.1f", minDur / 60), String.format("%.1f", maxDur / 60));
				return DiscContentType.TV_SERIES;
			}
		}

		// Default: movie
		log.info("Detected MOVIE (longest playlist: {} min)",
				String.format("%.1f", longEnough.get(0).getDurationSeconds() / 60));
		return DiscContentType.MOVIE;
	}

	// ─── Interesting playlist filter ────────────────────────────────────────

	private List<PlaylistScanResult.AnnotatedPlaylist> filterInteresting(
			List<PlaylistScanResult.AnnotatedPlaylist> candidates, DiscContentType type, PlaylistScanConfig config) {

		List<PlaylistScanResult.AnnotatedPlaylist> result = new ArrayList<>();

		switch (type) {
			case MOVIE -> filterMovie(candidates, config, result);
			case TV_SERIES -> filterSeries(candidates, config, result);
		}

		return result;
	}

	private void filterMovie(List<PlaylistScanResult.AnnotatedPlaylist> candidates, PlaylistScanConfig config,
			List<PlaylistScanResult.AnnotatedPlaylist> result) {
		double movieMinSec = config.getMovieMinMinutes() * 60;

		if (candidates.isEmpty())
			return;

		// The longest playlist is the main movie candidate
		double longestDur = candidates.get(0).getDurationSeconds();

		boolean mainFound = false;
		for (PlaylistScanResult.AnnotatedPlaylist ap : candidates) {
			if (ap.getDurationSeconds() < movieMinSec)
				continue;

			if (!mainFound) {
				ap.setRole("main movie");
				mainFound = true;
			}
			else if (ap.getDurationSeconds() >= longestDur * config.getMovieAlternateCutRatio()) {
				ap.setRole("alternate/director's cut");
			}
			else {
				continue; // too short relative to main
			}
			result.add(ap);
		}

		// If nothing matched the movie threshold, include the single longest playlist
		// anyway
		if (result.isEmpty() && !candidates.isEmpty()) {
			PlaylistScanResult.AnnotatedPlaylist best = candidates.get(0);
			best.setRole("main movie (below threshold)");
			result.add(best);
		}
	}

	private void filterSeries(List<PlaylistScanResult.AnnotatedPlaylist> candidates, PlaylistScanConfig config,
			List<PlaylistScanResult.AnnotatedPlaylist> result) {
		double episodeMinSec = config.getEpisodeMinMinutes() * 60;
		int episodeNum = 1;

		for (PlaylistScanResult.AnnotatedPlaylist ap : candidates) {
			if (ap.getDurationSeconds() >= episodeMinSec) {
				ap.setRole("episode " + episodeNum);
				episodeNum++;
				result.add(ap);
			}
		}

		// Sort episodes by playlist name (natural order on disc)
		result.sort(Comparator.comparing(a -> a.getPlaylist().getPlaylistName()));

		// Re-number after sorting
		for (int i = 0; i < result.size(); i++) {
			result.get(i).setRole("episode " + (i + 1));
		}
	}

	// ─── Summary ────────────────────────────────────────────────────────────

	private String buildSummary(DiscContentType type, List<PlaylistScanResult.AnnotatedPlaylist> interesting,
			List<PlaylistScanResult.AnnotatedPlaylist> allCandidates) {
		return switch (type) {
			case MOVIE ->
				String.format(
						"Detected MOVIE disc — %d interesting playlist(s) out of %d total (non-menu). "
								+ "Longest: %s.",
						interesting.size(), allCandidates.size(),
						interesting.isEmpty() ? "n/a" : interesting.get(0).getDurationFormatted());
			case TV_SERIES -> String.format("Detected TV_SERIES disc — %d episode(s) out of %d total (non-menu).",
					interesting.size(), allCandidates.size());
		};
	}

}
