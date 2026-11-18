/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/main/java/org/brts/highlevel/shrink/DiscShrinkPlanner.java' is part of BRTS.
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

package org.brts.highlevel.shrink;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves playlist language preferences and allocates deterministic per-clip byte budgets. */
public final class DiscShrinkPlanner {

	public Plan plan(DiscShrinkDescriptor descriptor, List<Playlist> playlists, List<Clip> clips, long fixedBytes,
			long targetBytes) {
		if (descriptor == null) {
			descriptor = new DiscShrinkDescriptor();
		}
		if (playlists == null || clips == null) {
			throw new IllegalArgumentException("Playlists and clips are required");
		}
		if (fixedBytes < 0 || targetBytes <= 0) {
			throw new IllegalArgumentException("fixedBytes must be non-negative and targetBytes must be positive");
		}
		validatePreferences(descriptor);

		Map<String, Clip> clipsById = new LinkedHashMap<>();
		for (Clip clip : clips) {
			if (clip == null || clip.id() == null || !clip.id().matches("[0-9]{5}") || clip.sourceBytes() < 0) {
				throw new IllegalArgumentException("Each clip must have a five-digit ID and non-negative size");
			}
			if (clipsById.putIfAbsent(clip.id(), clip) != null) {
				throw new IllegalArgumentException("Duplicate clip ID " + clip.id());
			}
		}

		Map<String, Set<String>> playlistIdsByClip = new HashMap<>();
		Set<String> playlistNames = new HashSet<>();
		for (Playlist playlist : playlists) {
			if (playlist == null || playlist.id() == null || !playlist.id().matches("[0-9]{5}")
					|| !playlistNames.add(playlist.id())) {
				throw new IllegalArgumentException("Each playlist must have a unique five-digit ID");
			}
			for (String clipId : playlist.mainClipIds() == null ? List.<String>of() : playlist.mainClipIds()) {
				if (!clipsById.containsKey(clipId)) {
					throw new IllegalArgumentException(
							"Playlist " + playlist.id() + " references missing clip " + clipId);
				}
				playlistIdsByClip.computeIfAbsent(clipId, ignored -> new LinkedHashSet<>()).add(playlist.id());
			}
		}
		for (String playlistId : descriptor.getPlaylists() == null ? Set.<String>of()
				: descriptor.getPlaylists().keySet()) {
			if (playlistId == null || !playlistId.matches("[0-9]{5}")) {
				throw new IllegalArgumentException("Playlist preference keys must contain exactly five digits");
			}
			if (!playlistNames.contains(playlistId)) {
				throw new IllegalArgumentException("Playlist preferences reference missing playlist " + playlistId);
			}
		}

		Map<String, ClipPlan> result = new LinkedHashMap<>();
		List<Clip> reducible = new ArrayList<>();
		long committedFixedBytes = fixedBytes;
		for (Clip clip : clips) {
			List<String> refs = new ArrayList<>(playlistIdsByClip.getOrDefault(clip.id(), Set.of()));
			refs.sort(String::compareTo);
			Selection selection = selectionForClip(descriptor, clip, refs);
			boolean canShrink = clip.reducible() && !clip.hasEmbeddedIgs();
			if (canShrink) {
				reducible.add(clip);
			} else {
				committedFixedBytes = Math.addExact(committedFixedBytes, clip.sourceBytes());
			}
			result.put(clip.id(),
					new ClipPlan(clip.id(), clip.sourceBytes(), clip.sourceBytes(), selection.audioPids(),
							selection.subtitlePids(), refs, canShrink,
							clip.hasEmbeddedIgs() ? "embedded IGS; copied without reduction"
									: clip.reducible() ? null : "unsupported clip topology; copied without reduction"));
		}

		long availableForReducible = Math.max(0, targetBytes - committedFixedBytes);
		Map<String, Long> budgets = allocate(reducible, availableForReducible);
		for (Clip clip : reducible) {
			ClipPlan old = result.get(clip.id());
			result.put(clip.id(), new ClipPlan(old.clipId(), old.sourceBytes(), budgets.get(clip.id()),
					old.retainedAudioPids(), old.retainedSubtitlePids(), old.playlistIds(), true, null));
		}
		long reducibleBudget = 0;
		for (long budget : budgets.values()) {
			reducibleBudget = Math.addExact(reducibleBudget, budget);
		}
		return new Plan(targetBytes, committedFixedBytes, reducibleBudget, result);
	}

	private static Selection selectionForClip(DiscShrinkDescriptor descriptor, Clip clip, List<String> playlistIds) {
		if (clip.hasEmbeddedIgs() || !clip.reducible()) {
			return allSelected(clip);
		}
		Selection common = null;
		String commonPlaylist = null;
		if (playlistIds.isEmpty()) {
			return select(clip, descriptor.getAudioLanguages(), descriptor.getSubtitleLanguages());
		}
		for (String playlistId : playlistIds) {
			DiscShrinkDescriptor.PlaylistPreferences override = descriptor.getPlaylists() == null ? null
					: descriptor.getPlaylists().get(playlistId);
			List<String> audio = override == null || override.getAudioLanguages() == null
					? descriptor.getAudioLanguages()
					: override.getAudioLanguages();
			List<String> subtitles = override == null || override.getSubtitleLanguages() == null
					? descriptor.getSubtitleLanguages()
					: override.getSubtitleLanguages();
			Selection selected = select(clip, audio, subtitles);
			if (common != null && !common.equals(selected)) {
				throw new IllegalArgumentException("Playlist stream preferences conflict for shared clip " + clip.id()
						+ " between playlists " + commonPlaylist + " and " + playlistId);
			}
			common = selected;
			commonPlaylist = playlistId;
		}
		return common;
	}

	private static Selection allSelected(Clip clip) {
		List<Integer> audio = new ArrayList<>();
		List<Integer> subtitles = new ArrayList<>();
		for (Stream stream : clip.streams()) {
			if (stream.kind() == StreamKind.AUDIO) {
				audio.add(stream.pid());
			} else if (stream.kind() == StreamKind.SUBTITLE) {
				subtitles.add(stream.pid());
			}
		}
		return new Selection(sorted(audio), sorted(subtitles));
	}

	private static Selection select(Clip clip, List<String> audioLanguages, List<String> subtitleLanguages) {
		List<Integer> audio = new ArrayList<>();
		List<Integer> subtitles = new ArrayList<>();
		Set<String> audioCodes = codes(audioLanguages);
		Set<String> subtitleCodes = codes(subtitleLanguages);
		for (Stream stream : clip.streams()) {
			if (stream.kind() == StreamKind.AUDIO && selected(stream.language(), audioLanguages, audioCodes)) {
				audio.add(stream.pid());
			} else if (stream.kind() == StreamKind.SUBTITLE
					&& selected(stream.language(), subtitleLanguages, subtitleCodes)) {
				subtitles.add(stream.pid());
			}
		}
		return new Selection(sorted(audio), sorted(subtitles));
	}

	private static boolean selected(String language, List<String> preferences, Set<String> codes) {
		if (preferences == null) {
			return true;
		}
		String normalized = language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
		if (normalized.isEmpty() || "und".equals(normalized)) {
			return true;
		}
		return codes.contains(normalized);
	}

	private static Set<String> codes(List<String> values) {
		if (values == null) {
			return Set.of();
		}
		Set<String> result = new HashSet<>();
		for (String value : values) {
			result.add(value.toLowerCase(java.util.Locale.ROOT));
		}
		return result;
	}

	private static void validatePreferences(DiscShrinkDescriptor descriptor) {
		validateCodes(descriptor.getAudioLanguages(), "audioLanguages");
		validateCodes(descriptor.getSubtitleLanguages(), "subtitleLanguages");
		if (descriptor.getPlaylists() != null) {
			for (Map.Entry<String, DiscShrinkDescriptor.PlaylistPreferences> entry : descriptor.getPlaylists()
					.entrySet()) {
				if (entry.getValue() == null) {
					throw new IllegalArgumentException("Playlist preferences must not be null for " + entry.getKey());
				}
				validateCodes(entry.getValue().getAudioLanguages(), "playlists." + entry.getKey() + ".audioLanguages");
				validateCodes(entry.getValue().getSubtitleLanguages(),
						"playlists." + entry.getKey() + ".subtitleLanguages");
			}
		}
	}

	private static void validateCodes(List<String> values, String field) {
		if (values == null) {
			return;
		}
		for (String value : values) {
			if (value == null || !value.matches("(?i)[a-z]{3}")) {
				throw new IllegalArgumentException(field + " values must be three-letter language codes");
			}
		}
	}

	private static List<Integer> sorted(List<Integer> values) {
		values.sort(Integer::compareTo);
		return List.copyOf(values);
	}

	private static Map<String, Long> allocate(List<Clip> clips, long available) {
		Map<String, Long> result = new LinkedHashMap<>();
		long total = 0;
		for (Clip clip : clips) {
			total = Math.addExact(total, clip.sourceBytes());
		}
		if (total <= available) {
			for (Clip clip : clips) {
				result.put(clip.id(), clip.sourceBytes());
			}
			return result;
		}
		if (total == 0) {
			return result;
		}
		long assigned = 0;
		List<Clip> ordered = clips.stream().sorted(Comparator.comparing(Clip::id)).toList();
		for (Clip clip : ordered) {
			long share = BigInteger.valueOf(available).multiply(BigInteger.valueOf(clip.sourceBytes()))
					.divide(BigInteger.valueOf(total)).longValueExact();
			result.put(clip.id(), share);
			assigned = Math.addExact(assigned, share);
		}
		long remainder = available - assigned;
		for (Clip clip : ordered) {
			if (remainder == 0) {
				break;
			}
			long current = result.get(clip.id());
			if (current < clip.sourceBytes()) {
				result.put(clip.id(), current + 1);
				remainder--;
			}
		}
		return result;
	}

	private record Selection(List<Integer> audioPids, List<Integer> subtitlePids) {
	}

	public enum StreamKind {
		AUDIO, SUBTITLE, OTHER
	}

	public record Stream(int pid, StreamKind kind, String language) {
		public Stream {
			if (pid < 0x20 || pid > 0x1FFE || kind == null) {
				throw new IllegalArgumentException("Invalid stream PID or kind");
			}
		}
	}

	/** Inventory entry; playlist references contain main PlayItem clips only, not type-3 SubPath assets. */
	public record Clip(String id, long sourceBytes, List<Stream> streams, boolean hasEmbeddedIgs, boolean reducible) {
		public Clip {
			streams = streams == null ? List.of() : List.copyOf(streams);
		}
	}

	public record Playlist(String id, List<String> mainClipIds) {
		public Playlist {
			mainClipIds = mainClipIds == null ? List.of() : List.copyOf(mainClipIds);
		}
	}

	public record ClipPlan(String clipId, long sourceBytes, long targetBytes, List<Integer> retainedAudioPids,
			List<Integer> retainedSubtitlePids, List<String> playlistIds, boolean reducible, String note) {
		public ClipPlan {
			retainedAudioPids = List.copyOf(retainedAudioPids);
			retainedSubtitlePids = List.copyOf(retainedSubtitlePids);
			playlistIds = List.copyOf(playlistIds);
		}
	}

	public record Plan(long targetBytes, long fixedBytes, long allocatedClipBytes, Map<String, ClipPlan> clips) {
		public Plan {
			clips = Map.copyOf(clips);
		}
	}
}
