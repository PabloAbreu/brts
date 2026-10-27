/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/TimingPreservingExtractor.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.common.m2ts;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.SegmentTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.Extensions;

/** Strict extraction path. Deliberately separate from the permissive, legacy raw demuxer. */
final class TimingPreservingExtractor {
	private final Map<Integer, Stream> streams = new LinkedHashMap<>();
	private Long clockReference;
	private Long firstPcr;
	private Long lastPcr;

	void extract(Path source, M2tsInfo info, Path outputDir, Set<Integer> filter, String expectedSourceSha256)
			throws IOException {
		Files.createDirectories(outputDir);
		Path manifestPath = outputDir.resolve(M2tsTimingManifest.FILE_NAME);
		if (Files.exists(manifestPath) && Files.isSameFile(source, manifestPath))
			throw new IOException("Timing manifest aliases the source: " + manifestPath);
		// A failed rerun must not leave an old success contract beside partial new ES files.
		Files.deleteIfExists(manifestPath);
		Path temporaryManifest = null;
		try {
			long sourceSize = Files.size(source);
			if (sourceSize == 0 || sourceSize % 192 != 0)
				throw new IOException("Empty or truncated M2TS source: " + source);
			for (M2tsStreamInfo metadata : info.getStreams() == null ? List.<M2tsStreamInfo>of() : info.getStreams()) {
				if (filter != null && !filter.isEmpty() && !filter.contains(metadata.getPid()))
					continue;
				int pid = metadata.getPid();
				if (pid <= 0 || pid >= 0x1FFF || streams.containsKey(pid))
					throw new IOException("Invalid or duplicate elementary PID: " + pid);
				String fileName = String.format("pid_%04x.%s", pid, Extensions.extensionForStream(metadata));
				Path path = outputDir.resolve(fileName);
				if (Files.exists(path) && Files.isSameFile(source, path))
					throw new IOException("Extraction output aliases the source: " + path);
				streams.put(pid, new Stream(metadata, fileName, new BufferedOutputStream(Files.newOutputStream(path))));
			}
			if (streams.isEmpty() || (filter != null && !filter.isEmpty() && !streams.keySet().containsAll(filter)))
				throw new IOException("No streams selected or requested PID is absent from source metadata");
			String sourceSha256 = readTransport(source, info.getPcrPid());
			if (Files.size(source) != sourceSize
					|| (expectedSourceSha256 != null && !sourceSha256.equalsIgnoreCase(expectedSourceSha256)))
				throw new IOException("Source changed during extraction: " + source);
			List<StreamTiming> timings = new ArrayList<>();
			for (Stream stream : streams.values()) {
				stream.finishPes();
				if (!stream.started || stream.firstPts == null || stream.segmentUsed != 0 || stream.outputOffset == 0)
					throw stream.error("Missing source PTS, empty ES or incomplete PGS segment at EOF");
				timings.add(stream.timing());
			}
			closeStreams();
			M2tsTimingManifest manifest = new M2tsTimingManifest(M2tsTimingManifest.CURRENT_VERSION,
					source.toAbsolutePath().normalize().toString(), sourceSize, firstPcr, List.copyOf(timings),
					sourceSha256);
			temporaryManifest = Files.createTempFile(outputDir, ".timing-", ".json");
			M2tsTiming.write(temporaryManifest, manifest);
			try {
				Files.move(temporaryManifest, manifestPath, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporaryManifest, manifestPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IllegalArgumentException | ArithmeticException e) {
			throw new IOException("Invalid source timing in " + source + ": " + e.getMessage(), e);
		} finally {
			try {
				closeStreams();
			} finally {
				if (temporaryManifest != null)
					Files.deleteIfExists(temporaryManifest);
			}
		}
	}

	private void closeStreams() throws IOException {
		IOException failure = null;
		for (Stream stream : streams.values()) {
			if (stream.closed)
				continue;
			stream.closed = true;
			try {
				stream.out.close();
			} catch (IOException e) {
				if (failure == null)
					failure = e;
				else
					failure.addSuppressed(e);
			}
		}
		if (failure != null)
			throw failure;
	}

	private String readTransport(Path source, int pcrPid) throws IOException {
		MessageDigest digest = SourceFingerprint.newDigest();
		try (InputStream in = new BufferedInputStream(Files.newInputStream(source))) {
			byte[] packet = new byte[192];
			long index = 0;
			int count;
			while ((count = in.readNBytes(packet, 0, packet.length)) != 0) {
				digest.update(packet, 0, count);
				if (count != 192 || packet[4] != 0x47)
					throw new IOException("Truncated packet or lost sync at source packet " + index);
				int pid = ((packet[5] & 0x1F) << 8) | (packet[6] & 0xFF);
				Stream stream = streams.get(pid);
				if (stream == null && pid != pcrPid) {
					index++;
					continue;
				}
				int control = (packet[7] >> 4) & 3;
				if ((packet[5] & 0x80) != 0 || (packet[7] & 0xC0) != 0 || control == 0)
					throw new IOException("Corrupt/scrambled transport at packet " + index + ", PID " + pid);
				int offset = 8;
				if ((control & 2) != 0) {
					int length = packet[8] & 0xFF;
					offset = 9 + length;
					if (offset > 192 || (control == 2 && offset != 192) || (control == 3 && offset == 192))
						throw new IOException("Invalid adaptation length at source packet " + index);
					if (length > 0) {
						if ((packet[9] & 0x80) != 0)
							throw new IOException("Unsupported timing discontinuity at source packet " + index);
						if (pid == pcrPid && (packet[9] & 0x10) != 0) {
							if (length < 7)
								throw new IOException("Truncated PCR at source packet " + index);
							int extension = ((packet[14] & 1) << 8) | (packet[15] & 0xFF);
							if ((packet[14] & 0x7E) != 0x7E || extension >= 300)
								throw new IOException("Malformed PCR at source packet " + index);
							long raw = ((long) (packet[10] & 0xFF) << 25) | ((long) (packet[11] & 0xFF) << 17)
									| ((long) (packet[12] & 0xFF) << 9) | ((long) (packet[13] & 0xFF) << 1)
									| ((packet[14] & 0x80) >> 7);
							long pcr = unwrap(raw);
							if (lastPcr != null && pcr < lastPcr)
								throw new IOException("Backward PCR at source packet " + index);
							if (firstPcr == null)
								firstPcr = pcr;
							lastPcr = pcr;
							clockReference = pcr;
						}
					}
				}
				if (stream != null && (control & 1) != 0) {
					int cc = packet[7] & 0x0F;
					if (stream.continuity >= 0 && cc != ((stream.continuity + 1) & 0x0F))
						throw stream.error("Lost/duplicate transport packet at source packet " + index);
					stream.continuity = cc;
					stream.accept(packet, offset, 192 - offset, (packet[5] & 0x40) != 0, index);
				}
				index++;
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private long unwrap(long raw) {
		return clockReference == null ? raw : M2tsTiming.unwrap33(raw, clockReference);
	}

	private final class Stream {
		private final M2tsStreamInfo metadata;
		private final String fileName;
		private final OutputStream out;
		private final boolean pgs;
		private final byte[] header = new byte[264];
		private final byte[] segment;
		private final List<PesTiming> pesTimings = new ArrayList<>();
		private final List<SegmentTiming> segments = new ArrayList<>();
		private int continuity = -1;
		private boolean started;
		private boolean closed;
		private int headerUsed;
		private int headerSize = 9;
		private boolean headerComplete;
		private long remaining;
		private long packetIndex;
		private long outputOffset;
		private Long pts;
		private Long dts;
		private Long firstPts;
		private Long firstDts;
		private Long previousDts;
		private int segmentUsed;
		private int segmentSize = 3;
		private long segmentPts;
		private Long segmentDts;

		Stream(M2tsStreamInfo metadata, String fileName, OutputStream out) {
			this.metadata = metadata;
			this.fileName = fileName;
			this.out = out;
			pgs = metadata.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS;
			segment = pgs ? new byte[65538] : null;
		}

		void accept(byte[] data, int offset, int length, boolean start, long index) throws IOException {
			if (start) {
				finishPes();
				started = true;
				headerUsed = 0;
				headerSize = 9;
				headerComplete = false;
				packetIndex = index;
			} else if (!started) {
				throw error("Payload before first PES start at source packet " + index);
			}
			int end = offset + length;
			while (offset < end && !headerComplete) {
				int take = Math.min(end - offset, headerSize - headerUsed);
				System.arraycopy(data, offset, header, headerUsed, take);
				headerUsed += take;
				offset += take;
				if (headerUsed == 9 && headerSize == 9) {
					if (header[0] != 0 || header[1] != 0 || header[2] != 1 || (header[6] & 0xC0) != 0x80
							|| (header[6] & 0x30) != 0)
						throw error("Invalid or scrambled MPEG-2 PES header");
					headerSize = 9 + (header[8] & 0xFF);
				}
				if (headerUsed == headerSize)
					parseHeader();
			}
			if (!headerComplete)
				return;
			int take = remaining < 0 ? end - offset : (int) Math.min(remaining, end - offset);
			if (pgs)
				acceptSegments(data, offset, take);
			else {
				out.write(data, offset, take);
				outputOffset += take;
			}
			if (remaining >= 0)
				remaining -= take;
			// Bytes beyond a bounded PES are TS stuffing, never ES or another guessed PES.
			for (int i = offset + take; i < end; i++)
				if ((data[i] & 0xFF) != 0xFF)
					throw error("Non-stuffing bytes beyond declared PES length");
		}

		void parseHeader() throws IOException {
			int size = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
			if (size != 0 && size < headerSize - 6)
				throw error("PES length is smaller than its optional header");
			if (size == 0 && (metadata.getCodingType() == null || !metadata.getCodingType().isVideo()))
				throw error("Unbounded PES is only supported for video");
			remaining = size == 0 ? -1 : size - (headerSize - 6);
			int flags = (header[7] >> 6) & 3;
			if (flags == 1 || (flags == 2 && headerSize < 14) || (flags == 3 && headerSize < 19))
				throw error("Invalid PTS/DTS flags or truncated timestamp fields");
			pts = null;
			dts = null;
			if (flags >= 2) {
				validateTimestamp(9, flags);
				if (flags == 3) {
					validateTimestamp(14, 1);
					dts = unwrap(PesTimestampParser.decodeTimestamp(header, 14));
				}
				long rawPts = PesTimestampParser.decodeTimestamp(header, 9);
				pts = dts == null ? unwrap(rawPts) : M2tsTiming.unwrap33(rawPts, dts);
				if (dts != null && (pts < dts || (previousDts != null && dts < previousDts)))
					throw error("Backward DTS or PTS preceding DTS");
				if (firstPts == null)
					firstPts = pts;
				if (dts != null) {
					if (firstDts == null)
						firstDts = dts;
					previousDts = dts;
				}
				long reference = dts == null ? pts : dts;
				if (lastPcr == null && (clockReference == null || reference > clockReference))
					clockReference = reference;
			} else if (pesTimings.isEmpty()) {
				throw error("First PES has no source PTS");
			}
			pesTimings.add(new PesTiming(packetIndex, outputOffset, pts, dts));
			headerComplete = true;
		}

		void validateTimestamp(int offset, int prefix) throws IOException {
			if ((header[offset] & 0xF0) != (prefix << 4) || (header[offset] & 1) == 0 || (header[offset + 2] & 1) == 0
					|| (header[offset + 4] & 1) == 0)
				throw error("Invalid PES timestamp prefix/marker bits");
		}

		void acceptSegments(byte[] data, int offset, int length) throws IOException {
			int end = offset + length;
			while (offset < end) {
				if (segmentUsed == 0) {
					if (pts == null)
						throw error("PGS segment starts in a PES without PTS");
					segmentPts = pts;
					segmentDts = dts;
				}
				int take = Math.min(end - offset, segmentSize - segmentUsed);
				System.arraycopy(data, offset, segment, segmentUsed, take);
				segmentUsed += take;
				offset += take;
				if (segmentUsed == 3 && segmentSize == 3) {
					int type = segment[0] & 0xFF;
					if (type != 0x14 && type != 0x15 && type != 0x16 && type != 0x17 && type != 0x80)
						throw error("Unknown PGS segment type: " + type);
					segmentSize = 3 + ((segment[1] & 0xFF) << 8) + (segment[2] & 0xFF);
				}
				if (segmentUsed == segmentSize) {
					int type = segment[0] & 0xFF;
					M2tsTiming.writeSupSegment(out, segmentPts, segmentDts, type, segment, 3, segmentSize - 3);
					segments.add(new SegmentTiming(outputOffset, type, segmentSize - 3, segmentPts, segmentDts));
					outputOffset += 10L + segmentSize;
					segmentUsed = 0;
					segmentSize = 3;
				}
			}
		}

		void finishPes() throws IOException {
			if (started && (!headerComplete || remaining > 0))
				throw error("Truncated PES header/payload before next PES or EOF");
		}

		StreamTiming timing() {
			return new StreamTiming(metadata.getPid(), fileName, metadata.getCodingType(), firstPts, firstDts,
					List.copyOf(pesTimings), List.copyOf(segments));
		}

		IOException error(String message) {
			return new IOException(message + " (PID " + metadata.getPid() + ", PES source packet " + packetIndex + ")");
		}
	}
}
