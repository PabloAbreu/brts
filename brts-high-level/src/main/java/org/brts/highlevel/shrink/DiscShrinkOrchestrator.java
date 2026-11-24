/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/main/java/org/brts/highlevel/shrink/DiscShrinkOrchestrator.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.brts.common.m2ts.TargetRootWorkspace;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.m2ts.PreservingM2tsClipWriter;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayItemStream;
import org.brts.lowlevel.model.mpls.SubPath;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.brts.lowlevel.shrink.BatchWholeM2tsConverter;
import org.brts.lowlevel.shrink.M2tsShrinkCache;
import org.brts.lowlevel.shrink.M2tsShrinkConfig;
import org.brts.lowlevel.shrink.M2tsShrinkInputResolver;
import org.brts.lowlevel.shrink.M2tsShrinkInventory;
import org.brts.lowlevel.shrink.M2tsShrinkPlan;
import org.brts.lowlevel.shrink.M2tsShrinkPlanner;
import org.brts.lowlevel.shrink.WholeM2tsConverter;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.writer.MoviePlaylistWriter;

import lombok.extern.slf4j.Slf4j;

/** Executes a conservative whole-disc shrink campaign into a separate output root. */
@Slf4j
public final class DiscShrinkOrchestrator {
	private static final Set<StreamCodingType> SUPPORTED = Set.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC,
			StreamCodingType.DOLBY_AC3, StreamCodingType.DOLBY_AC3_PLUS, StreamCodingType.DOLBY_TRUEHD,
			StreamCodingType.DTS, StreamCodingType.DTS_HD, StreamCodingType.DTS_HD_MASTER_AUDIO,
			StreamCodingType.DTS_EXPRESS, StreamCodingType.PRESENTATION_GRAPHICS);

	private final WholeM2tsConverter.BatchExecutor batchExecutor;
	private final M2tsShrinkCache.Discovery discovery;

	public DiscShrinkOrchestrator(WholeM2tsConverter.BatchExecutor batchExecutor) {
		this(batchExecutor, new M2tsShrinkInputResolver()::discover);
	}

	DiscShrinkOrchestrator(WholeM2tsConverter.BatchExecutor batchExecutor, M2tsShrinkCache.Discovery discovery) {
		this.batchExecutor = java.util.Objects.requireNonNull(batchExecutor);
		this.discovery = java.util.Objects.requireNonNull(discovery);
	}

	public Result shrink(Path input, Path output, DiscShrinkDescriptor descriptor, long targetBytes, Engine engine)
			throws IOException {
		Path source = input.toRealPath();
		Path destination = TargetRootWorkspace.canonical(output);
		if (!Files.isDirectory(source.resolve("BDMV"))) {
			throw new IOException("Input must be a disc root containing BDMV: " + source);
		}
		if (targetBytes <= 0 || engine == null) {
			throw new IllegalArgumentException("targetBytes must be positive and engine is required");
		}
		if (destination.startsWith(source) || source.startsWith(destination)) {
			throw new IOException("Input and output disc roots must be separate");
		}
		if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
			if (!Files.isDirectory(destination)) {
				throw new IOException("Output must be a nonexistent or empty directory: " + destination);
			}
			try (var children = Files.list(destination)) {
				if (children.findAny().isPresent()) {
					throw new IOException("Output must be a nonexistent or empty directory: " + destination);
				}
			}
		} else {
			Files.createDirectories(destination);
		}
		if (!TargetRootWorkspace.canonical(destination).equals(destination)) {
			throw new IOException("Output root changed while preparing disc shrink: " + destination);
		}

		log.info("Starting disc shrink: input={}, output={}, target={} bytes, engine={}", source, destination,
				targetBytes, engine);
		List<String> diagnostics = new ArrayList<>();
		log.info("Scanning disc clips and playlists for reducible files");
		Inventory inventory = inventory(source, diagnostics);
		DiscShrinkPlanner.Plan plan = new DiscShrinkPlanner().plan(descriptor, inventory.playlists(), inventory.clips(),
				inventory.fixedBytes(), targetBytes);
		Set<String> reducibleIds = plan.clips().values().stream().filter(DiscShrinkPlanner.ClipPlan::reducible)
				.map(DiscShrinkPlanner.ClipPlan::clipId).collect(Collectors.toSet());
		log.info("Disc shrink plan: {} clips, {} reducible, {} kept unchanged; fixed={} bytes, target={} bytes",
				plan.clips().size(), reducibleIds.size(), plan.clips().size() - reducibleIds.size(), plan.fixedBytes(),
				targetBytes);
		for (var clipPlan : plan.clips().values().stream()
				.sorted(Comparator.comparing(DiscShrinkPlanner.ClipPlan::clipId)).toList()) {
			if (clipPlan.reducible()) {
				log.info(
						"Reducible file {}.m2ts: source={} bytes, target={} bytes, retained audio PIDs={}, subtitle PIDs={}",
						clipPlan.clipId(), clipPlan.sourceBytes(), clipPlan.targetBytes(), clipPlan.retainedAudioPids(),
						clipPlan.retainedSubtitlePids());
			} else {
				log.info("Keeping {}.m2ts unchanged ({} bytes): {}", clipPlan.clipId(), clipPlan.sourceBytes(),
						clipPlan.note());
			}
		}
		log.info("Copying unchanged files to {}", destination);
		copyTree(source, destination, reducibleIds);
		log.info("Finished copying unchanged files");

		plan.clips().values().stream().filter(p -> !p.reducible())
				.sorted(Comparator.comparing(DiscShrinkPlanner.ClipPlan::clipId))
				.forEach(p -> diagnostics.add("Clip " + p.clipId() + ": " + p.note()));
		Set<String> changedClips = new HashSet<>();
		List<String> failedClips = new ArrayList<>();
		Map<String, Set<Integer>> keptPids = new HashMap<>();
		int clipIndex = 0;
		for (String clipId : reducibleIds.stream().sorted().toList()) {
			DiscShrinkPlanner.ClipPlan clipPlan = plan.clips().get(clipId);
			Path sourceM2ts = source.resolve("BDMV/STREAM/" + clipId + ".m2ts");
			Path sourceClpi = source.resolve("BDMV/CLIPINF/" + clipId + ".clpi");
			Path outputM2ts = destination.resolve("BDMV/STREAM/" + clipId + ".m2ts");
			Path outputClpi = destination.resolve("BDMV/CLIPINF/" + clipId + ".clpi");
			log.info("Processing reducible file [{}/{}]: {} (source={} bytes, target={} bytes)", ++clipIndex,
					reducibleIds.size(), sourceM2ts, clipPlan.sourceBytes(), clipPlan.targetBytes());
			Set<Integer> keep = retainedPids(inventory.metadata().get(clipId).inventory(), clipPlan);
			boolean pidFilter = hasRemovedAudioOrSubtitle(inventory.metadata().get(clipId).inventory(), keep);
			if (!pidFilter && clipPlan.targetBytes() >= clipPlan.sourceBytes()) {
				log.info("Copying {}.m2ts unchanged: already within budget and no streams to remove", clipId);
				copyPair(sourceM2ts, sourceClpi, outputM2ts, outputClpi);
				continue;
			}
			try {
				M2tsShrinkConfig config = new M2tsShrinkConfig();
				config.setAudioPids(clipPlan.retainedAudioPids());
				config.setSubtitlePids(clipPlan.retainedSubtitlePids());
				log.info("Analyzing and preparing extraction for {}.m2ts", clipId);
				M2tsShrinkInputResolver resolver = new M2tsShrinkInputResolver();
				M2tsShrinkInventory counts = resolver.resolve(source, clipId);
				M2tsShrinkCache.Prepared prepared = new M2tsShrinkCache(resolver).prepare(source, clipId, config,
						destination, null);
				M2tsShrinkPlan clipShrink = new M2tsShrinkPlanner().plan(counts, clipId, config,
						Math.max(1, clipPlan.targetBytes()), engine);
				if (clipShrink.decision() == M2tsShrinkPlan.Decision.AUDIO_PID_REMOVAL_FITS_ESTIMATE) {
					log.info("Reducing {}.m2ts by removing unselected streams without video conversion", clipId);
					writeFiltered(prepared, counts, clipPlan, outputM2ts, outputClpi);
				} else {
					log.info("Converting {}.m2ts using {} (decision={})", clipId, engine, clipShrink.decision());
					WholeM2tsConverter converter = new BatchWholeM2tsConverter(engine, batchExecutor);
					converter.convert(new WholeM2tsConverter.M2tsConversionRequest(counts, clipShrink, config,
							destination, null, clipId, false, prepared.cache().reference()));
				}
				if (!Files.isRegularFile(outputM2ts) || !Files.isRegularFile(outputClpi)) {
					throw new IOException("Conversion did not produce a complete M2TS/CLPI pair");
				}
				long outputBytes = Files.size(outputM2ts);
				log.info("Finished {}.m2ts: source={} bytes, output={} bytes, saved={} bytes", clipId,
						clipPlan.sourceBytes(), outputBytes, clipPlan.sourceBytes() - outputBytes);
				if (outputBytes > clipPlan.targetBytes()) {
					diagnostics
							.add("Clip " + clipId + ".m2ts completed reduction but exceeded its planned budget: output="
									+ outputBytes + " bytes, target=" + clipPlan.targetBytes() + " bytes, excess="
									+ (outputBytes - clipPlan.targetBytes()) + " bytes");
				}
				changedClips.add(clipId);
				keptPids.put(clipId, keep);
			} catch (Exception e) {
				log.warn(
						"Could not reduce {}.m2ts; restoring original M2TS/CLPI pair. sourceM2ts={}, sourceClpi={}, outputM2ts={}, outputClpi={}, sourceBytes={}, targetBytes={}, engine={}, retainedPids={}, pidFilter={}",
						clipId, sourceM2ts, sourceClpi, outputM2ts, outputClpi, clipPlan.sourceBytes(),
						clipPlan.targetBytes(), engine, keep, pidFilter, e);
				// A failed writer may have published either or both files. Never retain a mixed pair.
				Files.deleteIfExists(outputM2ts);
				Files.deleteIfExists(outputClpi);
				copyPair(sourceM2ts, sourceClpi, outputM2ts, outputClpi);
				failedClips.add(clipId + ".m2ts");
				diagnostics.add("Clip " + clipId + ".m2ts reduction failed; original M2TS/CLPI pair restored: "
						+ failureReason(e) + "; restored=" + clipPlan.sourceBytes() + " bytes, target="
						+ clipPlan.targetBytes() + " bytes, over planned budget="
						+ Math.max(0, clipPlan.sourceBytes() - clipPlan.targetBytes()) + " bytes");
			}
		}

		if (!changedClips.isEmpty()) {
			log.info("Updating playlists for {} changed clips", changedClips.size());
			writeFilteredPlaylists(destination, inventory.playlistModels(), changedClips, keptPids);
		}
		log.info("Cleaning temporary workspaces and measuring output disc size");
		cleanupWorkspaces(destination);
		long actualBytes = treeBytes(destination);
		Result result = new Result(source, destination, targetBytes, actualBytes, actualBytes <= targetBytes,
				List.copyOf(diagnostics));
		log.info("Disc shrink result: {} clips changed, actual={} bytes, target={} bytes, within limit={}, output={}",
				changedClips.size(), actualBytes, targetBytes, result.withinLimit(), destination);
		if (!result.withinLimit()) {
			throw new IOException("Disc shrink output exceeds --target-bytes: actual=" + actualBytes + ", limit="
					+ targetBytes + ", excess=" + (actualBytes - targetBytes) + " bytes; output retained at "
					+ destination + "; clips changed=" + changedClips.size() + ", failed reductions="
					+ failedClips.size() + (failedClips.isEmpty() ? "" : " (" + String.join(", ", failedClips) + ")")
					+ ", planned fixed=" + plan.fixedBytes() + " bytes" + (diagnostics.isEmpty() ? ""
							: "\nDisc shrink diagnostics:\n - " + String.join("\n - ", diagnostics)));
		}
		return result;
	}

	private static String failureReason(Throwable failure) {
		List<String> reasons = new ArrayList<>();
		Set<Throwable> seen = new HashSet<>();
		for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
			reasons.add(cause.getClass().getSimpleName()
					+ (cause.getMessage() == null || cause.getMessage().isBlank() ? "" : ": " + cause.getMessage()));
		}
		return String.join(" -> ", reasons);
	}

	private Inventory inventory(Path root, List<String> diagnostics) throws IOException {
		Path streamDir = root.resolve("BDMV/STREAM");
		Path playlistDir = root.resolve("BDMV/PLAYLIST");
		if (!Files.isDirectory(streamDir) || !Files.isDirectory(playlistDir)) {
			throw new IOException("Input BDMV must contain STREAM and PLAYLIST directories");
		}
		Map<String, M2tsShrinkInputResolver.Metadata> metadata = new LinkedHashMap<>();
		Map<String, Long> clipSizes = new LinkedHashMap<>();
		try (var files = Files.list(streamDir)) {
			for (Path file : files.filter(p -> p.getFileName().toString().matches("[0-9]{5}\\.m2ts")).sorted()
					.toList()) {
				String clipId = file.getFileName().toString().substring(0, 5);
				clipSizes.put(clipId, Files.size(file));
				log.info("Inspecting clip {} ({} bytes)", file, clipSizes.get(clipId));
				try {
					metadata.put(clipId, discovery.discover(root, clipId));
				} catch (IOException | RuntimeException e) {
					log.warn("Keeping {} unchanged because discovery failed: {}", file, e.getMessage());
					diagnostics
							.add("Clip " + clipId + " was kept unchanged because discovery failed: " + e.getMessage());
				}
			}
		}
		Map<String, MoviePlaylist> models = new LinkedHashMap<>();
		List<DiscShrinkPlanner.Playlist> playlists = new ArrayList<>();
		Set<String> embeddedIgs = new HashSet<>();
		Set<String> subPathIgs = new HashSet<>();
		boolean unreadablePlaylist = false;
		try (var files = Files.list(playlistDir)) {
			for (Path file : files.filter(p -> p.getFileName().toString().matches("[0-9]{5}\\.mpls")).sorted()
					.toList()) {
				String id = file.getFileName().toString().substring(0, 5);
				log.info("Inspecting playlist {}", file);
				MoviePlaylist playlist;
				try {
					playlist = new MoviePlaylistParser().parse(file);
				} catch (IOException | RuntimeException e) {
					log.warn("Could not read playlist {}; all clips will be kept unchanged: {}", file, e.getMessage());
					// Unknown references/IGS dependencies mean no clip can safely be rewritten.
					unreadablePlaylist = true;
					playlists.add(new DiscShrinkPlanner.Playlist(id, List.of()));
					diagnostics.add("Playlist " + id + " could not be read; all clips will be kept unchanged: "
							+ e.getMessage());
					continue;
				}
				playlist.setPlaylistName(id);
				models.put(id, playlist);
				List<String> mainIds = new ArrayList<>();
				for (PlayItem item : playlist.getPlayItems() == null ? List.<PlayItem>of() : playlist.getPlayItems()) {
					if (item.getClipName() != null) {
						mainIds.add(item.getClipName());
						// Out-of-mux IGS entries belong to the SubPath clip, not this main clip.
						if (item.getStreams() != null && item.getStreams().stream().anyMatch(
								s -> s.getCodingType() != null && s.getCodingType().isMenu() && (s.getStreamType() == 0
										|| s.getStreamType() == PlayItemStream.STREAM_TYPE_IN_MUX))) {
							embeddedIgs.add(item.getClipName());
						}
					}
				}
				for (SubPath subPath : playlist.getSubPaths() == null ? List.<SubPath>of() : playlist.getSubPaths()) {
					if (subPath.getSubPathType() == 3 && subPath.getSubPlayItems() != null) {
						for (SubPath.SubPlayItem subPlayItem : subPath.getSubPlayItems()) {
							if (subPlayItem.getClipName() != null) {
								subPathIgs.add(subPlayItem.getClipName());
							}
						}
					}
				}
				playlists.add(new DiscShrinkPlanner.Playlist(id, mainIds));
			}
		}
		List<DiscShrinkPlanner.Clip> clips = new ArrayList<>();
		for (var clip : clipSizes.entrySet()) {
			String clipId = clip.getKey();
			var clipMetadata = metadata.get(clipId);
			if (clipMetadata == null) {
				clips.add(new DiscShrinkPlanner.Clip(clip.getKey(), clip.getValue(), List.of(), false, false));
				continue;
			}
			var tracks = clipMetadata.inventory().tracks();
			if (clipMetadata.atcDelta()) {
				diagnostics.add("Clip " + clipId + ": ATC-delta preservation unsupported; copied unchanged");
			}
			if (clipMetadata.applicationType() != ClipInfo.APPLICATION_TYPE_MOVIE) {
				diagnostics.add("Clip " + clipId + ": non-movie CLPI application type " + clipMetadata.applicationType()
						+ " unsupported; copied unchanged");
			}
			boolean supported = !unreadablePlaylist && supportedTopology(clipMetadata);
			boolean hasIgs = embeddedIgs.contains(clipId) || subPathIgs.contains(clipId)
					|| tracks.stream().anyMatch(t -> t.codingType() != null && t.codingType().isMenu());
			List<DiscShrinkPlanner.Stream> streams = tracks.stream()
					.map(t -> new DiscShrinkPlanner.Stream(t.pid(),
							t.codingType() != null && t.codingType().isAudio() ? DiscShrinkPlanner.StreamKind.AUDIO
									: t.codingType() != null && t.codingType().isSubtitle()
											? DiscShrinkPlanner.StreamKind.SUBTITLE
											: DiscShrinkPlanner.StreamKind.OTHER,
							t.language()))
					.toList();
			clips.add(new DiscShrinkPlanner.Clip(clipId, clip.getValue(), streams, hasIgs, supported));
		}
		long fixed = 0;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				String name = path.getFileName().toString();
				String id = name.length() >= 5 ? name.substring(0, 5) : "";
				boolean isClipMedia = path.getParent().equals(root.resolve("BDMV/STREAM")) && clipSizes.containsKey(id)
						&& name.equals(id + ".m2ts");
				if (!isClipMedia) {
					fixed = Math.addExact(fixed, Files.size(path));
				}
			}
		}
		return new Inventory(playlists, clips, fixed, metadata, models);
	}

	private static boolean supportedTopology(M2tsShrinkInputResolver.Metadata metadata) {
		var tracks = metadata.inventory().tracks();
		long videos = retainedVideoTracks(tracks).size();
		boolean primary = tracks.stream().anyMatch(t -> t.pid() == 0x1011
				&& (t.codingType() == StreamCodingType.H264_AVC || t.codingType() == StreamCodingType.H265_HEVC));
		return metadata.applicationType() == ClipInfo.APPLICATION_TYPE_MOVIE && !metadata.atcDelta()
				&& metadata.inventory().duration90k() > 0 && primary && videos == 1
				&& tracks.stream().allMatch(
						t -> t.codingType() != null && !t.codingType().isMenu() && SUPPORTED.contains(t.codingType()))
				&& metadata.info().getStreams() != null
				&& metadata.info().getStreams().stream()
						.allMatch(s -> s.getCodingType() != null && !s.getCodingType().isMenu()
								&& SUPPORTED.contains(s.getCodingType()))
				&& (metadata.info().getProgramPidMap() == null || metadata.info().getProgramPidMap().size() <= 1);
	}

	/**
	 * Video tracks kept by m2ts-shrink: all of them, except the HEVC Dolby Vision companion (PID 0x1015) that
	 * accompanies a primary HEVC stream (PID 0x1011), which is dropped. Same heuristic as m2ts-shrink.
	 */
	private static List<M2tsShrinkInventory.Track> retainedVideoTracks(List<M2tsShrinkInventory.Track> tracks) {
		var videos = tracks.stream().filter(t -> t.codingType() != null && t.codingType().isVideo()).toList();
		boolean dolbyVisionCompanion = videos.size() == 2
				&& videos.stream().anyMatch(t -> t.pid() == 0x1011 && t.codingType() == StreamCodingType.H265_HEVC)
				&& videos.stream().anyMatch(t -> t.pid() == 0x1015 && t.codingType() == StreamCodingType.H265_HEVC);
		return dolbyVisionCompanion ? videos.stream().filter(t -> t.pid() != 0x1015).toList() : videos;
	}

	private static Set<Integer> retainedPids(M2tsShrinkInventory inventory, DiscShrinkPlanner.ClipPlan plan) {
		Set<Integer> result = retainedVideoTracks(inventory.tracks()).stream().map(M2tsShrinkInventory.Track::pid)
				.collect(Collectors.toSet());
		result.addAll(plan.retainedAudioPids());
		result.addAll(plan.retainedSubtitlePids());
		return result;
	}

	private static boolean hasRemovedAudioOrSubtitle(M2tsShrinkInventory inventory, Set<Integer> keep) {
		return inventory.tracks().stream().anyMatch(t -> t.codingType() != null
				&& (t.codingType().isAudio() || t.codingType().isSubtitle()) && !keep.contains(t.pid()));
	}

	private static void writeFiltered(M2tsShrinkCache.Prepared prepared, M2tsShrinkInventory inventory,
			DiscShrinkPlanner.ClipPlan plan, Path outputM2ts, Path outputClpi) throws IOException {
		var cache = prepared.cache();
		var descriptor = new M2tsDescriptor();
		descriptor.setOutputName(plan.clipId());
		descriptor.setSourceM2ts(inventory.m2tsPath().toString());
		descriptor.setSourceClpi(inventory.clpiPath().toString());
		descriptor.setTimingManifest(cache.directory().resolve(M2tsTimingManifest.FILE_NAME).toString());
		descriptor.setExtractionCacheReference(cache.reference());
		descriptor.setMaximumOutputBytes(plan.targetBytes());
		Set<Integer> pids = new HashSet<>(plan.retainedAudioPids());
		pids.addAll(plan.retainedSubtitlePids());
		retainedVideoTracks(inventory.tracks()).stream().map(M2tsShrinkInventory.Track::pid).forEach(pids::add);
		Map<Integer, M2tsTimingManifest.StreamTiming> timing = cache.timing().streams().stream()
				.collect(Collectors.toMap(M2tsTimingManifest.StreamTiming::pid, s -> s));
		var info = cache.manifest().info();
		List<M2tsDescriptor.StreamEntry> entries = new ArrayList<>();
		for (int pid : pids.stream().sorted().toList()) {
			var stream = info.getStreams().stream().filter(s -> s.getPid() == pid).findFirst()
					.orElseThrow(() -> new IOException("Missing stream metadata for PID " + pid));
			var trackTiming = timing.get(pid);
			if (trackTiming == null) {
				throw new IOException("Missing extraction timing for retained PID " + pid);
			}
			var entry = new M2tsDescriptor.StreamEntry();
			entry.setFile(cache.directory().resolve(trackTiming.fileName()).toString());
			entry.setPid(pid);
			entry.setStreamTypeByte(stream.getStreamTypeByte());
			entry.setLanguage(stream.getLanguage());
			entry.setFrameRateFps(stream.getFrameRateFps());
			entry.setVideoFormat(stream.getVideoFormat());
			entry.setSampleRateHz(stream.getSampleRateHz());
			entry.setBitrateKbps(stream.getBitrateKbps());
			entry.setChannels(stream.getChannels());
			entries.add(entry);
		}
		descriptor.setStreams(entries);
		new PreservingM2tsClipWriter().write(descriptor, outputM2ts, outputClpi);
	}

	private static void writeFilteredPlaylists(Path destination, Map<String, MoviePlaylist> playlists,
			Set<String> changed, Map<String, Set<Integer>> keptPids) throws IOException {
		MoviePlaylistWriter writer = new MoviePlaylistWriter();
		for (var entry : playlists.entrySet()) {
			boolean modified = false;
			for (PlayItem item : entry.getValue().getPlayItems() == null ? List.<PlayItem>of()
					: entry.getValue().getPlayItems()) {
				if (!changed.contains(item.getClipName()) || item.getStreams() == null) {
					continue;
				}
				Set<Integer> keep = keptPids.get(item.getClipName());
				List<org.brts.lowlevel.model.mpls.PlayItemStream> selected = item.getStreams().stream()
						.filter(s -> s.getCodingType() == null
								|| !s.getCodingType().isAudio() && !s.getCodingType().isSubtitle()
								|| keep.contains(s.getPid()))
						.toList();
				if (selected.size() != item.getStreams().size()) {
					item.setStreams(selected);
					modified = true;
				}
			}
			if (modified) {
				Path output = destination.resolve("BDMV/PLAYLIST/" + entry.getKey() + ".mpls");
				log.info("Updating playlist {} to reference retained streams", output);
				writer.write(entry.getValue(), output);
			}
		}
	}

	private static void copyTree(Path source, Path destination, Set<String> skippedIds) throws IOException {
		try (var paths = Files.walk(source)) {
			for (Path path : paths.sorted().toList()) {
				if (path.equals(source)) {
					continue;
				}
				if (Files.isSymbolicLink(path)) {
					throw new IOException("Symbolic links are not supported in disc roots: " + path);
				}
				Path relative = source.relativize(path);
				Path target = destination.resolve(relative);
				if (Files.isDirectory(path)) {
					Files.createDirectories(target);
				} else if (Files.isRegularFile(path)) {
					String file = path.getFileName().toString();
					String id = file.length() >= 5 ? file.substring(0, 5) : "";
					boolean clipStream = relative.getParent() != null
							&& relative.getParent().equals(Path.of("BDMV", "STREAM")) && file.equals(id + ".m2ts");
					boolean clipInfo = relative.getParent() != null
							&& relative.getParent().equals(Path.of("BDMV", "CLIPINF")) && file.equals(id + ".clpi");
					if (skippedIds.contains(id) && (clipStream || clipInfo)) {
						continue;
					}
					Files.createDirectories(target.getParent());
					if (clipStream) {
						log.info("Copying unchanged clip {}", relative);
					}
					Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
				} else {
					throw new IOException("Unsupported filesystem entry in disc root: " + path);
				}
			}
		}
	}

	private static void copyPair(Path sourceM2ts, Path sourceClpi, Path outputM2ts, Path outputClpi)
			throws IOException {
		Files.createDirectories(outputM2ts.getParent());
		Files.createDirectories(outputClpi.getParent());
		if (!Files.exists(outputM2ts)) {
			Files.copy(sourceM2ts, outputM2ts, StandardCopyOption.COPY_ATTRIBUTES);
		}
		if (!Files.exists(outputClpi)) {
			Files.copy(sourceClpi, outputClpi, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

	private static long treeBytes(Path root) throws IOException {
		try (var paths = Files.walk(root)) {
			long sum = 0;
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				sum = Math.addExact(sum, Files.size(path));
			}
			return sum;
		}
	}

	private static void cleanupWorkspaces(Path root) throws IOException {
		deleteTree(root.resolve(".brts-shrink"));
		Path streams = root.resolve("BDMV/STREAM");
		if (Files.isDirectory(streams)) {
			try (var children = Files.list(streams)) {
				for (Path child : children.filter(p -> p.getFileName().toString().startsWith(".brts-preserve-"))
						.toList()) {
					deleteTree(child);
				}
			}
		}
	}

	private static void deleteTree(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (var paths = Files.walk(path)) {
			for (Path entry : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.delete(entry);
			}
		}
	}

	private record Inventory(List<DiscShrinkPlanner.Playlist> playlists, List<DiscShrinkPlanner.Clip> clips,
			long fixedBytes, Map<String, M2tsShrinkInputResolver.Metadata> metadata,
			Map<String, MoviePlaylist> playlistModels) {
	}

	public record Result(Path input, Path output, long targetBytes, long actualBytes, boolean withinLimit,
			List<String> diagnostics) {
	}
}
