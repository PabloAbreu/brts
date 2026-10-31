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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.SourceFingerprint;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.Extensions;
import org.brts.lowlevel.batch.BatchArgument;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.FrameRate;

/** Engine-independent, metadata-only batch preparation. Execution belongs to the calling layer. */
public final class BatchWholeM2tsConverter implements WholeM2tsConverter {
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
		config.validate(inventory);
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
		Path work = canonical(request.workspace() == null ? outputRoot.resolve(".brts-shrink").resolve(request.clipId())
				: request.workspace());
		if (outputRoot.startsWith(sourceRoot) || sourceRoot.startsWith(outputRoot) || work.startsWith(sourceRoot)) {
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
		for (var track : inventory.tracks()) {
			if (track.codingType() == null || track.codingType().isMenu()) {
				throw new IllegalArgumentException("Unknown/interactive streams are unsupported");
			}
			if (retained.contains(track.pid()) && !Set.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC,
					StreamCodingType.DOLBY_AC3, StreamCodingType.DOLBY_AC3_PLUS, StreamCodingType.DTS,
					StreamCodingType.PRESENTATION_GRAPHICS).contains(track.codingType())) {
				throw new IllegalArgumentException("Unsupported retained codec: " + track.codingType());
			}
		}
		int bitrate = ShrinkBitrateBudget.calculate(inventory, plan, retained);
		M2tsInfo info = metadataReader.read(source);
		if (info.getStreams() == null || info.getStreams().size() != inventory.tracks().size()) {
			throw new IOException("Source metadata changed or contains unsupported streams");
		}
		if (info.getProgramPidMap() != null && info.getProgramPidMap().size() > 1) {
			throw new IOException("Multiple transport programs are unsupported");
		}
		String sourceSha256 = SourceFingerprint.sha256(source);
		String sourceClpiSha256 = SourceFingerprint.sha256(sourceClpi);
		Path extracted = work.resolve("extracted");
		Path converted = work.resolve("converted");
		if (!canonical(extracted).equals(extracted) || !canonical(converted).equals(converted)) {
			throw new IOException("Extraction/conversion workspace directories must not be symlink aliases");
		}
		M2tsDescriptor descriptor = new M2tsDescriptor();
		descriptor.setOutputName(request.clipId());
		descriptor.setSourceM2ts(source.toString());
		descriptor.setSourceClpi(sourceClpi.toString());
		descriptor.setSourceM2tsSha256(sourceSha256);
		descriptor.setSourceClpiSha256(sourceClpiSha256);
		descriptor.setTimingManifest(extracted.resolve("timing.json").toString());
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
		// Do not follow a pre-existing metadata symlink/hardlink into source media or another workspace.
		if (Files.exists(mux, LinkOption.NOFOLLOW_LINKS) || Files.exists(batch, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException(
					"Preparation descriptors already exist; execute/resume the existing batch or use a new workspace");
		}
		BatchRunDescriptor job = new BatchRunDescriptor();
		job.setSteps(List.of(step("m2ts-extract", "--input", source, "--output", extracted, "--pids",
				String.join(",", retained.stream().map(String::valueOf).toList()), "--preserve-timing", null,
				"--source-sha256", sourceSha256, "--source-clpi", sourceClpi, "--source-clpi-sha256", sourceClpiSha256),
				step("video-convert", "--input", videoInput, "--output", videoOutput, "--codec",
						primaryVideo.codingType().name(), "--engine", engine.name(), "--bitrate-kbps", bitrate,
						"--source-frame-rate", sourceFrameRate, "--overwrite", null),
				step("m2ts-create", "--descriptor", mux, "--output", output.getParent(), "--clpi", clpi.getParent())));
		// VideoConverter requires an existing output parent. This creates directories, not media.
		Files.createDirectories(converted);
		JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValue(mux.toFile(), descriptor);
		JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValue(batch.toFile(), job);
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
		Path absolute = path.toAbsolutePath().normalize();
		if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
			return absolute.toRealPath();
		}
		return canonical(absolute.getParent()).resolve(absolute.getFileName());
	}
}
