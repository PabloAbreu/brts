package org.brts.common.m2ts;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * Core MPEG-2 Transport Stream demultiplexer for Blu-ray M2TS files.
 * <p>
 * Reads 192-byte source packets, strips the 4-byte {@code TP_extra_header} and the TS header / adaptation field, and
 * delivers raw PES-payload bytes to an {@link M2tsPacketHandler}.
 * <p>
 * The demuxer itself is stateless with respect to <em>output</em> — all output decisions are made by the handler. This
 * allows the same demuxing logic to drive file-per-PID extraction, MKV muxing, or any other consumer.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * M2tsInfo info = new M2tsParser().parse(inputPath);
 * try (FilePacketHandler handler = new FilePacketHandler(info, outputDir, pidFilter)) {
 * 	new M2tsDemuxer().demux(inputPath, info, handler, pidFilter);
 * }
 * }</pre>
 */
@Slf4j
public class M2tsDemuxer {
	/** Blu-ray source packet: 4-byte TP_extra_header + 188-byte TS packet. */
	static final int SOURCE_PACKET_SIZE = 192;

	private static final int SYNC_BYTE = 0x47;

	/**
	 * Demuxes the M2TS file at {@code source}, delivering payload bytes for every packet whose PID is in
	 * {@code pidFilter} to the given {@code handler}.
	 *
	 * @param source    path to the M2TS source file
	 * @param info      stream metadata (from {@link M2tsParser})
	 * @param handler   receives the raw payload bytes per PID
	 * @param pidFilter only these PIDs are delivered; pass {@code null} or empty to deliver all PIDs present in
	 *                  {@code info}
	 * @throws IOException on I/O error
	 */
	public void demux(Path source, M2tsInfo info, M2tsPacketHandler handler, Set<Integer> pidFilter)
			throws IOException {
		demux(source, info, handler, pidFilter, 0, -1, false);
	}

	/**
	 * Demuxes a range of packets from the M2TS file at {@code source}.
	 *
	 * @param source      path to the M2TS source file
	 * @param info        stream metadata (from {@link M2tsParser})
	 * @param handler     receives the raw payload bytes per PID
	 * @param pidFilter   only these PIDs are delivered; pass {@code null} or empty to deliver all PIDs present in
	 *                    {@code info}
	 * @param startPacket first packet to process (0-based, inclusive)
	 * @param stopPacket  last packet to process (inclusive); pass {@code -1} to process until end-of-file
	 * @param keepTables  if true, PAT/PMT/PCR PIDs are included, unless {@code pidFilter} is set
	 * @throws IOException on I/O error
	 */
	public void demux(Path source, M2tsInfo info, M2tsPacketHandler handler, Set<Integer> pidFilter, long startPacket,
			long stopPacket, boolean keepTables) throws IOException {

		Set<Integer> activePids = resolveActivePids(info, pidFilter, keepTables);

		log.info("Demuxing M2TS: {} ({} active PIDs)", source.getFileName(), activePids.size());

		try (InputStream in = new BufferedInputStream(Files.newInputStream(source))) {
			byte[] sp = new byte[SOURCE_PACKET_SIZE];

			// Skip to start packet
			if (startPacket > 0) {
				long toSkip = startPacket * SOURCE_PACKET_SIZE;
				while (toSkip > 0) {
					long skipped = in.skip(toSkip);
					if (skipped <= 0)
						break;
					toSkip -= skipped;
				}
			}

			long packetIndex = startPacket;
			long effectiveStop = stopPacket >= 0 ? stopPacket : Long.MAX_VALUE;

			while (packetIndex <= effectiveStop) {
				int read = readFully(in, sp);
				if (read < SOURCE_PACKET_SIZE)
					break;

				if ((sp[4] & 0xFF) != SYNC_BYTE) {
					log.warn("Lost sync at packet {}", packetIndex);
					packetIndex++;
					continue;
				}

				// Extract ATS from TP_extra_header (30-bit, 27 MHz)
				long ats = (((long) (sp[0] & 0x3F)) << 24) | (((long) (sp[1] & 0xFF)) << 16)
						| (((long) (sp[2] & 0xFF)) << 8) | (((long) (sp[3] & 0xFF)));

				int b1 = sp[5] & 0xFF;
				int b2 = sp[6] & 0xFF;
				boolean transportError = (b1 & 0x80) != 0;
				int pid = ((b1 & 0x1F) << 8) | b2;

				int b3 = sp[7] & 0xFF;
				int adaptCtrl = (b3 >> 4) & 0x03;

				if (transportError || !activePids.contains(pid)) {
					// log only if unknown PID
					if (pid != 0x1FFF && pid != 0 && pid != 0x100 && pid != 0x1F && pid != 0x1001)
						log.debug("Skipped packet at index {} (PID {}, transportError={}, adaptCtrl={})", packetIndex,
								pid, transportError, adaptCtrl);
					packetIndex++;
					continue;
				}

				// Locate payload within the 188-byte TS packet (sp[4..191])
				int payloadOff = 4 + 4; // skip 4-byte tp_extra + 4-byte TS header
				if (adaptCtrl == 2) {
					// adaptation field only, no payload
					if (pid == info.getPcrPid()) {
						// FIXME call that for PCR in other cases too
						handler.onPayload(pid, sp, 0, SOURCE_PACKET_SIZE, false, packetIndex, ats);
					} else
						log.debug("Skipped adaptation field only packet at index {}", packetIndex);
					packetIndex++;
					continue;
				}
				if (adaptCtrl == 3) {
					// adaptation field + payload: skip adaptation field
					int afLen = sp[payloadOff] & 0xFF;
					payloadOff += 1 + afLen;

					if (payloadOff >= SOURCE_PACKET_SIZE) {
						log.debug("Skipped adaptation field + payload packet at index {}", packetIndex);
						packetIndex++;
						continue;
					}
				}
				// adaptCtrl == 1: payload only (payloadOff stays as-is)

				if (payloadOff >= SOURCE_PACKET_SIZE) {
					log.debug("Skipped payloadOff too big packet at index {}", packetIndex);
					packetIndex++;
					continue;
				}

				boolean payloadUnitStart = (b1 & 0x40) != 0;
				int payloadLen = SOURCE_PACKET_SIZE - payloadOff;
				handler.onPayload(pid, sp, payloadOff, payloadLen, payloadUnitStart, packetIndex, ats);

				packetIndex++;
			}

			log.info("Demuxed {} source packets", packetIndex - startPacket);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Resolves the effective set of PIDs to process.
	 */
	static Set<Integer> resolveActivePids(M2tsInfo info, Set<Integer> pidFilter, boolean keepTables) {
		Set<Integer> allPids = new LinkedHashSet<>();
		if (info.getStreams() != null) {
			for (M2tsStreamInfo s : info.getStreams())
				allPids.add(s.getPid());
		}
		if (keepTables) {
			allPids.add(0); // PAT
			if (info.getProgramPidMap() != null && !info.getProgramPidMap().isEmpty()) {
				allPids.addAll(info.getProgramPidMap().values()); // all PMT PIDs
			} else {
				allPids.add(info.getPmtPid()); // backward compat
			}
			allPids.add(info.getPcrPid());
			if (info.getSitPid() >= 0) {
				allPids.add(info.getSitPid()); // SIT
			}
			allPids.add(0x1FFF); // Null packet PID
			log.debug("Added table PIDs: PAT=0, PMT(s)={}, PCR={}, SIT={}",
					info.getProgramPidMap() != null ? info.getProgramPidMap().values() : info.getPmtPid(),
					info.getPcrPid(), info.getSitPid());
		}
		if (pidFilter != null && !pidFilter.isEmpty()) {
			allPids.retainAll(pidFilter);
		}
		return allPids;
	}

	private int readFully(InputStream in, byte[] buf) throws IOException {
		int total = 0;
		while (total < buf.length) {
			int n = in.read(buf, total, buf.length - total);
			if (n < 0)
				break;
			total += n;
		}
		return total;
	}

}
