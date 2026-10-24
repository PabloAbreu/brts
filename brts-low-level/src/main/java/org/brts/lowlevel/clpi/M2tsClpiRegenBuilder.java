/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/M2tsClpiRegenBuilder.java' is part of BRTS.
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

package org.brts.lowlevel.clpi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.AudioChannelLayoutConverter;
import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.common.utils.AudioUtils;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Regenerates a {@link ClipInfo} (CLPI metadata) by fully parsing an existing M2TS file — without any JSON descriptor
 * or pre-muxed ES files.
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li><b>Phase 1 – PMT scan</b>: {@link M2tsParser} reads PAT/PMT to extract stream metadata (PIDs, coding types, video
 * format/frame-rate, audio channels, languages).</li>
 * <li><b>Phase 2 – Full-file EP scan</b>: {@link M2tsDemuxer} iterates every source packet; for each video PES access
 * unit the PTS is decoded from the PES header and the bitstream is probed to determine whether the access unit is a
 * random-access picture (IDR for H.264, BLA/IDR/CRA for HEVC, I-frame for MPEG-2). Every entry contains its PTS (90
 * kHz) and SPN (source packet number).</li>
 * <li><b>Phase 3 – Assembly</b>: stream metadata and EP entries are composed into a {@link ClipInfo} model ready for
 * {@link org.brts.lowlevel.writer.ClipInfoWriter}.</li>
 * </ol>
 */
@Slf4j
public class M2tsClpiRegenBuilder {

	@FunctionalInterface
	interface MetadataProbe {
		Map<Integer, M2tsVideoMetadataProbe.Metadata> probe(Path path, Map<Integer, StreamCodingType> videos)
				throws IOException;
	}

	private final MetadataProbe metadataProbe;

	static final String PROBE_ENGINE_PROPERTY = M2tsVideoMetadataProbeFactory.ENGINE_PROPERTY;

	public M2tsClpiRegenBuilder() {
		this(defaultProbe(BrtsFileConfig.getInstance()::getProperty));
	}

	static MetadataProbe defaultProbe(UnaryOperator<String> config) {
		return M2tsVideoMetadataProbeFactory.createDefault(config)::probe;
	}

	M2tsClpiRegenBuilder(MetadataProbe metadataProbe) {
		this.metadataProbe = metadataProbe;
	}

	/** MPEG-2 PTS/PCR_base modulo in 90 kHz ticks (33-bit counter). */
	static final long PTS_MODULO = 1L << 33;

	/** PCR modulo in 27 MHz ticks ({@code PCR_base * 300 + PCR_ext}). */
	static final long PCR_27MHZ_MODULO = PTS_MODULO * 300L;

	/**
	 * Parses {@code m2tsPath} and builds a fully-populated {@link ClipInfo}.
	 *
	 * @param m2tsPath path to the {@code .m2ts} source file
	 * @param clipName 5-digit clip name without extension (e.g. {@code "00001"})
	 * @return populated {@link ClipInfo} ready for {@link org.brts.lowlevel.writer.ClipInfoWriter}
	 * @throws IOException on I/O error
	 */
	public ClipInfo build(Path m2tsPath, String clipName) throws IOException {
		return build(m2tsPath, clipName, null);
	}

	/**
	 * Regenerates CLPI with automatic UHD version selection and optional per-PID overrides. Probing defaults to best
	 * effort; strict mode requires known HEVC dynamic range and color space before scanning the full file.
	 */
	public ClipInfo build(Path m2tsPath, String clipName, ClpiRegenOptions options) throws IOException {
		log.info("Regenerating CLPI for clip '{}' from {}", clipName, m2tsPath);

		// Phase 1 – stream metadata from PAT/PMT
		M2tsInfo info = new M2tsParser().parse(m2tsPath);
		log.debug("M2TS stream info: {}",
				JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValueAsString(info));
		ClipInfo metadata = prepareMetadata(m2tsPath, info, options);

		M2tsStreamInfo videoStream = findFirstVideoStream(info);
		if (videoStream == null) {
			log.warn("No video stream found in {}; CLPI will have empty EP map", m2tsPath.getFileName());
		}

		// Phase 2 – full-file scan for PTS range and random-access entry points
		EpScanResult scan = doEpScan(m2tsPath, info, videoStream);

		// Phase 3 – assemble ClipInfo
		ClipInfo clip = assemble(clipName, info, videoStream, scan);
		clip.setVersion(metadata.getVersion());
		clip.setStreams(metadata.getStreams());
		return clip;
	}

	/** Metadata-only phase, separated for deterministic testing and validation before the full EP scan. */
	ClipInfo prepareMetadata(Path path, M2tsInfo info, ClpiRegenOptions options) throws IOException {
		ClpiRegenOptions effective = options != null ? options : new ClpiRegenOptions();
		ClpiRegenOptions.ProbeMode mode = effective.getProbeMode() != null ? effective.getProbeMode()
				: ClpiRegenOptions.ProbeMode.BEST_EFFORT;
		Map<Integer, ClpiRegenOptions.VideoOverrides> overrides = effective.getVideoStreams() != null
				? effective.getVideoStreams()
				: Map.of();
		Map<Integer, StreamCodingType> videos = M2tsVideoMetadataProbeFactory.videoStreamsByPid(info);
		validateOverrides(overrides, videos);
		Map<Integer, M2tsVideoMetadataProbe.Metadata> detected = Map.of();
		if (mode != ClpiRegenOptions.ProbeMode.DISABLED && !videos.isEmpty()) {
			try {
				detected = metadataProbe.probe(path, videos);
			} catch (IOException | LinkageError e) {
				log.warn("CLPI metadata probe failed for {} (PIDs {}): {}; using PMT metadata and overrides", path,
						videos.keySet(), e.toString());
			}
		}
		List<ClipStream> streams = buildClipStreams(info.getStreams());
		boolean uhd = false;
		for (ClipStream stream : streams) {
			// first audio overrides
			if (stream.getCodingType().isAudio() && effective.getAudioStreams() != null) {
				ClpiRegenOptions.AudioOverrides audioOverride = effective.getAudioStreams().get(stream.getPid());
				if (audioOverride != null) {
					applyAudioOverrides(stream, audioOverride);
				}
			} else if (stream.getCodingType().isSubtitle() && effective.getSubtitleStreams() != null) {
				ClpiRegenOptions.SubtitleOverrides subtitleOverride = effective.getSubtitleStreams()
						.get(stream.getPid());
				if (subtitleOverride != null) {
					applySubtitleOverrides(stream, subtitleOverride);
				}
			}

			// then video
			if (!stream.getCodingType().isVideo()) {
				continue;
			}
			boolean hevc = stream.getCodingType() == StreamCodingType.H265_HEVC;
			boolean fourK = Integer.valueOf(8).equals(stream.getVideoFormat());
			M2tsVideoMetadataProbe.Metadata metadata = detected.get(stream.getPid());
			if (metadata != null) {
				fourK |= metadata.fourK();
				if (metadata.videoFormat() != null) {
					stream.setVideoFormat(metadata.videoFormat());
				}
				if (hevc) {
					stream.setDynamicRangeType(metadata.dynamicRangeType());
					stream.setColorSpace(metadata.colorSpace());
					stream.setHdrPlusFlag(metadata.hdrPlusFlag());
				}
			}
			ClpiRegenOptions.VideoOverrides override = overrides.get(stream.getPid());
			if (override != null) {
				if (fourK && override.getVideoFormat() != null && override.getVideoFormat() != 8) {
					throw new IllegalArgumentException("CLPI override for PID " + stream.getPid()
							+ " videoFormat conflicts with detected 4K video; expected 8");
				}
				applyOverrides(stream, override);
			}
			uhd |= hevc || fourK || Integer.valueOf(8).equals(stream.getVideoFormat());
			if (hevc) {
				boolean rangeKnown = stream.getDynamicRangeType() != null && stream.getDynamicRangeType() <= 2;
				boolean colorKnown = Integer.valueOf(1).equals(stream.getColorSpace())
						|| Integer.valueOf(2).equals(stream.getColorSpace());
				if (!rangeKnown || !colorKnown) {
					String message = "Unresolved HEVC dynamicRangeType/colorSpace for PID " + stream.getPid() + " in "
							+ path + "; supply per-PID CLPI overrides (unknowns encode as zero)";
					if (mode == ClpiRegenOptions.ProbeMode.STRICT) {
						throw new IOException(message);
					}
					log.warn(message);
				}
				if (stream.getOcFlag() == null || stream.getCrFlag() == null || stream.getHdrPlusFlag() == null) {
					log.warn(
							"CLPI PID {} in {} has unknown OC/CR/HDR+ flags; unset flags encode as false. "
									+ "A bounded prefix cannot prove absence; provide overrides if known",
							stream.getPid(), path);
				}
			}

			// if values are missing, processing will halt here with an exception
			if (stream.getVideoFormat() == null || stream.getFrameRate() == null || stream.getAspectRatio() == null) {
				throw new IllegalStateException(
						"Missing required video stream parameters videoFormat/frameRate/aspectRatio for PID: "
								+ stream.getPid());
			}
		}
		ClipInfo clip = new ClipInfo();
		clip.setVersion(uhd ? "0300" : "0200");
		clip.setStreams(streams);
		return clip;
	}

	private static void validateOverrides(Map<Integer, ClpiRegenOptions.VideoOverrides> overrides,
			Map<Integer, StreamCodingType> videos) {
		for (var entry : overrides.entrySet()) {
			Integer pid = entry.getKey();
			if (!videos.containsKey(pid)) {
				throw new IllegalArgumentException("CLPI override PID " + pid + " is not a known video PID");
			}
			var value = entry.getValue();
			if (value == null) {
				continue;
			}
			validateNibble(pid, "videoFormat", value.getVideoFormat());
			validateNibble(pid, "frameRate", value.getFrameRate());
			validateNibble(pid, "aspectRatio", value.getAspectRatio());
			validateNibble(pid, "dynamicRangeType", value.getDynamicRangeType());
			validateNibble(pid, "colorSpace", value.getColorSpace());
			if (videos.get(pid) != StreamCodingType.H265_HEVC
					&& (value.getCrFlag() != null || value.getDynamicRangeType() != null
							|| value.getColorSpace() != null || value.getHdrPlusFlag() != null)) {
				throw new IllegalArgumentException(
						"CLPI override PID " + pid + " has HEVC-only attributes on " + videos.get(pid));
			}
		}
	}

	private static void validateNibble(int pid, String field, Integer value) {
		if (value != null && (value < 0 || value > 15)) {
			throw new IllegalArgumentException(
					"CLPI override PID " + pid + " " + field + " must be between 0 and 15, got " + value);
		}
	}

	private static void applyAudioOverrides(ClipStream stream, ClpiRegenOptions.AudioOverrides value) {
		if (value.getAudioChannelLayout() != null)
			stream.setAudioChannelLayout(value.getAudioChannelLayout());
		if (value.getSampleRate() != null)
			stream.setSampleRate(value.getSampleRate());
		if (value.getLanguage() != null)
			stream.setLanguage(value.getLanguage());
	}

	private static void applySubtitleOverrides(ClipStream stream, ClpiRegenOptions.SubtitleOverrides value) {
		if (value.getLanguage() != null)
			stream.setLanguage(value.getLanguage());
	}

	private static void applyOverrides(ClipStream stream, ClpiRegenOptions.VideoOverrides value) {
		if (value.getVideoFormat() != null)
			stream.setVideoFormat(value.getVideoFormat());
		if (value.getFrameRate() != null)
			stream.setFrameRate(value.getFrameRate());
		if (value.getAspectRatio() != null)
			stream.setAspectRatio(value.getAspectRatio());
		if (value.getOcFlag() != null)
			stream.setOcFlag(value.getOcFlag());
		if (value.getCrFlag() != null)
			stream.setCrFlag(value.getCrFlag());
		if (value.getDynamicRangeType() != null)
			stream.setDynamicRangeType(value.getDynamicRangeType());
		if (value.getColorSpace() != null)
			stream.setColorSpace(value.getColorSpace());
		if (value.getHdrPlusFlag() != null)
			stream.setHdrPlusFlag(value.getHdrPlusFlag());
	}

	// =========================================================================
	// Phase 1 helpers
	// =========================================================================

	private static M2tsStreamInfo findFirstVideoStream(M2tsInfo info) {
		if (info.getStreams() == null)
			return null;
		return info.getStreams().stream().filter(s -> s.getCodingType() != null && s.getCodingType().isVideo())
				.findFirst().orElse(null);
	}

	// =========================================================================
	// Phase 2 – full-file random-access picture scan
	// =========================================================================

	/** Accumulated results of the EP-map scan pass. */
	private static class EpScanResult {

		long firstPts = -1;

		long lastPts = -1;

		final List<EpMap.EpMapEntry> entries = new ArrayList<>();

	}

	private EpScanResult doEpScan(Path path, M2tsInfo info, M2tsStreamInfo videoStream) throws IOException {
		EpScanResult result = new EpScanResult();
		if (videoStream == null)
			return result;

		MonotonicTimestampUnwrapper pcrUnwrapper = new MonotonicTimestampUnwrapper(PCR_27MHZ_MODULO);
		MonotonicTimestampUnwrapper ptsUnwrapper = new MonotonicTimestampUnwrapper(PTS_MODULO);

		int videoPid = videoStream.getPid();
		StreamCodingType videoType = videoStream.getCodingType();

		Set<Integer> filter = new HashSet<>();
		filter.add(videoPid);
		filter.add(info.getPcrPid());
		// reset PCR and re-calculate from scratch during the EP scan
		// since the M2tsInfo holds only results for a 50000 packets scan.
		info.setFirstPcr27MHz(-1);
		info.setLastPcr27MHz(-1);

		VideoEntryPointScanner scanner = new VideoEntryPointScanner(videoType, raw -> {
			long pts = ptsUnwrapper.unwrap(raw);
			if (result.firstPts < 0 || pts < result.firstPts)
				result.firstPts = pts;
			if (pts > result.lastPts)
				result.lastPts = pts;
			return pts;
		}, (pts, spn) -> {
			if (!result.entries.isEmpty()) {
				EpMap.EpMapEntry previous = result.entries.getLast();
				if (previous.getPtsTicks() == pts && previous.getSpn() == spn)
					return;
				if (pts < previous.getPtsTicks())
					log.warn("Non-monotonic video entry PTS in {}: PID={} SPN={} PTS={} previous={}", path, videoPid,
							spn, pts, previous.getPtsTicks());
			}
			EpMap.EpMapEntry entry = new EpMap.EpMapEntry();
			entry.setPtsTicks(pts);
			entry.setSpn(spn);
			result.entries.add(entry);
			log.trace("EP entry: PTS={} SPN={}", pts, spn);
		});

		try (M2tsPacketHandler handler = new M2tsPacketHandler() {

			private int lastCounter = -1;

			private byte[] previousPacket;

			private long transportResets;

			@Override
			public void onPayload(int pid, byte[] sp, int offset, int length, boolean payloadUnitStart,
					long packetIndex, long ats) {

				if (pid == info.getPcrPid()) {
					long pcr = pcrUnwrapper.unwrap(M2tsParser.extractPcr(sp));
					if (pcr >= 0) {
						if (info.getFirstPcr27MHz() < 0 || pcr < info.getFirstPcr27MHz())
							info.setFirstPcr27MHz(pcr);
						if (pcr > info.getLastPcr27MHz())
							info.setLastPcr27MHz(pcr);
					}
				}
				if (pid != videoPid)
					return;
				int control = (sp[7] >> 4) & 3;
				boolean discontinuity = (control == 2 || control == 3) && (sp[8] & 0xFF) > 0 && (sp[9] & 0x80) != 0;
				if (discontinuity) {
					scanner.reset();
					lastCounter = -1;
				}
				if (control == 2 || control == 0)
					return; // adaptation-only PCR callbacks contain no ES bytes
				int counter = sp[7] & 15;
				if (counter == lastCounter && previousPacket != null
						&& Arrays.equals(previousPacket, 4, sp.length, sp, 4, sp.length))
					return; // an exact retransmission, not another PES/access unit
				boolean lost = lastCounter >= 0 && counter != ((lastCounter + 1) & 15);
				boolean scrambled = (sp[7] & 0xC0) != 0;
				if (lost || scrambled) {
					scanner.reset();
					if (transportResets++ < 5)
						log.warn("Resetting EP scanner in {}: PID={} SPN={} continuityLoss={} scrambled={}", path,
								videoPid, packetIndex, lost, scrambled);
				}
				lastCounter = counter;
				if (previousPacket == null)
					previousPacket = new byte[sp.length];
				System.arraycopy(sp, 0, previousPacket, 0, sp.length);
				if (!scrambled)
					scanner.accept(sp, offset, length, payloadUnitStart, packetIndex);
			}

			@Override
			public void close() {
				scanner.reset();
			}

		}) {
			// keepTables includes a dedicated PCR PID in the demuxer's candidate set; the explicit filter still
			// limits callbacks to video and PCR. No global PID-filter semantics need to change.
			new M2tsDemuxer().demux(path, info, handler, filter, 0, -1, true);
		}
		if (scanner.malformedHeaders() > 0 || scanner.untimestampedPictures() > 0)
			log.warn(
					"EP scan in {} (PID={}): rejected {} malformed PES headers; skipped {} random-access pictures "
							+ "without a dedicated PTS",
					path, videoPid, scanner.malformedHeaders(), scanner.untimestampedPictures());

		log.info("EP scan complete: {} random-access entries, PTS range [{} .. {}]", result.entries.size(),
				result.firstPts, result.lastPts);
		return result;
	}

	// =========================================================================
	// Phase 3 – assemble the ClipInfo model
	// =========================================================================

	private static ClipInfo assemble(String clipName, M2tsInfo info, M2tsStreamInfo videoStream, EpScanResult scan) {
		ClipInfo clip = new ClipInfo();
		clip.setClipName(clipName);
		clip.setClipStreamType(1); // AV clip
		clip.setApplicationType(1); // Movie

		// Determine PTS range in 90 kHz ticks.
		// Prefer PTS extracted from PES headers; fall back to PCR-derived values.
		long firstPts;
		long lastPts;
		log.debug("PES PTS range: [{} .. {}]", scan.firstPts, scan.lastPts);
		if (scan.firstPts >= 0 && scan.lastPts >= 0) {
			firstPts = scan.firstPts;
			lastPts = scan.lastPts;
		} else if (info.getFirstPcr27MHz() >= 0 && info.getLastPcr27MHz() >= 0) {
			// PCR is at 27 MHz; 90 kHz = 27 MHz / 300
			firstPts = info.getFirstPcr27MHz() / 300;
			lastPts = info.getLastPcr27MHz() / 300;
		} else {
			firstPts = 0;
			lastPts = 0;
		}

		clip.setTsRecordingStartPts(Timestamp.ofTicks(firstPts));
		clip.setTsRecordingEndPts(Timestamp.ofTicks(lastPts));
		clip.setDuration(Timestamp.ofTicks(Math.max(0L, lastPts - firstPts)));

		clip.setStreams(buildClipStreams(info.getStreams()));

		clip.setNumSourcePackets(info.getTotalPackets());
		// TODO find what this should be
		// real-life values seem to often be 6000000
		clip.setTsRecordingRate(6_000_000);

		// EP map — one stream entry for the primary video PID
		if (videoStream != null && !scan.entries.isEmpty()) {
			log.debug("Adding EP map for video stream PID: {}", videoStream.getPid());
			log.debug("EP entries found: {}", scan.entries.size());
			EpMap.EpMapStream eps = new EpMap.EpMapStream();
			eps.setPid(videoStream.getPid());
			eps.setEpType(1); // I-frame only (standard)
			eps.setEntries(new ArrayList<>(scan.entries));

			EpMap epMap = new EpMap();
			epMap.setStreams(List.of(eps));
			clip.setEpMap(epMap);
		} else {
			log.warn("No video stream or EP entries found for clip: {}", clipName);
		}

		return clip;
	}

	private static List<ClipStream> buildClipStreams(List<M2tsStreamInfo> streams) {
		List<ClipStream> out = new ArrayList<>();
		if (streams == null)
			return out;
		for (M2tsStreamInfo si : streams) {
			if (si.getCodingType() == null)
				continue;
			ClipStream cs = new ClipStream();
			cs.setPid(si.getPid());
			cs.setCodingType(si.getCodingType());
			if (si.getCodingType().isVideo()) {
				// videoFormat and frameRate codes come directly from the HDMV
				// registration descriptor parsed by M2tsParser.
				cs.setVideoFormat(si.getVideoFormat());
				cs.setFrameRate(si.getFrameRate());
				cs.setAspectRatio(si.getAspectRatio());
			} else if (si.getCodingType().isAudio()) {
				cs.setAudioChannelLayout(AudioChannelLayoutConverter.channelsToLayout(si.getChannels()));
				cs.setSampleRate(AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(si.getSampleRateHz()));
				cs.setLanguage(si.getLanguage() != null ? si.getLanguage() : "und");
			} else {
				// PGS / IG / text subtitle
				if (si.getCodingType() == StreamCodingType.TEXT_SUBTITLE) {
					cs.setCharacterCode(0); // UTF-8 default
				}
				cs.setLanguage(si.getLanguage() != null ? si.getLanguage() : "und");
			}
			out.add(cs);
		}
		return out;
	}

	// =========================================================================
	// PES and bitstream helpers (package-private for unit testing)
	// =========================================================================

	/**
	 * Converts a modulo counter into a monotonic timeline by counting wrap-arounds.
	 */
	static final class MonotonicTimestampUnwrapper {

		private final long modulo;

		private long wraps;

		private long lastRaw = -1;

		MonotonicTimestampUnwrapper(long modulo) {
			if (modulo <= 0)
				throw new IllegalArgumentException("modulo must be > 0");
			this.modulo = modulo;
		}

		long unwrap(long raw) {
			if (raw < 0)
				return -1;
			// A large backward jump means the source counter wrapped.
			if (lastRaw >= 0 && raw < lastRaw && (lastRaw - raw) > (modulo / 2)) {
				wraps++;
			}
			lastRaw = raw;
			return raw + wraps * modulo;
		}

	}

	// =========================================================================
	// Blu-ray attribute code mappings
	// =========================================================================

}
