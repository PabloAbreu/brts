package org.brts.common.m2ts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for M2TS (MPEG-2 Transport Stream) files used on Blu-ray discs.
 * <p>
 * M2TS files are standard 188-byte MPEG-2 TS packets each prepended with a 4-byte {@code TP_extra_header} (Arrival Time
 * Stamp + copy-permission bits), giving a fixed 192-byte <em>Source Packet</em> size.
 * <p>
 * This parser:
 * <ol>
 * <li>Scans the first {@code MAX_SCAN_PACKETS} source packets (or the full file for small files) to harvest PAT, PMT,
 * and PCR data.</li>
 * <li>Builds an {@link M2tsInfo} with stream metadata and timing information.</li>
 * </ol>
 * <p>
 * <strong>Timing fields</strong><br>
 * ATS values in the {@code TP_extra_header} run at 27 MHz.<br>
 * PCR values in PCR-carrying TS packets also run at 27 MHz (base×300 + ext).
 */
@Slf4j
public class M2tsParser {
	/** Size of a Blu-ray source packet (4-byte header + 188-byte TS packet). */
	public static final int SOURCE_PACKET_SIZE = 192;

	/** Standard MPEG-2 TS sync byte. */
	private static final int SYNC_BYTE = 0x47;

	/** PAT (Program Association Table) PID. */
	private static final int PAT_PID = 0x0000;

	/** Maximum number of source packets to scan for PAT/PMT. */
	private static final int MAX_SCAN_PACKETS = 50_000;

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Parses the M2TS file at {@code path} and returns stream/timing metadata.
	 *
	 * @param path path to the {@code .m2ts} file
	 * @return populated {@link M2tsInfo}
	 * @throws IOException on I/O error
	 */
	public M2tsInfo parse(Path path) throws IOException {
		log.info("Parsing M2TS: {}", path);

		long fileSize = Files.size(path);
		long totalPackets = fileSize / SOURCE_PACKET_SIZE;

		M2tsInfo info = new M2tsInfo();
		info.setSourcePath(path.toAbsolutePath().toString());
		info.setTotalPackets(totalPackets);

		try (InputStream in = Files.newInputStream(path)) {
			scanPackets(in, info, (int) Math.min(totalPackets, MAX_SCAN_PACKETS));
		}

		log.info("M2TS scan complete: {} total packets, {} streams found", totalPackets,
				info.getStreams() != null ? info.getStreams().size() : 0);
		return info;
	}

	// -------------------------------------------------------------------------
	// Core packet scanner
	// -------------------------------------------------------------------------

	private void scanPackets(InputStream in, M2tsInfo info, int maxPackets) throws IOException {

		byte[] sp = new byte[SOURCE_PACKET_SIZE];

		// PIDs we're interested in, discovered progressively
		int pmtPid = -1;
		boolean pmtParsed = false;
		// Track PCR PID once PMT is parsed
		int pcrPid = -1;

		// Stream map (PID → info), built from PMT
		Map<Integer, M2tsStreamInfo> streamMap = new LinkedHashMap<>();

		long firstAts = -1;
		long ats27 = -1;
		long firstPcr = -1;
		long lastPcr = -1;

		for (int i = 0; i < maxPackets; i++) {

			int read = readFully(in, sp);
			if (read < SOURCE_PACKET_SIZE)
				break; // EOF

			// --- TP_extra_header: 30-bit ATS (bits 29-0), 2 copy-permission bits ---
			long atsRaw = (((long) (sp[0] & 0x3F)) << 24) | (((long) (sp[1] & 0xFF)) << 16)
					| (((long) (sp[2] & 0xFF)) << 8) | (((long) (sp[3] & 0xFF)));
			// ATS counter wraps at 2^30; scale to 27 MHz for external use
			ats27 = atsRaw; // already in 27 MHz ticks (modulo 2^30)

			if (firstAts < 0)
				firstAts = ats27;

			// --- TS packet starts at offset 4 ---
			if ((sp[4] & 0xFF) != SYNC_BYTE) {
				log.warn("Lost sync at source packet {}", i);
				continue;
			}

			int b1 = sp[5] & 0xFF;
			int b2 = sp[6] & 0xFF;

			boolean transportError = (b1 & 0x80) != 0;
			boolean payloadUnitStart = (b1 & 0x40) != 0;
			int pid = ((b1 & 0x1F) << 8) | b2;

			int b3 = sp[7] & 0xFF;
			int adaptationFieldControl = (b3 >> 4) & 0x03;
			// 01 = payload only, 10 = adaptation only, 11 = adaptation + payload

			if (transportError)
				continue;

			// ---------------------------------------------------------------
			// PAT
			// ---------------------------------------------------------------
			if (pid == PAT_PID && payloadUnitStart && pmtPid < 0) {
				pmtPid = parsePat(sp);
				if (pmtPid >= 0) {
					info.setPmtPid(pmtPid);
					log.debug("PAT found: PMT PID = 0x{}", Integer.toHexString(pmtPid));
				}
				continue;
			}

			// ---------------------------------------------------------------
			// PMT
			// ---------------------------------------------------------------
			if (pid == pmtPid && payloadUnitStart && !pmtParsed) {
				PmtParseResult pmt = parsePmt(sp);
				if (pmt != null) {
					pmtParsed = true;
					pcrPid = pmt.pcrPid;
					info.setPcrPid(pcrPid);
					streamMap = pmt.streams;
					log.debug("PMT parsed: PCR PID=0x{}, {} streams", Integer.toHexString(pcrPid), streamMap.size());
				}
				continue;
			}

			// ---------------------------------------------------------------
			// PCR
			// ---------------------------------------------------------------
			if (pid == pcrPid && (adaptationFieldControl == 2 || adaptationFieldControl == 3)) {
				long pcr = extractPcr(sp);
				if (pcr >= 0) {
					if (firstPcr < 0)
						firstPcr = pcr;
					lastPcr = pcr;
				}
			}

			// Stop scanning once we have full PMT and some PCR readings
			if (pmtParsed && firstPcr >= 0 && i > 500) {
				// Continue scanning to get a decent lastPcr sample — stop at max
			}
		}

		info.setFirstAts27MHz(firstAts);
		info.setLastAts27MHz(ats27);
		info.setFirstPcr27MHz(firstPcr);
		info.setLastPcr27MHz(lastPcr);
		info.setStreams(new ArrayList<>(streamMap.values()));
	}

	// -------------------------------------------------------------------------
	// PAT parser
	// -------------------------------------------------------------------------

	/**
	 * Extracts the PMT PID from a PAT (Program Association Table) TS packet. Returns -1 if parsing fails.
	 */
	private int parsePat(byte[] sp) {
		// TS payload starts at offset 4 + (possibly) adaptation field
		int tsOff = 4; // offset into sp[] of the TS packet
		int b3 = sp[tsOff + 3] & 0xFF;
		int adaptCtrl = (b3 >> 4) & 0x03;

		int payloadStart = tsOff + 4; // after 4-byte TS header
		if (adaptCtrl == 3) {
			int afLen = sp[payloadStart] & 0xFF;
			payloadStart += 1 + afLen;
		}

		// pointer_field
		int pointer = sp[payloadStart] & 0xFF;
		payloadStart += 1 + pointer;

		// Table header
		// table_id (1), section_syntax_indicator+flags (1), section_length (2)
		// transport_stream_id (2), version+current (1), section_number (1),
		// last_section_number (1)
		int tableId = sp[payloadStart] & 0xFF;
		if (tableId != 0x00)
			return -1; // not a PAT

		int sectionLength = ((sp[payloadStart + 1] & 0x0F) << 8) | (sp[payloadStart + 2] & 0xFF);
		// program loop starts at offset +8 from section start
		int loopStart = payloadStart + 8;
		int loopEnd = payloadStart + 3 + sectionLength - 4; // exclude 4-byte CRC

		for (int pos = loopStart; pos + 3 < loopEnd && pos + 3 < sp.length; pos += 4) {
			int programNumber = ((sp[pos] & 0xFF) << 8) | (sp[pos + 1] & 0xFF);
			int mapPid = ((sp[pos + 2] & 0x1F) << 8) | (sp[pos + 3] & 0xFF);
			if (programNumber != 0) {
				return mapPid; // return the first actual program's PMT PID
			}
		}
		return -1;
	}

	// -------------------------------------------------------------------------
	// PMT parser
	// -------------------------------------------------------------------------

	private static class PmtParseResult {

		int pcrPid;

		Map<Integer, M2tsStreamInfo> streams = new LinkedHashMap<>();

	}

	private static final int descriptor_tag_ISO_639_LANGUAGE = 0x0A;

	private static final int descriptor_tag_registration = 0x05;

	private static final String descriptor_tag_registration_HDMV = "HDMV";

	private static final String descriptor_tag_registration_AC_3 = "AC-3";

	private static final int descriptor_tag_registration_HDMV_mystery_byte = 0xFF;// this
																					// thing
																					// seems
																					// to
																					// be
																					// FF
																					// all
																					// the
																					// time,
																					// even
																					// for
																					// audio

	/**
	 * Parses a PMT (Program Map Table) TS packet. Returns null if the packet does not contain a recognisable PMT.
	 */
	private PmtParseResult parsePmt(byte[] sp) {
		int tsOff = 4;
		int b3 = sp[tsOff + 3] & 0xFF;
		int adaptCtrl = (b3 >> 4) & 0x03;

		int payloadStart = tsOff + 4;
		if (adaptCtrl == 3) {
			int afLen = sp[payloadStart] & 0xFF;
			payloadStart += 1 + afLen;
		}

		int pointer = sp[payloadStart] & 0xFF;
		payloadStart += 1 + pointer;

		int tableId = sp[payloadStart] & 0xFF;
		if (tableId != 0x02)
			return null; // not a PMT

		int sectionLength = ((sp[payloadStart + 1] & 0x0F) << 8) | (sp[payloadStart + 2] & 0xFF);
		int pcrPid = ((sp[payloadStart + 8] & 0x1F) << 8) | (sp[payloadStart + 9] & 0xFF);
		int programInfoLength = ((sp[payloadStart + 10] & 0x0F) << 8) | (sp[payloadStart + 11] & 0xFF);

		// ES loop start
		int esStart = payloadStart + 12 + programInfoLength;
		int esEnd = payloadStart + 3 + sectionLength - 4; // exclude CRC

		PmtParseResult result = new PmtParseResult();
		result.pcrPid = pcrPid;

		for (int pos = esStart; pos + 4 < esEnd && pos + 4 < sp.length;) {
			int streamType = sp[pos] & 0xFF;
			int esPid = ((sp[pos + 1] & 0x1F) << 8) | (sp[pos + 2] & 0xFF);
			int esInfoLen = ((sp[pos + 3] & 0x0F) << 8) | (sp[pos + 4] & 0xFF);

			M2tsStreamInfo streamInfo = new M2tsStreamInfo();
			streamInfo.setPid(esPid);
			streamInfo.setStreamTypeByte(streamType);
			streamInfo.setCodingType(mapStreamType(streamType));

			// Parse descriptors in ES info to find language
			int descPos = pos + 5;
			int descEnd = pos + 5 + esInfoLen;
			log.debug("Stream " + esPid + ". esInfoLen " + esInfoLen);
			if (esInfoLen > 0) {
				log.debug("descriptors hex bytes "
						+ StringUtils.bytesToHex(sp, descPos, Math.min(esInfoLen, sp.length - descPos)));
			}
			while (descPos + 1 < descEnd && descPos + 1 < sp.length) {
				int descTag = sp[descPos] & 0xFF;
				int descLen = sp[descPos + 1] & 0xFF;
				if (descPos + 2 + descLen > sp.length)
					break; // malformed descriptor
				switch (descTag) {
				case descriptor_tag_registration -> {
					// Registration descriptor (e.g. "HDMV" for Blu-ray)
					if (descLen >= 4) {
						String reg = new String(sp, descPos + 2, 4, java.nio.charset.StandardCharsets.US_ASCII);
						log.debug("  Registration descriptor: {}", reg);
						if (!reg.isBlank())
							streamInfo.setRegistration(reg);
						if (descriptor_tag_registration_HDMV.equals(reg)) {
							if (descLen == 8) {
								int category = sp[descPos + 6] & 0xFF;
								if (category == descriptor_tag_registration_HDMV_mystery_byte) {
									int codec = sp[descPos + 7] & 0xFF;
									if (codec != streamType) {
										log.warn(
												"  HDMV registration stream type 0x{} does not match PMT stream type 0x{}",
												Integer.toHexString(codec), Integer.toHexString(streamType));
									}
									int todo = ((sp[descPos + 8] & 0xFF) << 8) | (sp[descPos + 9] & 0xFF);
									if (streamInfo.getCodingType().isVideo()) {
										int vfr = sp[descPos + 8] & 0xFF;
										streamInfo.setVfr(vfr);
									} else if (todo == 0x31FF && codec == StreamCodingType.LPCM.getCodingTypeByte()) {
										streamInfo.setBitrateKbps(2304);// based upon
																		// one LPCM
																		// example
										streamInfo.setSampleRateHz(48000);
										streamInfo.setChannels(2);
									} else if (todo == 0x317F && codec == StreamCodingType.LPCM.getCodingTypeByte()) {
										streamInfo.setBitrateKbps(1536);// based upon
																		// one 16-bit
																		// LPCM
																		// example
										streamInfo.setSampleRateHz(48000);
										streamInfo.setChannels(2);
									} else if (todo == 0x617F && codec == StreamCodingType.LPCM.getCodingTypeByte()) {
										streamInfo.setBitrateKbps(4608);// based upon
																		// one LPCM
																		// example
									} else {
										log.warn(
												"  TODO developers: reverse engineer that. HDMV registration has some original bytes: 0x{}",
												Integer.toHexString(todo));
									}
								}
							} else {
								log.warn("  Unexpected registration descriptor length {} for HDMV stream", descLen);
							}
							// found on one track 04 64 00 29 BF after the 8-byte HDMV
							// no idea what that means
						} else if (descriptor_tag_registration_AC_3.equals(reg)) {
							if (descLen == 4 && esInfoLen >= 12) {
								int audioType = sp[descPos + 6] & 0xFF;
								if (audioType != StreamCodingType.DOLBY_AC3.getCodingTypeByte()) {
									log.warn("  AC-3 registration stream type 0x{} does not match expected AC3",
											Integer.toHexString(audioType));
								}
							}
							int subLength = sp[descPos + 7] & 0xFF;
							if (subLength == 4) {
								int audioCodingType = sp[descPos + 8] & 0xFF;
								// IA suggests that:
								// 0x06 AC‑3 (Dolby Digital) primary audio
								// 0x0A E‑AC‑3 (Dolby Digital Plus) primary audio
								// 0x0B AC‑3 secondary audio
								// 0x0E E‑AC‑3 secondary audio
								// 0x82 DTS primary audio
								// 0x83 DTS‑HD primary audio
								// 0x84 DTS‑HD Master Audio
								// 0x80 LPCM primary audio
								// 0x81 LPCM secondary audio
								// but i've seen E-AC-3 with code 0x06 on some
								// blu-rays...
								// 0x08 seems to be AC-3 too. So confidence is low on
								// that mapping.
								BitrateCode bitrateCode = BitrateCode.fromCode(sp[descPos + 9] & 0xFF);
								if (bitrateCode == null) {
									log.warn("  Unknown bitrate code 0x{} in AC-3 registration descriptor",
											Integer.toHexString(sp[descPos + 9] & 0xFF));
								} else {
									streamInfo.setBitrateKbps(bitrateCode.getBitrateKbps());
									streamInfo.setChannels(bitrateCode.getChannels());
								}
								// no idea what that data means
								// found on some blu-rays:
								// 06 35 04 00 : AC-3 320 kbps, 2 channels, 48 kHz
								// 06 40 0E 00 : E-AC-3 896 kbps, 8 channels, 48 kHz
								// 06 29 04 00 : AC-3 192 kbps, 2 channels, 48 kHz .
								// 08 29 04 00 too
								int todooo = sp[descPos + 10] & 0xFF;
								// 0x04 seems to be 2, and 0X0E seems to be 8
								// channels, but that is based on very limited data
								// and may be wrong.
							}

						}
					}
				}
				case descriptor_tag_ISO_639_LANGUAGE -> {
					// ISO 639 language descriptor
					if (descLen >= 3) {
						String lang = new String(sp, descPos + 2, 3, java.nio.charset.StandardCharsets.US_ASCII).trim();
						log.debug("  Language descriptor: {}", lang);
						if (!lang.isBlank())
							streamInfo.setLanguage(lang);
					}
				}
				default -> {
					// Other descriptors can be handled here if needed
				}
				}
				descPos += 2 + descLen;
			}

			result.streams.put(esPid, streamInfo);
			pos += 5 + esInfoLen;
		}

		return result;
	}

	// -------------------------------------------------------------------------
	// PCR extraction
	// -------------------------------------------------------------------------

	/**
	 * Extracts the 27 MHz PCR value from a TS packet's adaptation field. Returns -1 if no PCR is present.
	 */
	public static long extractPcr(byte[] sp) {
		final int tsOff = 4;
		int b3 = sp[tsOff + 3] & 0xFF;
		int adaptCtrl = (b3 >> 4) & 0x03;
		if (adaptCtrl != 2 && adaptCtrl != 3)
			return -1;

		final int afOff = tsOff + 4;
		int afLen = sp[afOff] & 0xFF;
		if (afLen < 7)
			return -1; // need at least 7 bytes for adaptation field with PCR

		int afFlags = sp[afOff + 1] & 0xFF;
		if ((afFlags & 0x10) == 0)
			return -1; // PCR_flag not set

		// PCR base (33 bits) and PCR extension (9 bits) at afOff+2..afOff+7
		long pcrBase = (((long) (sp[afOff + 2] & 0xFF)) << 25) | (((long) (sp[afOff + 3] & 0xFF)) << 17)
				| (((long) (sp[afOff + 4] & 0xFF)) << 9) | (((long) (sp[afOff + 5] & 0xFF)) << 1)
				| ((sp[afOff + 6] & 0xFF) >> 7);
		long pcrExt = ((sp[afOff + 6] & 0x01L) << 8) | (sp[afOff + 7] & 0xFF);
		return pcrBase * 300 + pcrExt; // 27 MHz
	}

	// -------------------------------------------------------------------------
	// Stream type mapping
	// -------------------------------------------------------------------------

	/**
	 * Maps an ISO 13818-1 stream_type byte to a Blu-ray {@link StreamCodingType}. Returns null for unknown stream
	 * types.
	 */
	public static StreamCodingType mapStreamType(int streamTypeByte) {
		return switch (streamTypeByte) {
		case 0x02 -> StreamCodingType.MPEG2_VIDEO;
		case 0x1B -> StreamCodingType.H264_AVC;
		case 0x24 -> StreamCodingType.H265_HEVC;
		case 0xEA -> StreamCodingType.VC1;
		case 0x80 -> StreamCodingType.LPCM;
		case 0x81 -> StreamCodingType.DOLBY_AC3;
		case 0x82 -> StreamCodingType.DTS;
		case 0x83 -> StreamCodingType.DOLBY_TRUEHD;
		case 0x84 -> StreamCodingType.DOLBY_AC3_PLUS;
		case 0x85 -> StreamCodingType.DTS_HD;
		case 0x86 -> StreamCodingType.DTS_HD_MASTER_AUDIO;
		case 0x90 -> StreamCodingType.PRESENTATION_GRAPHICS;
		case 0x91 -> StreamCodingType.INTERACTIVE_GRAPHICS;
		case 0x92 -> StreamCodingType.TEXT_SUBTITLE;
		default -> null;
		};
	}

	// -------------------------------------------------------------------------
	// Utility
	// -------------------------------------------------------------------------

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
