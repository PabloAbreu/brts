/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/PreservingM2tsClipWriter.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import static org.brts.lowlevel.m2ts.PreservationTransportNormalizer.require;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.SourceFingerprint;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.common.utils.TsMuxerUtils;
import org.brts.lowlevel.clpi.ClpiRegenOptions;
import org.brts.lowlevel.clpi.M2tsClpiRegenBuilder;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.common.video.FrameRate;
import org.brts.lowlevel.writer.ClipInfoWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * Opt-in, fail-closed tsMuxeR preservation path. Source ES is re-extracted to validate the manifest and copied tracks.
 * Every attempt is retained; only a validated normalized M2TS and regenerated CLPI are published. Supports one AVC/HEVC
 * video and AC3/EAC3/DTS/PGS. AC3 uses syncframe/sample clocks independently of PES boundaries. EAC3/DTS retain strict
 * PES-grid checks (repacketization is unsupported). Video requires one AUD-delimited single-slice picture per
 * timestamped PES; unproven layouts fail closed. This writer checks source video before mux, not before encoding in an
 * upstream batch. Clock discontinuities, changed presentation grids and copied-track timing changes are rejected.
 */
@Slf4j
public final class PreservingM2tsClipWriter implements M2tsClipWriter {

	@Override
	public void write(M2tsDescriptor descriptor, Path m2tsPath, Path clipPath) throws IOException {
		validateFields(descriptor);
		Path source = absoluteFile(descriptor.getSourceM2ts());
		Path sourceClpi = absoluteFile(descriptor.getSourceClpi());
		Path manifestPath = absoluteFile(descriptor.getTimingManifest());
		Path target = m2tsPath.toAbsolutePath().normalize();
		Path targetClpi = clipPath.toAbsolutePath().normalize();
		require(!target.equals(targetClpi), "M2TS and CLPI destinations must differ");
		require(!target.equals(source) && !targetClpi.equals(sourceClpi), "Cannot replace source files");
		require(source.getFileName().toString().equals(descriptor.getOutputName() + ".m2ts")
				&& sourceClpi.getFileName().toString().equals(descriptor.getOutputName() + ".clpi"),
				"Output clip ID must match the source pair");
		Files.createDirectories(target.getParent());
		Path root = target.getParent().resolve(".brts-preserve-" + descriptor.getOutputName());
		Files.createDirectories(root);
		try (FileChannel channel = FileChannel.open(root.resolve("writer.lock"), StandardOpenOption.CREATE,
				StandardOpenOption.WRITE); FileLock lock = channel.tryLock()) {
			require(lock != null, "Another preservation writer owns " + root);
			PreservationPublication.recover(root, target, targetClpi);
			PreservationPublication.absent(target);
			PreservationPublication.absent(targetClpi);
			Path stage = Files.createTempDirectory(root, "attempt-");
			try {
				preserve(descriptor, source, sourceClpi, manifestPath, target, targetClpi, root, stage);
			} catch (IOException | RuntimeException e) {
				throw new IOException(
						"M2TS preservation failed; artifacts retained at " + stage + ": " + e.getMessage(), e);
			}
		}
	}

	private void preserve(M2tsDescriptor descriptor, Path source, Path sourceClpi, Path manifestPath, Path target,
			Path targetClpi, Path root, Path stage) throws IOException {
		// Resolve the required backend before expensive validation. Never invoke the fallback factory.
		String binary = TsMuxerUtils.resolveTsMuxeRBinary();
		M2tsInfo sourceInfo = new M2tsParser().parse(source);
		ClipInfo originalClpi = new ClipInfoParser().parse(sourceClpi);
		require(originalClpi.getApplicationType() == ClipInfo.APPLICATION_TYPE_MOVIE && !originalClpi.isAtcDelta(),
				"Only movie clips without ATC deltas are supported");
		M2tsTimingManifest manifest = M2tsTiming.read(manifestPath);
		SourceFingerprint.verify(source, descriptor.getSourceM2tsSha256());
		SourceFingerprint.verify(sourceClpi, descriptor.getSourceClpiSha256());
		if (descriptor.getSourceM2tsSha256() != null)
			require(descriptor.getSourceM2tsSha256().equalsIgnoreCase(manifest.sourceSha256()),
					"Timing manifest does not match the prepared source fingerprint");
		require(Path.of(manifest.sourcePath()).toRealPath().equals(source)
				&& manifest.sourceSize() == Files.size(source), "Timing manifest does not identify source M2TS");
		Map<Integer, StreamTiming> timings = index(manifest);
		Set<Integer> retained = new HashSet<>();
		M2tsDescriptor.StreamEntry video = null;
		for (var stream : descriptor.getStreams()) {
			require(retained.add(stream.getPid()), "Duplicate descriptor PID " + stream.getPid());
			StreamCodingType type = StreamCodingType.fromByte(stream.getStreamTypeByte(), false);
			require(type != null && Set.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC,
					StreamCodingType.DOLBY_AC3, StreamCodingType.DOLBY_AC3_PLUS, StreamCodingType.DTS,
					StreamCodingType.PRESENTATION_GRAPHICS).contains(type),
					"Unsupported retained stream codec " + type);
			var original = sourceInfo.getStreams().stream().filter(s -> s.getPid() == stream.getPid()).findFirst()
					.orElseThrow(() -> new IOException("Requested PID absent from source: " + stream.getPid()));
			require(original.getStreamTypeByte() == stream.getStreamTypeByte(), "Source codec must not change");
			ClipStream clpiStream = originalClpi.getStreams().stream().filter(s -> s.getPid() == stream.getPid())
					.findFirst().orElseThrow(() -> new IOException("Requested PID absent from source CLPI"));
			require(clpiStream.getCodingType() == type, "Source CLPI codec disagrees with M2TS");
			StreamTiming timing = timings.get(stream.getPid());
			require(timing != null && timing.codingType() == type, "Missing/mismatched original stream timing");
			validateTiming(timing);
			absoluteFile(stream.getFile());
			if (type.isVideo()) {
				require(video == null, "Exactly one video is supported");
				video = stream;
				require(stream.getFrameRateFps() != null && stream.getFrameRateFps() > 0
						&& stream.getVideoFormat() != null, "Video frame rate and format are required");
			}
		}
		require(video != null, "One AVC/HEVC video is required");
		validateSourceVideo(sourceInfo, video);
		Path originalExtraction = stage.resolve("source-check");
		M2tsTimingManifest actualSource = extract(source, sourceInfo, originalExtraction, retained);
		Map<Integer, StreamTiming> actualTimings = index(actualSource);
		for (int pid : retained)
			require(timings.get(pid).equals(actualTimings.get(pid)),
					"Source timing manifest is stale or differs from re-extraction for PID " + pid);
		long origin = timings.get(video.getPid()).firstPts90k();
		require(origin >= 0, "Negative source video origin is unsupported");
		var sourceGeometry = PreservationVideoGeometry.read(source, video.getPid(), video.getStreamTypeByte());
		PreservationVideoGeometry.validateStableSequence(
				originalExtraction.resolve(timings.get(video.getPid()).fileName()), video.getStreamTypeByte());
		PreservationVideoTimeline.validate(originalExtraction.resolve(timings.get(video.getPid()).fileName()),
				timings.get(video.getPid()));
		PreservationVideoGeometry.validateStableSequence(absoluteFile(video.getFile()), video.getStreamTypeByte());
		List<Path> muxInputs = new ArrayList<>();
		for (var stream : descriptor.getStreams()) {
			Path input = absoluteFile(stream.getFile());
			StreamTiming timing = timings.get(stream.getPid());
			Path originalEs = originalExtraction.resolve(timing.fileName());
			if (timing.codingType() == StreamCodingType.DOLBY_AC3)
				PreservationAc3Timeline.validate(originalEs, timing);
			if (!timing.codingType().isVideo())
				require(Files.mismatch(input, originalEs) == -1,
						"Copied stream bytes differ from source extraction for PID " + stream.getPid());
			if (timing.codingType() == StreamCodingType.PRESENTATION_GRAPHICS) {
				Path sup = stage.resolve("pid_" + stream.getPid() + ".sup");
				normalizeSup(input, sup, timing, origin);
				muxInputs.add(sup);
			} else
				muxInputs.add(input);
		}
		Path muxDirectory = stage.resolve("mux");
		Files.createDirectories(muxDirectory);
		Path meta = stage.resolve("preservation.meta");
		Files.writeString(meta, meta(descriptor, muxInputs, timings, origin, sourceInfo.getPcrPid() == video.getPid()));
		run(binary, meta, muxDirectory, stage.resolve("tsmuxer.log"));
		Path generated = muxDirectory.resolve("BDMV/STREAM/00000.m2ts");
		require(Files.isRegularFile(generated) && Files.size(generated) > 0, "tsMuxeR did not produce M2TS");
		M2tsInfo generatedInfo = new M2tsParser().parse(generated);
		Map<Integer, Integer> mapping = PreservationTransportNormalizer.mapping(generatedInfo, sourceInfo,
				descriptor.getStreams());
		M2tsTimingManifest generatedTiming = extract(generated, generatedInfo, stage.resolve("mux-check"), null);
		Map<Integer, StreamTiming> generatedTimings = index(generatedTiming);
		Map<Integer, Long> shifts = new HashMap<>();
		long videoShift = 0;
		Set<Integer> generatedPids = new HashSet<>();
		for (int i = 0; i < descriptor.getStreams().size(); i++) {
			var entry = descriptor.getStreams().get(i);
			int generatedPid = generatedInfo.getStreams().get(i).getPid();
			generatedPids.add(generatedPid);
			StreamTiming original = timings.get(entry.getPid());
			StreamTiming muxed = generatedTimings.get(generatedPid);
			validateTiming(muxed);
			long shift = Math.subtractExact(original.firstPts90k(), muxed.firstPts90k());
			shifts.put(generatedPid, shift);
			if (entry == video)
				videoShift = shift;
			// A constant shift cannot repair dropped samples, changed gaps or presentation cadence.
			validatePresentation(original, muxed, shift, originalExtraction.resolve(original.fileName()),
					stage.resolve("mux-check").resolve(muxed.fileName()));
		}
		for (long shift : shifts.values())
			require(Math.abs(Math.subtractExact(shift, videoShift)) <= 90,
					"Mux did not honor source-relative scheduling; more than 1 ms of per-track repair is unsafe");
		Path normalized = stage.resolve("normalized.m2ts");
		new PreservationTransportNormalizer(mapping, shifts, videoShift, generatedInfo.getPmtPid(),
				generatedInfo.getPcrPid(), generatedPids).normalize(generated, normalized);
		validateSize(normalized, descriptor.getMaximumOutputBytes());
		M2tsInfo finalInfo = new M2tsParser().parse(normalized);
		// trace geometries
		log.debug("Source geometry: {}", sourceGeometry);
		var finalGeometry = PreservationVideoGeometry.read(normalized, video.getPid(), video.getStreamTypeByte());
		log.debug("Final geometry: {}", finalGeometry);
		require(sourceGeometry.equals(finalGeometry),
				"Video dimensions, pixel aspect, scan signaling or color signaling changed");
		require(finalInfo.getStreams().stream().map(s -> s.getPid()).collect(Collectors.toSet()).equals(retained),
				"Final retained PID set differs");
		M2tsTimingManifest finalTiming = extract(normalized, finalInfo, stage.resolve("final-check"), retained);
		Map<Integer, StreamTiming> finalTimings = index(finalTiming);
		PreservationVideoGeometry.validateStableSequence(
				stage.resolve("final-check").resolve(finalTimings.get(video.getPid()).fileName()),
				video.getStreamTypeByte());
		for (var entry : descriptor.getStreams()) {
			StreamTiming original = timings.get(entry.getPid());
			StreamTiming result = finalTimings.get(entry.getPid());
			require(Objects.equals(original.firstPts90k(), result.firstPts90k()), "Final presentation origin changed");
			validatePresentation(original, result, 0, originalExtraction.resolve(original.fileName()),
					stage.resolve("final-check").resolve(result.fileName()));
			if (!original.codingType().isVideo())
				require(Files.mismatch(originalExtraction.resolve(original.fileName()),
						stage.resolve("final-check").resolve(result.fileName())) == -1,
						"Final copied ES/SUP bytes or PGS timestamps changed for PID " + entry.getPid());
		}
		// PMT registration need not repeat display aspect. Geometry and frame-grid checks above
		// establish that these original CLPI values still describe the normalized video.
		var regenOptions = new ClpiRegenOptions();
		int videoPid = video.getPid();
		var originalVideo = originalClpi.getStreams().stream().filter(s -> s.getPid() == videoPid).findFirst()
				.orElseThrow(() -> new IOException("Source CLPI has no video metadata"));
		var videoMetadata = new ClpiRegenOptions.VideoOverrides();
		videoMetadata.setVideoFormat(originalVideo.getVideoFormat());
		videoMetadata.setFrameRate(originalVideo.getFrameRate());
		videoMetadata.setAspectRatio(originalVideo.getAspectRatio());
		regenOptions.getVideoStreams().put(video.getPid(), videoMetadata);
		// Copied elementary bytes have already been proved identical. CLPI language/layout
		// are authoritative when the transport omits their optional PMT descriptors.
		for (var stream : originalClpi.getStreams()) {
			if (!retained.contains(stream.getPid()))
				continue;
			if (stream.getCodingType().isAudio()) {
				var metadata = new ClpiRegenOptions.AudioOverrides();
				metadata.setLanguage(stream.getLanguage());
				metadata.setAudioChannelLayout(stream.getAudioChannelLayout());
				metadata.setSampleRate(stream.getSampleRate());
				regenOptions.getAudioStreams().put(stream.getPid(), metadata);
			} else if (stream.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS) {
				var metadata = new ClpiRegenOptions.SubtitleOverrides();
				metadata.setLanguage(stream.getLanguage());
				regenOptions.getSubtitleStreams().put(stream.getPid(), metadata);
			}
		}
		// Independently regenerate the SOURCE too: comparing a native CLPI directly with
		// BRTS's last-picture-PTS convention incorrectly rejects an exclusive native end.
		// Never copy source packet counts or entry points into the new transport.
		ClipInfo sourceScan = new M2tsClpiRegenBuilder().build(source, descriptor.getOutputName(),
				sourceRegenOptions(sourceInfo, video, regenOptions));
		validateSourceBounds(originalClpi, sourceScan, timings.get(video.getPid()));
		ClipInfo clip = new M2tsClpiRegenBuilder().build(normalized, descriptor.getOutputName(), regenOptions);
		require(Objects.equals(sourceScan.getTsRecordingStartPts(), clip.getTsRecordingStartPts())
				&& Objects.equals(sourceScan.getTsRecordingEndPts(), clip.getTsRecordingEndPts()),
				"Regenerated output/source media presentation bounds differ");
		// All output access-unit PTS were independently proved identical above. Preserve
		// the validated playlist contract, not the regenerator's duration convention.
		// These fields are also the model's SequenceInfo presentation_start/end_time;
		// there is no separate SequenceInfo bounds object to update.
		clip.setTsRecordingStartPts(originalClpi.getTsRecordingStartPts());
		clip.setTsRecordingEndPts(originalClpi.getTsRecordingEndPts());
		clip.setDuration(originalClpi.getDuration());
		validateClip(originalClpi, clip, retained);
		Path normalizedClpi = stage.resolve("normalized.clpi");
		try (OutputStream out = Files.newOutputStream(normalizedClpi)) {
			new ClipInfoWriter().write(clip, out);
		}
		ClipInfo reread = new ClipInfoParser().parse(normalizedClpi);
		validateClip(originalClpi, reread, retained);
		// Stage data must reach storage before any public directory entries are created.
		force(normalized);
		force(normalizedClpi);
		SourceFingerprint.verify(source, descriptor.getSourceM2tsSha256());
		SourceFingerprint.verify(sourceClpi, descriptor.getSourceClpiSha256());
		PreservationPublication.publish(root, normalized, normalizedClpi, target, targetClpi);
	}

	/** Source-only comparison options; the accepted companion is not retained or published. */
	static ClpiRegenOptions sourceRegenOptions(M2tsInfo sourceInfo, M2tsDescriptor.StreamEntry video,
			ClpiRegenOptions outputOptions) throws IOException {
		validateSourceVideo(sourceInfo, video);
		var options = new ClpiRegenOptions();
		options.setProbeMode(outputOptions.getProbeMode());
		options.setVideoStreams(new HashMap<>(outputOptions.getVideoStreams()));
		options.setAudioStreams(new HashMap<>(outputOptions.getAudioStreams()));
		options.setSubtitleStreams(new HashMap<>(outputOptions.getSubtitleStreams()));
		for (var stream : sourceInfo.getStreams()) {
			if (stream.getCodingType() != null && stream.getCodingType().isVideo() && stream.getPid() != video.getPid())
				options.getExcludedStreamPids().add(stream.getPid());
		}
		return options;
	}

	static void validateSourceVideo(M2tsInfo sourceInfo, M2tsDescriptor.StreamEntry video) throws IOException {
		var videos = sourceInfo.getStreams().stream()
				.filter(s -> s.getCodingType() != null && s.getCodingType().isVideo()).toList();
		// Match batch preparation's PID/codec heuristic; actual Dolby Vision metadata is not checked.
		// Only retain the primary video, never the companion.
		boolean ignoredDolbyVisionCompanion = videos.size() == 2 && video.getPid() == 0x1011
				&& video.getStreamTypeByte() == StreamCodingType.H265_HEVC.getCodingTypeByte()
				&& videos.stream()
						.anyMatch(s -> s.getPid() == 0x1011 && s.getCodingType() == StreamCodingType.H265_HEVC)
				&& videos.stream()
						.anyMatch(s -> s.getPid() == 0x1015 && s.getCodingType() == StreamCodingType.H265_HEVC);
		require(videos.size() == 1 || ignoredDolbyVisionCompanion, "Secondary/multiview video is unsupported");
	}

	private static void validateFields(M2tsDescriptor descriptor) throws IOException {
		require(descriptor != null && descriptor.getOutputName() != null
				&& descriptor.getOutputName().matches("[0-9]{5}"), "Preservation requires a five-digit outputName");
		require(descriptor.getSourceM2ts() != null && descriptor.getSourceClpi() != null
				&& descriptor.getTimingManifest() != null && descriptor.getMaximumOutputBytes() != null
				&& descriptor.getMaximumOutputBytes() > 0,
				"sourceM2ts, sourceClpi, timingManifest and positive maximumOutputBytes are required together");
		require(descriptor.getStreams() != null && !descriptor.getStreams().isEmpty(), "Streams are required");
		require(descriptor.getInitialPtsOffsetTicks() == null && descriptor.getMinEndPtsTicks() == null
				&& descriptor.getTargetBitrateKbps() == null
				&& (descriptor.getChapters() == null || descriptor.getChapters().isEmpty()),
				"Preservation does not permit timeline, bitrate-stuffing or chapter overrides");
	}

	private static Path absoluteFile(String value) throws IOException {
		require(value != null && Path.of(value).isAbsolute(), "Preservation paths must be absolute: " + value);
		Path path = Path.of(value).toRealPath();
		require(Files.isRegularFile(path) && Files.size(path) > 0, "Missing/empty preservation input: " + value);
		return path;
	}

	private static Map<Integer, StreamTiming> index(M2tsTimingManifest manifest) throws IOException {
		Map<Integer, StreamTiming> result = new HashMap<>();
		for (var timing : manifest.streams()) {
			require(timing != null && result.putIfAbsent(timing.pid(), timing) == null, "Duplicate timing PID");
		}
		return result;
	}

	private static M2tsTimingManifest extract(Path input, M2tsInfo info, Path dir, Set<Integer> pids)
			throws IOException {
		new M2tsExtractor().extract(input, info, dir, pids, true);
		return M2tsTiming.read(dir.resolve(M2tsTimingManifest.FILE_NAME));
	}

	static void validateTiming(StreamTiming timing) throws IOException {
		require(timing != null && timing.firstPts90k() != null && timing.pesTimings() != null
				&& !timing.pesTimings().isEmpty() && timing.segments() != null,
				"Incomplete original/generated stream timing");
		Long previous = null;
		for (var pes : timing.pesTimings()) {
			require(pes.pts90k() != null, "Timestamp-less PES cannot establish preservation");
			Long decode = pes.dts90k() != null ? pes.dts90k() : pes.pts90k();
			require(pes.dts90k() == null || pes.dts90k() <= pes.pts90k(), "DTS exceeds PTS");
			// PTS reorder without DTS is not safe for re-encoding; nonvideo clocks must also be monotonic.
			long maximumGap = timing.codingType() == StreamCodingType.PRESENTATION_GRAPHICS ? (1L << 32) - 1 : 900_000;
			if (previous != null)
				require(decode >= previous && decode - previous <= maximumGap,
						"Discontinuous/ambiguous decode timeline; preservation unsupported");
			previous = decode;
		}
	}

	static void validatePresentation(StreamTiming source, StreamTiming generated, long shift, Path sourceEs,
			Path generatedEs) throws IOException {
		require(source.codingType() == generated.codingType(), "Presentation codec changed");
		if (source.codingType() == StreamCodingType.DOLBY_AC3) {
			PreservationAc3Timeline.compare(sourceEs, source, generatedEs, generated, shift);
			return;
		}
		if (source.codingType().isVideo()) {
			PreservationVideoTimeline.validate(sourceEs, source);
			PreservationVideoTimeline.validate(generatedEs, generated);
		}
		validatePresentation(source, generated, shift, source.codingType().isVideo());
	}

	static void validatePresentation(StreamTiming source, StreamTiming generated, long shift, boolean video)
			throws IOException {
		List<Long> originalPts = source.pesTimings().stream().map(p -> p.pts90k()).sorted().toList();
		List<Long> generatedPts = generated.pesTimings().stream().map(p -> Math.addExact(p.pts90k(), shift)).sorted()
				.toList();
		require(originalPts.equals(generatedPts), "PES presentation grid changed for PID " + source.pid()
				+ "; constant-shift preservation cannot be proved");
		if (!video) {
			List<Long> originalDts = source.pesTimings().stream().map(p -> p.dts90k()).toList();
			List<Long> generatedDts = generated.pesTimings().stream()
					.map(p -> p.dts90k() == null ? null : Math.addExact(p.dts90k(), shift)).toList();
			require(originalDts.equals(generatedDts), "Copied PES DTS timeline changed");
			require(Objects.equals(source.firstDts90k(),
					generated.firstDts90k() == null ? null : Math.addExact(generated.firstDts90k(), shift)),
					"Copied stream DTS offset changed");
			require(source.segments().size() == generated.segments().size(), "PGS segment count changed");
			for (int i = 0; i < source.segments().size(); i++) {
				var a = source.segments().get(i);
				var b = generated.segments().get(i);
				require(a.type() == b.type() && a.size() == b.size() && a.pts90k() == Math.addExact(b.pts90k(), shift)
						&& Objects.equals(a.dts90k(), b.dts90k() == null ? null : Math.addExact(b.dts90k(), shift)),
						"PGS segment timestamp/shape changed");
			}
		}
	}

	/** SUP input carries absolute source clocks. tsMuxeR input must instead be relative to video origin. */
	static void normalizeSup(Path input, Path output, StreamTiming timing, long videoOrigin) throws IOException {
		require(!timing.segments().isEmpty(), "PGS lacks timed SUP segments");
		try (DataInputStream in = new DataInputStream(Files.newInputStream(input));
				OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
			long offset = 0;
			for (var segment : timing.segments()) {
				require(segment.outputOffset() == offset && in.readUnsignedShort() == 0x5047,
						"Invalid SUP record offset/header");
				long pts = Integer.toUnsignedLong(in.readInt());
				long dts = Integer.toUnsignedLong(in.readInt());
				int type = in.readUnsignedByte();
				int size = in.readUnsignedShort();
				require(pts == (segment.pts90k() & 0xffffffffL)
						&& dts == (segment.dts90k() == null ? 0 : segment.dts90k() & 0xffffffffL)
						&& type == segment.type() && size == segment.size(),
						"SUP differs from original timing manifest");
				long relativePts = Math.subtractExact(segment.pts90k(), videoOrigin);
				Long relativeDts = segment.dts90k() == null ? null : Math.subtractExact(segment.dts90k(), videoOrigin);
				require(relativePts >= 0 && relativePts < (1L << 32)
						&& (relativeDts == null || relativeDts >= 0 && relativeDts < (1L << 32)),
						"PGS before video origin or beyond SUP timebase cannot be muxed safely");
				byte[] data = in.readNBytes(size);
				require(data.length == size, "Truncated SUP segment");
				M2tsTiming.writeSupSegment(out, relativePts, relativeDts, type, data, 0, size);
				offset += 13L + size;
			}
			require(in.read() == -1, "SUP contains unmanifested records");
		}
	}

	static String meta(M2tsDescriptor descriptor, List<Path> inputs, Map<Integer, StreamTiming> timings,
			long videoOrigin, boolean pcrOnVideo) throws IOException {
		boolean hevc = descriptor.getStreams().stream().anyMatch(s -> s.getStreamTypeByte() == 0x24);
		StringBuilder result = new StringBuilder("MUXOPT ");
		result.append(pcrOnVideo ? "--pcr-on-video-pid " : "--no-pcr-on-video-pid ");
		result.append(hevc ? "--blu-ray-v3 " : "--blu-ray ");
		result.append("--new-audio-pes --vbr --vbv-len=500 --start-time=")
				.append(Math.floorMod(videoOrigin, 1L << 33) / 2).append('\n');
		for (int i = 0; i < inputs.size(); i++) {
			var stream = descriptor.getStreams().get(i);
			String path = inputs.get(i).toString();
			require(!path.contains("\"") && !path.contains("\n") && !path.contains("\r"), "Unsafe tsMuxeR meta path");
			String codec = switch (stream.getStreamTypeByte()) {
			case 0x1b -> "V_MPEG4/ISO/AVC";
			case 0x24 -> "V_MPEGH/ISO/HEVC";
			case 0x81, 0x84 -> "A_AC3";
			case 0x82 -> "A_DTS";
			case 0x90 -> "S_HDMV/PGS";
			default -> throw new IOException("Unsupported preservation codec");
			};
			String language = stream.getLanguage() == null ? "und" : stream.getLanguage();
			require(language.matches("[a-zA-Z]{3}"), "Invalid stream language");
			result.append(codec).append(", \"").append(path).append("\", lang=").append(language);
			if (stream.getStreamTypeByte() == 0x1b || stream.getStreamTypeByte() == 0x24)
				result.append(", fps=").append(stream.getFrameRateFps());
			else if (stream.getStreamTypeByte() != 0x90) {
				// tsMuxeR docs/USAGE.md specifies signed millisecond audio timeshift. Restore only
				// the <1 ms remainder after mux, and reject versions that ignore/reinterpret this option.
				long delay = Math.subtractExact(timings.get(stream.getPid()).firstPts90k(), videoOrigin);
				result.append(", timeshift=").append(delay / 90).append("ms");
			}
			result.append('\n');
		}
		return result.toString();
	}

	static void run(String binary, Path meta, Path output, Path log) throws IOException {
		Process process = new ProcessBuilder(binary, meta.toString(), output.toString()).redirectErrorStream(true)
				.redirectOutput(log.toFile()).start();
		try {
			int exit = process.waitFor();
			require(exit == 0, "tsMuxeR exited " + exit + "; see " + log);
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new IOException("tsMuxeR interrupted; stage retained at " + output, e);
		}
	}

	private static void validateSourceBounds(ClipInfo original, ClipInfo scan, StreamTiming video) throws IOException {
		require(original.getTsRecordingStartPts() != null && original.getTsRecordingEndPts() != null,
				"Source CLPI lacks presentation bounds");
		List<Long> pts = video.pesTimings().stream().map(p -> p.pts90k()).sorted().toList();
		long first = pts.getFirst();
		long last = pts.getLast();
		require(scan.getTsRecordingStartPts() != null && scan.getTsRecordingEndPts() != null
				&& scan.getTsRecordingStartPts().getTicks() == first && scan.getTsRecordingEndPts().getTicks() == last,
				"Source CLPI regeneration disagrees with validated video access-unit grid");
		long start = original.getTsRecordingStartPts().getTicks();
		long end = original.getTsRecordingEndPts().getTicks();
		// CLPI SequenceInfo is 45 kHz, so round trips discard the low 90 kHz bit.
		require(start == first / 2 * 2 && end >= start && (end & 1) == 0,
				"Source CLPI presentation start/range is incompatible with source media");
		if (end == last / 2 * 2)
			return; // BRTS-authored inclusive last-picture convention
		var metadata = original.getStreams().stream().filter(s -> s.getPid() == video.pid()).findFirst()
				.orElseThrow(() -> new IOException("Source CLPI lacks video frame rate"));
		require(metadata.getFrameRate() != null, "Source CLPI lacks video frame rate");
		FrameRate rate = FrameRate.fromCode(metadata.getFrameRate());
		// case block removed as FrameRate.fromCode handles it
		long periodNumerator = 90_000L * rate.denominator();
		long floorFrame = periodNumerator / rate.numerator();
		long ceilFrame = (periodNumerator + rate.numerator() - 1) / rate.numerator();
		// Prove the stated sample duration against every observed presentation interval,
		// rather than treating one frame as an arbitrary widened bounds tolerance.
		for (int i = 1; i < pts.size(); i++) {
			long interval = pts.get(i) - pts.get(i - 1);
			require(interval == floorFrame || interval == ceilFrame,
					"Source presentation cadence does not prove CLPI exclusive-end sample duration");
		}
		require(pts.size() > 1
				&& (end == Math.addExact(last, floorFrame) / 2 * 2 || end == Math.addExact(last, ceilFrame) / 2 * 2),
				"Source CLPI end is not the last picture or its proven exclusive sample end; "
						+ "unproven presentation padding is unsupported (end=" + end + ", last PTS=" + last + ")");
	}

	static void validateClip(ClipInfo original, ClipInfo output, Set<Integer> retained) throws IOException {
		require(output.getStreams() != null && output.getStreams().size() == retained.size(),
				"CLPI stream count differs: expected=" + retained.size() + ", output="
						+ (output.getStreams() == null ? "null" : output.getStreams().size()) + ", retainedPids="
						+ retained + ", sourceStreams=" + clipStreamsDiagnostic(original) + ", outputStreams="
						+ clipStreamsDiagnostic(output));
		for (ClipStream stream : output.getStreams()) {
			ClipStream source = original.getStreams().stream().filter(s -> s.getPid() == stream.getPid()).findFirst()
					.orElseThrow(() -> new IOException("M2TS preservation: Regenerated CLPI has unknown PID "
							+ stream.getPid() + ": outputStream=" + clipStreamDiagnostic(stream) + ", sourceStreams="
							+ clipStreamsDiagnostic(original) + ", retainedPids=" + retained));
			require(retained.contains(stream.getPid()) && source.getCodingType() == stream.getCodingType(),
					"CLPI codec/PID changed: retained=" + retained.contains(stream.getPid()) + ", retainedPids="
							+ retained + ", sourceStream=" + clipStreamDiagnostic(source) + ", outputStream="
							+ clipStreamDiagnostic(stream));
			List<String> differences = new ArrayList<>();
			if (stream.getCodingType().isVideo()) {
				clipFieldDifference(differences, "videoFormat", source.getVideoFormat(), stream.getVideoFormat(), true);
				clipFieldDifference(differences, "frameRate", source.getFrameRate(), stream.getFrameRate(), true);
				clipFieldDifference(differences, "aspectRatio", source.getAspectRatio(), stream.getAspectRatio(),
						false);
				if (stream.getCodingType() == StreamCodingType.H265_HEVC) {
					clipFieldDifference(differences, "dynamicRangeType", source.getDynamicRangeType(),
							stream.getDynamicRangeType(), false);
					clipFieldDifference(differences, "colorSpace", source.getColorSpace(), stream.getColorSpace(),
							false);
					// The probe leaves unproven HDR10+ absence null, which ClipInfoWriter encodes as false.
					if (Boolean.TRUE.equals(source.getHdrPlusFlag()) != Boolean.TRUE.equals(stream.getHdrPlusFlag()))
						differences.add("hdrPlusFlag: source=" + source.getHdrPlusFlag() + ", output="
								+ stream.getHdrPlusFlag() + " (changed)");
				}
				if (Boolean.TRUE.equals(source.getOcFlag()))
					differences
							.add("ocFlag: source=true is unsupported by this pipeline; output=" + stream.getOcFlag());
				if (Boolean.TRUE.equals(source.getCrFlag()))
					differences
							.add("crFlag: source=true is unsupported by this pipeline; output=" + stream.getCrFlag());
			} else {
				clipFieldDifference(differences, "language", source.getLanguage(), stream.getLanguage(), false);
				if (stream.getCodingType().isAudio()) {
					clipFieldDifference(differences, "audioChannelLayout", source.getAudioChannelLayout(),
							stream.getAudioChannelLayout(), false);
					clipFieldDifference(differences, "sampleRate", source.getSampleRate(), stream.getSampleRate(),
							false);
				}
			}
			require(differences.isEmpty(),
					"CLPI stream metadata validation failed: " + String.join("; ", differences) + "; sourceStream="
							+ clipStreamDiagnostic(source) + "; outputStream=" + clipStreamDiagnostic(stream));
		}
		require(output.getEpMap() != null && output.getEpMap().getStreams() != null
				&& output.getEpMap().getStreams().stream()
						.anyMatch(s -> s.getEntries() != null && !s.getEntries().isEmpty()),
				"Regenerated CLPI lacks random-access entry points: output.epMap="
						+ (output.getEpMap() == null ? "null" : "present") + ", output.epMap.streamEntryCounts="
						+ (output.getEpMap() == null || output.getEpMap().getStreams() == null ? "null"
								: output.getEpMap().getStreams().stream().map(
										s -> s.getEntries() == null ? "null" : String.valueOf(s.getEntries().size()))
										.toList()));
		List<String> differences = new ArrayList<>();
		clipFieldDifference(differences, "tsRecordingStartPts (90 kHz ticks)", ticks(original.getTsRecordingStartPts()),
				ticks(output.getTsRecordingStartPts()), true);
		clipFieldDifference(differences, "tsRecordingEndPts (90 kHz ticks)", ticks(original.getTsRecordingEndPts()),
				ticks(output.getTsRecordingEndPts()), true);
		Long expectedDuration = original.getTsRecordingStartPts() == null || original.getTsRecordingEndPts() == null
				? null
				: original.getTsRecordingEndPts().getTicks() - original.getTsRecordingStartPts().getTicks();
		clipFieldDifference(differences, "duration (90 kHz ticks, expected from source end-start)", expectedDuration,
				ticks(output.getDuration()), true);
		require(differences.isEmpty(),
				"Regenerated CLPI presentation bounds differ from source: " + String.join("; ", differences)
						+ "; source=" + clipBoundsDiagnostic(original) + "; output=" + clipBoundsDiagnostic(output));
	}

	private static void clipFieldDifference(List<String> differences, String field, Object source, Object output,
			boolean required) {
		if (required && source == null || !Objects.equals(source, output))
			differences.add(field + ": source=" + source + ", output=" + output
					+ (required && source == null ? " (required source value is missing)" : " (changed)"));
	}

	private static String clipStreamsDiagnostic(ClipInfo clip) {
		return clip.getStreams() == null ? "null"
				: clip.getStreams().stream().map(PreservingM2tsClipWriter::clipStreamDiagnostic).toList().toString();
	}

	private static String clipStreamDiagnostic(ClipStream stream) {
		return "{pid=" + stream.getPid() + " (0x" + Integer.toHexString(stream.getPid()) + "), codingType="
				+ stream.getCodingType() + ", videoFormat=" + stream.getVideoFormat() + ", frameRate="
				+ stream.getFrameRate() + ", aspectRatio=" + stream.getAspectRatio() + ", dynamicRangeType="
				+ stream.getDynamicRangeType() + ", colorSpace=" + stream.getColorSpace() + ", hdrPlusFlag="
				+ stream.getHdrPlusFlag() + ", ocFlag=" + stream.getOcFlag() + ", crFlag=" + stream.getCrFlag()
				+ ", language=" + stream.getLanguage() + ", audioChannelLayout=" + stream.getAudioChannelLayout()
				+ ", sampleRate=" + stream.getSampleRate() + "}";
	}

	private static Long ticks(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.getTicks();
	}

	private static String clipBoundsDiagnostic(ClipInfo clip) {
		return "{tsRecordingStartPts=" + ticks(clip.getTsRecordingStartPts()) + ", tsRecordingEndPts="
				+ ticks(clip.getTsRecordingEndPts()) + ", duration=" + ticks(clip.getDuration()) + "} (90 kHz ticks)";
	}

	private static void force(Path path) throws IOException {
		try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE)) {
			file.force(true);
		}
	}

	static void validateSize(Path stage, long maximumOutputBytes) throws IOException {
		long bytes = Files.size(stage);
		require(maximumOutputBytes > 0 && bytes > 0 && bytes <= maximumOutputBytes,
				"Final M2TS exceeds maximumOutputBytes or is empty: " + bytes + " > " + maximumOutputBytes);
	}
}
