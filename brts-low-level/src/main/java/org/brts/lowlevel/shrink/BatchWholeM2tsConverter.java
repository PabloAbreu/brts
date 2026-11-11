/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/BatchWholeM2tsConverter.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.SourceFingerprint;
import org.brts.common.m2ts.TargetRootWorkspace;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.Extensions;
import org.brts.lowlevel.batch.BatchArgument;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

import lombok.extern.slf4j.Slf4j;

import org.brts.common.video.FrameRate;

/** Engine-independent, metadata-only batch preparation. Execution belongs to the calling layer. */
@Slf4j
public final class BatchWholeM2tsConverter implements WholeM2tsConverter {
	private static final Set<StreamCodingType> SUPPORTED_RETAINED_TYPES = Set.of(StreamCodingType.H264_AVC,
			StreamCodingType.H265_HEVC, StreamCodingType.DOLBY_AC3, StreamCodingType.DOLBY_AC3_PLUS,
			StreamCodingType.DOLBY_TRUEHD, StreamCodingType.DTS, StreamCodingType.DTS_HD,
			StreamCodingType.DTS_HD_MASTER_AUDIO, StreamCodingType.DTS_EXPRESS, StreamCodingType.PRESENTATION_GRAPHICS);

	@FunctionalInterface
	public interface MetadataReader {
		M2tsInfo read(Path source) throws IOException;
	}

	private final Engine engine;
	private final BatchExecutor executor;
	private final MetadataReader metadataReader;

	/** Preparation-only instance. No executable/backend availability is checked here. */
	public BatchWholeM2tsConverter(Engine engine) {
		this(engine, descriptor -> {
			throw new IOException("No batch executor configured");
		});
	}

	public BatchWholeM2tsConverter(Engine engine, BatchExecutor executor) {
		this(engine, executor, new M2tsParser()::parse);
	}

	public BatchWholeM2tsConverter(Engine engine, BatchExecutor executor, MetadataReader metadataReader) {
		this.engine = Objects.requireNonNull(engine);
		this.executor = Objects.requireNonNull(executor);
		this.metadataReader = Objects.requireNonNull(metadataReader);
	}

	@Override
	public Engine engine() {
		return engine;
	}

	@Override
	public M2tsConversionPlan prepare(M2tsConversionRequest request) throws IOException {
		var inventory = Objects.requireNonNull(request.inventory());
		var plan = Objects.requireNonNull(request.shrinkPlan());
		var config = Objects.requireNonNull(request.config());
		if (plan.decision() != M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED) {
			throw new IllegalArgumentException("Only VIDEO_CONVERSION_REQUIRED can execute");
		}
		if (request.clipId() == null || !request.clipId().matches("[0-9]{5}")) {
			throw new IllegalArgumentException("clipId must contain exactly five digits");
		}
		log.debug("Validating request for clip ID {}", request.clipId());
		config.validate(inventory);
		log.debug("Preparing conversion for clip ID {}", request.clipId());
		if (nonempty(config.getAudioDowngrades()) || nonempty(config.getMinimumAudioBitrateKbps())
				|| nonempty(config.getFixedAudioBitrateKbps()) || !plan.plannedAudioDowngrades().isEmpty()) {
			throw new IllegalArgumentException("Audio transformations and bitrate hints are unsupported for execution");
		}
		Path source = inventory.m2tsPath().toRealPath();
		Path sourceClpi = inventory.clpiPath().toRealPath();
		if (!source.getFileName().toString().equals(request.clipId() + ".m2ts")
				|| !sourceClpi.getFileName().toString().equals(request.clipId() + ".clpi")) {
			throw new IllegalArgumentException("Clip ID must match the original M2TS/CLPI pair");
		}
		Path sourceRoot = canonical(
				inventory.m2tsPath().toAbsolutePath().normalize().getParent().getParent().getParent());
		Path outputRoot = canonical(Objects.requireNonNull(request.destinationRoot(), "destinationRoot is required"));
		if (outputRoot.startsWith(sourceRoot) || sourceRoot.startsWith(outputRoot)) {
			throw new IOException("Output/workspace must be separate from the source disc root");
		}
		Path output = canonical(outputRoot.resolve("BDMV/STREAM/" + request.clipId() + ".m2ts"));
		Path clpi = canonical(outputRoot.resolve("BDMV/CLIPINF/" + request.clipId() + ".clpi"));
		if (output.startsWith(sourceRoot) || clpi.startsWith(sourceRoot)) {
			throw new IOException("Output aliases the source disc");
		}
		if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) || Files.exists(clpi, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Destination M2TS/CLPI pair already exists");
		}
		requireTargetPath(outputRoot, output);
		requireTargetPath(outputRoot, clpi);
		Set<Integer> retained = new LinkedHashSet<>();
		var videos = inventory.tracks().stream().filter(t -> t.codingType() != null && t.codingType().isVideo())
				.toList();
		var primaryVideo = videos.stream().filter(t -> t.pid() == 0x1011).findFirst().orElse(null);
		boolean supportedPrimary = primaryVideo != null
				&& Set.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC).contains(primaryVideo.codingType());
		// heuristic for ignoring Dolby Vision companion streams
		// this is simple and does not check actual Dolby Vision metadata
		boolean ignoredDolbyVisionCompanion = videos.size() == 2 && primaryVideo != null
				&& primaryVideo.codingType() == StreamCodingType.H265_HEVC
				&& videos.stream().anyMatch(t -> t.pid() == 0x1015 && t.codingType() == StreamCodingType.H265_HEVC);
		if (!supportedPrimary || (videos.size() != 1 && !ignoredDolbyVisionCompanion)) {
			throw new IllegalArgumentException("Exactly one primary AVC/HEVC video (PID 0x1011) is required");
		}
		retained.add(primaryVideo.pid());
		retained.addAll(plan.retainedAudioPids());
		retained.addAll(plan.retainedSubtitlePids());
		log.debug("Retained PIDs for clip ID {}: {}", request.clipId(), retained);
		for (var track : inventory.tracks()) {
			if (track.codingType() == null || track.codingType().isMenu()) {
				throw new IllegalArgumentException("Unknown/interactive streams are unsupported");
			}
			if (retained.contains(track.pid()) && !SUPPORTED_RETAINED_TYPES.contains(track.codingType())) {
				throw new IllegalArgumentException("Unsupported retained codec: " + track.codingType());
			}
		}
		ExtractionCache.Validated cache = request.extractionCache() == null ? null
				: ExtractionCache.validate(request.extractionCache());
		log.debug("Validated extraction cache for clip ID {}: {}", request.clipId(), cache);
		if (cache != null) {
			validateCacheRequest(cache, request, source, sourceClpi, outputRoot, retained);
		}
		int bitrate = ShrinkBitrateBudget.calculate(inventory, plan, retained);
		log.debug("Calculated bitrate for clip ID {}: {}", request.clipId(), bitrate);
		M2tsInfo info = cache == null ? metadataReader.read(source) : cache.manifest().info();
		if (info.getStreams() == null || info.getStreams().size() != inventory.tracks().size()) {
			throw new IOException("Source metadata changed or contains unsupported streams");
		}
		if (info.getProgramPidMap() != null && info.getProgramPidMap().size() > 1) {
			throw new IOException("Multiple transport programs are unsupported");
		}
		boolean computeSha256 = request.computeSha256();
		String sourceSha256 = computeSha256 ? SourceFingerprint.sha256(source) : null;
		String sourceClpiSha256 = computeSha256 ? SourceFingerprint.sha256(sourceClpi) : null;
		Path defaultWork = outputRoot.resolve(".brts-shrink").resolve(request.clipId());
		if (cache != null) {
			defaultWork = defaultWork.resolve("jobs").resolve(jobKey(request, bitrate));
		}
		Path work = canonical(request.workspace() == null ? defaultWork : request.workspace());
		requireTargetPath(outputRoot, work);
		if (cache != null && (work.startsWith(cache.directory()) || cache.directory().startsWith(work))) {
			throw new IOException("Job workspace must be separate from the immutable extraction cache");
		}
		Path extracted = cache == null ? work.resolve("extracted") : cache.directory();
		Path converted = work.resolve("converted");
		if (!canonical(extracted).equals(extracted) || !canonical(converted).equals(converted)) {
			throw new IOException("Extraction/conversion workspace directories must not be symlink aliases");
		}
		requireTargetPath(outputRoot, extracted);
		requireTargetPath(outputRoot, converted);
		M2tsDescriptor descriptor = new M2tsDescriptor();
		descriptor.setOutputName(request.clipId());
		descriptor.setSourceM2ts(source.toString());
		descriptor.setSourceClpi(sourceClpi.toString());
		descriptor.setSourceM2tsSha256(sourceSha256);
		descriptor.setSourceClpiSha256(sourceClpiSha256);
		descriptor.setTimingManifest(extracted.resolve(M2tsTimingManifest.FILE_NAME).toString());
		descriptor.setExtractionCacheReference(request.extractionCache());
		descriptor.setMaximumOutputBytes(plan.targetBytes());
		List<M2tsDescriptor.StreamEntry> streams = new ArrayList<>();
		Path videoInput = null;
		Path videoOutput = null;
		String sourceFrameRate = null;
		for (int pid : retained) {
			var track = inventory.track(pid);
			var metadata = info.getStreams().stream().filter(s -> s.getPid() == pid).findFirst()
					.orElseThrow(() -> new IOException("Missing source metadata for PID " + pid));
			if (track == null || track.codingType() != metadata.getCodingType()
					|| metadata.getStreamTypeByte() != track.codingType().getCodingTypeByte()) {
				throw new IOException("Source codec changed for PID " + pid);
			}
			String name = String.format("pid_%04x.%s", pid, Extensions.extensionForStream(metadata));
			if (cache != null) {
				name = cache.timing().streams().stream().filter(s -> s.pid() == pid).findFirst().orElseThrow()
						.fileName();
			}
			Path file = extracted.resolve(name);
			if (track.codingType().isVideo()) {
				if (metadata.getFrameRateFps() == null || !Double.isFinite(metadata.getFrameRateFps())
						|| metadata.getFrameRateFps() <= 0 || metadata.getVideoFormat() == null) {
					throw new IOException("Source video frame rate and format are required");
				}
				videoInput = file;
				sourceFrameRate = FrameRate.parse(metadata.getFrameRateFps().toString()).toString();
				videoOutput = converted.resolve(name);
				file = videoOutput;
			}
			var entry = new M2tsDescriptor.StreamEntry();
			entry.setFile(file.toString());
			entry.setPid(pid);
			entry.setStreamTypeByte(metadata.getStreamTypeByte());
			entry.setLanguage(track.language());
			entry.setFrameRateFps(metadata.getFrameRateFps());
			entry.setVideoFormat(metadata.getVideoFormat());
			entry.setChannels(metadata.getChannels());
			entry.setSampleRateHz(metadata.getSampleRateHz());
			entry.setBitrateKbps(metadata.getBitrateKbps());
			streams.add(entry);
		}
		descriptor.setStreams(streams);
		Path mux = work.resolve("m2ts-descriptor.json");
		Path batch = work.resolve("batch-run.json");
		Path referenceFile = work.resolve("cache-reference.json");
		// Do not follow a pre-existing metadata symlink/hardlink into source media or another workspace.
		if (Files.exists(mux, LinkOption.NOFOLLOW_LINKS) || Files.exists(batch, LinkOption.NOFOLLOW_LINKS)
				|| cache != null && Files.exists(referenceFile, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException(
					"Preparation descriptors already exist; execute/resume the existing batch or use a new workspace");
		}
		BatchRunDescriptor job = new BatchRunDescriptor();
		List<BatchStep> steps = new ArrayList<>();
		if (cache != null) {
			job.setPreflightSteps(List.of(step("m2ts-extract", "--input", source, "--output", extracted,
					"--preserve-timing", null, "--cache-reference", referenceFile, "--validate-cache-only", null)));
		} else {
			steps.add(computeSha256
					? step("m2ts-extract", "--input", source, "--output", extracted, "--pids",
							String.join(",", retained.stream().map(String::valueOf).toList()), "--preserve-timing",
							null, "--source-sha256", sourceSha256, "--source-clpi", sourceClpi, "--source-clpi-sha256",
							sourceClpiSha256)
					: step("m2ts-extract", "--input", source, "--output", extracted, "--pids",
							String.join(",", retained.stream().map(String::valueOf).toList()), "--preserve-timing",
							null));
		}
		steps.add(step("video-convert", "--input", videoInput, "--output", videoOutput, "--codec",
				primaryVideo.codingType().name(), "--engine", engine.name(), "--bitrate-kbps", bitrate,
				"--source-frame-rate", sourceFrameRate, "--overwrite", null));
		steps.add(step("m2ts-create", "--descriptor", mux, "--output", output.getParent(), "--clpi", clpi.getParent()));
		job.setSteps(steps);
		// All request/cache/path validation above is read-only, including for nonexistent legacy output roots.
		if (cache != null) {
			ExtractionCache.validate(request.extractionCache());
		}
		Files.createDirectories(outputRoot);
		if (!canonical(outputRoot).equals(outputRoot)) {
			throw new IOException("Output root changed while preparing job workspace: " + outputRoot);
		}
		TargetRootWorkspace.directory(outputRoot, work);
		TargetRootWorkspace.directory(outputRoot, output.getParent());
		TargetRootWorkspace.directory(outputRoot, clpi.getParent());
		// VideoConverter requires an existing output parent. This creates directories, not media.
		TargetRootWorkspace.directory(outputRoot, converted);
		if (cache != null) {
			writeNew(outputRoot, referenceFile, request.extractionCache());
		}
		writeNew(outputRoot, mux, descriptor);
		writeNew(outputRoot, batch, job);
		return new M2tsConversionPlan(engine, plan.targetBytes(), bitrate, batch, mux, output, clpi, work, List.of(
				"Budget reserves planner safety, retained nonvideo packet bytes, 2% fresh mux overhead and 192/184 video expansion.",
				"Final size is authoritative; no automatic retry. tsMuxeR is required at execution; workspace is retained."));
	}

	@Override
	public M2tsConversionResult convert(M2tsConversionRequest request) throws IOException {
		var plan = prepare(request);
		executor.execute(plan.batchDescriptor());
		if (!Files.isRegularFile(plan.outputPath()) || !Files.isRegularFile(plan.outputClpi())) {
			throw new IOException("Batch did not publish the M2TS/CLPI pair");
		}
		long size = Files.size(plan.outputPath());
		if (size <= 0 || size > plan.targetBytes()) {
			throw new IOException("Published output violates target byte limit");
		}
		return new M2tsConversionResult(plan.outputPath(), plan.outputClpi(), size, plan);
	}

	private static boolean nonempty(java.util.Map<?, ?> map) {
		return map != null && !map.isEmpty();
	}

	private static void validateCacheRequest(ExtractionCache.Validated cache, M2tsConversionRequest request,
			Path source, Path clpi, Path outputRoot, Set<Integer> retained) throws IOException {
		var manifest = cache.manifest();
		var inventory = request.inventory();
		if (!manifest.targetRoot().equals(outputRoot.toString()) || !manifest.source().path().equals(source.toString())
				|| !manifest.clpi().path().equals(clpi.toString())
				|| manifest.source().size() != inventory.sourceBytes()
				|| manifest.duration90k() != inventory.duration90k()) {
			throw new IOException("Extraction cache source/CLPI, target root or inventory identity mismatch");
		}
		var metadata = manifest.info().getStreams();
		Set<Integer> inventoryPids = new TreeSet<>();
		for (var track : inventory.tracks()) {
			var stream = metadata.stream().filter(s -> s.getPid() == track.pid()).findFirst().orElse(null);
			if (!inventoryPids.add(track.pid()) || stream == null || stream.getCodingType() != track.codingType()
					|| stream.getStreamTypeByte() != track.codingType().getCodingTypeByte()
					|| !Objects.equals(stream.getLanguage(), track.language())
					|| !Objects.equals(stream.getBitrateKbps(), track.declaredBitrateKbps())
					|| cache.timing().sourcePacketCounts().getOrDefault(track.pid(), 0L) != track.sourcePacketCount()) {
				throw new IOException(
						"Extraction cache metadata/counts disagree with inventory for PID " + track.pid());
			}
		}
		if (metadata.size() != inventoryPids.size()) {
			throw new IOException("Extraction cache metadata differs from inventory tracks");
		}
		Set<Integer> configured = new TreeSet<>();
		configured.add(0x1011);
		for (var track : inventory.tracks()) {
			if (track.codingType().isAudio()
					&& (request.config().getAudioPids() == null
							|| request.config().getAudioPids().contains(track.pid()))
					|| track.codingType().isSubtitle() && (request.config().getSubtitlePids() == null
							|| request.config().getSubtitlePids().contains(track.pid()))) {
				configured.add(track.pid());
			}
		}
		if (!manifest.pids().equals(List.copyOf(new TreeSet<>(retained))) || !configured.equals(retained)) {
			throw new IOException("Extraction cache selected PID coverage must match plan and config exactly");
		}
	}

	private record JobKey(ExtractionCache.Reference reference, long targetBytes, Engine engine, int bitrateKbps,
			boolean computeSha256, List<Integer> audioPids, List<Integer> subtitlePids) {
	}

	private String jobKey(M2tsConversionRequest request, int bitrate) throws IOException {
		var config = request.config();
		var key = new JobKey(request.extractionCache(), request.shrinkPlan().targetBytes(), engine, bitrate,
				request.computeSha256(),
				config.getAudioPids() == null ? null : config.getAudioPids().stream().sorted().toList(),
				config.getSubtitlePids() == null ? null : config.getSubtitlePids().stream().sorted().toList());
		try {
			// Hash only the small pinned job metadata, never media bytes unless explicitly requested above.
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(JsonMapperFactory.get().writeValueAsBytes(key)));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 metadata key unavailable", e);
		}
	}

	/** Validate without creating even the target root; use its nearest existing ancestor for filesystem checks. */
	private static void requireTargetPath(Path root, Path path) throws IOException {
		if (!canonical(path).startsWith(root)) {
			throw new IOException("Workspace path must remain inside target root " + root + ": " + path);
		}
		Path anchor = root;
		while (!Files.exists(anchor, LinkOption.NOFOLLOW_LINKS)) {
			anchor = anchor.getParent();
		}
		TargetRootWorkspace.requireInside(anchor, path);
	}

	private static void writeNew(Path root, Path path, Object value) throws IOException {
		TargetRootWorkspace.requireInside(root, path);
		try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValue(out, value);
		}
	}

	private static BatchStep step(String command, Object... arguments) {
		List<BatchArgument> args = new ArrayList<>();
		for (int i = 0; i < arguments.length; i += 2) {
			args.add(new BatchArgument(arguments[i].toString(),
					arguments[i + 1] == null ? null : arguments[i + 1].toString()));
		}
		return BatchStep.invocation("low", command, args);
	}

	/** Resolve existing ancestors too, so a not-yet-created destination cannot hide a symlink alias. */
	private static Path canonical(Path path) throws IOException {
		return TargetRootWorkspace.canonical(path);
	}
}
