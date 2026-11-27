/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/M2tsParser.java' is part of BRTS.
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

package org.brts.common.m2ts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/** PID of the Selection Information Table (SIT) on Blu-ray/ARIB discs. */
	private static final int SIT_PID = 0x001F;

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

		// PIDs we're interested in, discovered from the PAT
		boolean patParsed = false;
		Set<Integer> pmtPids = new LinkedHashSet<>();
		Set<Integer> parsedPmtPids = new HashSet<>();
		Map<Integer, PmtSectionAssembler> pmtSections = new LinkedHashMap<>();
		int sitPid = -1;
		boolean sitParsed = false;
		// Track PCR PID once the first PMT is parsed
		int pcrPid = -1;

		// Stream map (PID → info), built from all parsed PMTs
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
				pmtSections.clear();
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

			if (transportError) {
				pmtSections.remove(pid);
				continue;
			}

			// ---------------------------------------------------------------
			// PAT
			// ---------------------------------------------------------------
			if (pid == PAT_PID && payloadUnitStart && !patParsed) {
				PatParseResult pat = parsePat(sp);
				if (pat != null) {
					patParsed = true;
					pmtPids.addAll(pat.programs.values());
					sitPid = pat.sitPid;
					if (!pmtPids.isEmpty()) {
						info.setPmtPid(pmtPids.iterator().next());
					}
					info.setProgramPidMap(pat.programs);
					if (sitPid >= 0) {
						info.setSitPid(sitPid);
						log.debug("PAT found: {} program(s), SIT PID=0x{}", pmtPids.size(),
								Integer.toHexString(sitPid));
					} else {
						log.debug("PAT found: {} program(s)", pmtPids.size());
					}
				}
				continue;
			}

			// ---------------------------------------------------------------
			// PMT (any program found in the PAT)
			// ---------------------------------------------------------------
			if (pmtPids.contains(pid) && !parsedPmtPids.contains(pid)) {
				PmtSectionAssembler assembler = pmtSections.computeIfAbsent(pid, key -> new PmtSectionAssembler());
				for (byte[] section : assembler.accept(sp)) {
					PmtParseResult pmt = parsePmt(section);
					if (pmt != null) {
						parsedPmtPids.add(pid);
						pmtSections.remove(pid);
						if (pcrPid < 0) {
							pcrPid = pmt.pcrPid;
							info.setPcrPid(pcrPid);
						}
						streamMap.putAll(pmt.streams);
						log.debug("PMT 0x{} parsed: PCR PID=0x{}, {} streams", Integer.toHexString(pid),
								Integer.toHexString(pmt.pcrPid), pmt.streams.size());
						break;
					}
				}
				continue;
			}

			// ---------------------------------------------------------------
			// SIT
			// ---------------------------------------------------------------
			if (sitPid >= 0 && pid == sitPid && payloadUnitStart && !sitParsed) {
				parseSit(sp);
				sitParsed = true;
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

			// Stop scanning once we have all PMTs, SIT, and some PCR readings
			// if (patParsed && parsedPmtPids.containsAll(pmtPids) && (sitPid < 0 || sitParsed) && firstPcr >= 0
			// && i > 500) {
			// Continue scanning to get a decent lastPcr sample — stop at max
			// }
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

	private static class PatParseResult {

		/** All non-NIT programs: program_number -> PMT PID. */
		Map<Integer, Integer> programs = new LinkedHashMap<>();

		/** SIT PID if a PAT entry pointed to {@link M2tsParser#SIT_PID}, otherwise -1. */
		int sitPid = -1;

	}

	/**
	 * Extracts all program entries from a PAT (Program Association Table) TS packet. Returns {@code null} if the packet
	 * is not a valid PAT.
	 */
	private PatParseResult parsePat(byte[] sp) {
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
		int tableId = sp[payloadStart] & 0xFF;
		if (tableId != 0x00)
			return null; // not a PAT

		int sectionLength = ((sp[payloadStart + 1] & 0x0F) << 8) | (sp[payloadStart + 2] & 0xFF);
		// program loop starts at offset +8 from section start
		int loopStart = payloadStart + 8;
		int loopEnd = payloadStart + 3 + sectionLength - 4; // exclude 4-byte CRC

		PatParseResult result = new PatParseResult();
		for (int pos = loopStart; pos + 3 < loopEnd && pos + 3 < sp.length; pos += 4) {
			int programNumber = ((sp[pos] & 0xFF) << 8) | (sp[pos + 1] & 0xFF);
			int mapPid = ((sp[pos + 2] & 0x1F) << 8) | (sp[pos + 3] & 0xFF);
			// if (programNumber == 0) {
			// program 0 is the NIT PID — not a PMT
			// continue;
			// }
			if (mapPid == SIT_PID) {
				result.sitPid = mapPid;
			} else {
				result.programs.put(programNumber, mapPid);
			}
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// PMT parser
	// -------------------------------------------------------------------------

	/** Reassembles one PID's PSI sections, excluding TS headers, pointer fields and stuffing. */
	private static class PmtSectionAssembler {
		// ISO/IEC 13818-1 limits PMT section_length to 1021 bytes, plus its three-byte header.
		private final byte[] section = new byte[1024];
		private int size;
		private int expected = 3;
		private int continuity = -1;
		private byte[] previousPacket;

		private void discardSection() {
			size = 0;
			expected = 3;
		}

		private void reset() {
			discardSection();
			continuity = -1;
			previousPacket = null;
		}

		List<byte[]> accept(byte[] packet) {
			List<byte[]> completed = new ArrayList<>();
			int control = (packet[7] >> 4) & 3;
			if (control == 0 || (packet[7] & 0xC0) != 0) {
				reset();
				return completed;
			}
			int offset = 8;
			if (control == 2 || control == 3) {
				int length = packet[offset] & 0xFF;
				if (offset + 1 + length > packet.length) {
					reset();
					return completed;
				}
				if (length > 0 && (packet[offset + 1] & 0x80) != 0)
					reset(); // discontinuity_indicator also applies to adaptation-only packets
				offset += 1 + length;
			}
			if (control == 2)
				return completed; // no payload, so do not advance the payload continuity counter
			if (offset >= packet.length) {
				reset();
				return completed;
			}

			int counter = packet[7] & 0x0F;
			if (counter == continuity && previousPacket != null
					&& Arrays.equals(packet, 4, packet.length, previousPacket, 4, previousPacket.length))
				return completed; // retransmission; ATS may differ
			if (continuity >= 0 && counter != ((continuity + 1) & 0x0F))
				discardSection();
			continuity = counter;
			previousPacket = packet.clone();

			if ((packet[5] & 0x40) != 0) {
				int pointer = packet[offset++] & 0xFF;
				int start = offset + pointer;
				if (start >= packet.length) {
					discardSection();
					return completed;
				}
				if (size > 0)
					append(packet, offset, start, completed);
				discardSection(); // a new section starts here even if the old one was incomplete
				offset = start;
				while (offset < packet.length && (packet[offset] & 0xFF) != 0xFF)
					offset = append(packet, offset, packet.length, completed);
			} else if (size > 0) {
				append(packet, offset, packet.length, completed);
			}
			return completed;
		}

		/** Consumes at most one section; only PUSI establishes where further sections can begin. */
		private int append(byte[] packet, int offset, int end, List<byte[]> completed) {
			while (offset < end) {
				int count = Math.min(end - offset, expected - size);
				System.arraycopy(packet, offset, section, size, count);
				size += count;
				offset += count;
				if (size == 3 && expected == 3) {
					int length = ((section[1] & 0x0F) << 8) | (section[2] & 0xFF);
					if (length < 13 || length > 1021) {
						discardSection();
						return end;
					}
					expected = 3 + length;
				}
				if (size == expected) {
					completed.add(Arrays.copyOf(section, size));
					discardSection();
					return offset;
				}
			}
			return offset;
		}
	}

	private static class PmtParseResult {

		int pcrPid;

		Map<Integer, M2tsStreamInfo> streams = new LinkedHashMap<>();

	}

	private static final int descriptor_tag_ISO_639_LANGUAGE = 0x0A;

	private static final int descriptor_tag_registration = 0x05;

	private static final int descriptor_tag_ATSC_AC_3_AUDIO = 0x81;

	private static final String descriptor_tag_registration_HDMV = "HDMV";

	private static final String descriptor_tag_registration_AC_3 = "AC-3";

	// this thing seems to be FF all the time, even for audio
	private static final int descriptor_tag_registration_HDMV_mystery_byte = 0xFF;

	/**
	 * Parses a complete PMT section, starting at table_id. Returns null for malformed sections, never partial streams.
	 */
	private PmtParseResult parsePmt(byte[] sp) {
		if (sp.length < 16)
			return null;
		int payloadStart = 0;
		int tableId = sp[payloadStart] & 0xFF;
		if (tableId != 0x02)
			return null; // not a PMT

		int sectionLength = ((sp[payloadStart + 1] & 0x0F) << 8) | (sp[payloadStart + 2] & 0xFF);
		int pcrPid = ((sp[payloadStart + 8] & 0x1F) << 8) | (sp[payloadStart + 9] & 0xFF);
		int programInfoLength = ((sp[payloadStart + 10] & 0x0F) << 8) | (sp[payloadStart + 11] & 0xFF);

		// ES loop start
		int esStart = payloadStart + 12 + programInfoLength;
		int esEnd = payloadStart + 3 + sectionLength - 4; // exclude CRC
		if (sectionLength < 13 || sectionLength > 1021 || 3 + sectionLength != sp.length || esStart > esEnd) {
			log.warn("Invalid PMT section/program info length: {}/{}", sectionLength, programInfoLength);
			return null;
		}

		PmtParseResult result = new PmtParseResult();
		result.pcrPid = pcrPid;

		for (int pos = esStart; pos < esEnd;) {
			if (pos + 5 > esEnd) {
				log.warn("Truncated PMT ES header at offset {}", pos);
				return null;
			}
			int streamType = sp[pos] & 0xFF;
			int esPid = ((sp[pos + 1] & 0x1F) << 8) | (sp[pos + 2] & 0xFF);
			int esInfoLen = ((sp[pos + 3] & 0x0F) << 8) | (sp[pos + 4] & 0xFF);
			int descEnd = pos + 5 + esInfoLen;
			if (descEnd > esEnd || descEnd > sp.length) {
				log.warn("Truncated PMT ES info for PID {}: length {} at offset {}", esPid, esInfoLen, pos);
				return null;
			}

			M2tsStreamInfo streamInfo = new M2tsStreamInfo();
			streamInfo.setPid(esPid);
			streamInfo.setStreamTypeByte(streamType);
			streamInfo.setCodingType(mapStreamType(streamType));

			// Parse each independent descriptor within this ES info region.
			int descPos = pos + 5;
			log.debug("Stream {}. esInfoLen {}", esPid, esInfoLen);
			if (esInfoLen > 0) {
				log.debug("descriptors hex bytes {}", StringUtils.bytesToHex(sp, descPos, esInfoLen));
			}
			while (descPos < descEnd) {
				if (descPos + 2 > descEnd) {
					log.warn("Truncated descriptor header for PID {} at offset {}", esPid, descPos);
					return null;
				}
				int descTag = sp[descPos] & 0xFF;
				int descLen = sp[descPos + 1] & 0xFF;
				if (descPos + 2 + descLen > descEnd) {
					log.warn("Truncated descriptor 0x{} for PID {} at offset {}: length {}",
							Integer.toHexString(descTag), esPid, descPos, descLen);
					return null;
				}
				switch (descTag) {
				case descriptor_tag_registration -> {
					// Registration descriptor (e.g. "HDMV" for Blu-ray)
					if (descLen >= 4) {
						String reg = new String(sp, descPos + 2, 4, StandardCharsets.US_ASCII);
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
										log.trace("  Video VFR value: {}", vfr);
										streamInfo.setVfr(vfr);
									} else if (todo == 0x31FF && codec == StreamCodingType.LPCM.getCodingTypeByte()) {
										// based upon one LPCM example
										streamInfo.setBitrateKbps(2304);
										streamInfo.setSampleRateHz(48000);
										streamInfo.setChannels(2);
									} else if (todo == 0x317F && codec == StreamCodingType.LPCM.getCodingTypeByte()) {
										// based upon one 16-bit LPCM example
										streamInfo.setBitrateKbps(1536);
										streamInfo.setSampleRateHz(48000);
										streamInfo.setChannels(2);
									} else if (todo == 0x617F && codec == StreamCodingType.LPCM.getCodingTypeByte()) {
										// based upon one LPCM example
										streamInfo.setBitrateKbps(4608);
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
							// Registration ends here; tag 0x81 is a separate descriptor.
							if (descLen != 4) {
								log.warn("Unexpected AC-3 registration descriptor length {} for PID {}", descLen,
										esPid);
							}
						}
					}
				}
				case descriptor_tag_ATSC_AC_3_AUDIO ->
					parseAtscAc3AudioDescriptor(sp, descPos + 2, descLen, streamInfo);
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
					log.debug("  Unknown descriptor with tag 0x{} and length {}", Integer.toHexString(descTag),
							descLen);
				}
				}
				descPos += 2 + descLen;
			}

			result.streams.put(esPid, streamInfo);
			pos += 5 + esInfoLen;
		}

		return result;
	}

	/** Decodes the fixed three-byte ATSC A/52 AC-3 audio stream descriptor header. */
	private void parseAtscAc3AudioDescriptor(byte[] data, int offset, int length, M2tsStreamInfo streamInfo) {
		if (length < 3) {
			log.warn("ATSC AC-3 audio descriptor too short for PID {}: {} bytes", streamInfo.getPid(), length);
			return;
		}
		int sampleRateCode = (data[offset] & 0xE0) >> 5;
		int bsid = data[offset] & 0x1F;
		int bitRateCode = (data[offset + 1] & 0xFC) >> 2;
		int surroundMode = data[offset + 1] & 0x03;
		int bsmod = (data[offset + 2] & 0xE0) >> 5;
		int numChannels = (data[offset + 2] & 0x1E) >> 1;
		boolean fullService = (data[offset + 2] & 1) != 0;

		Integer sampleRate = switch (sampleRateCode) {
		case 0 -> 48000;
		case 1 -> 44100;
		case 2 -> 32000;
		default -> null; // Reserved or a choice of sample rates, not an exact value.
		};
		if (sampleRate != null)
			streamInfo.setSampleRateHz(sampleRate);

		// Bit 5 denotes an upper bound, which must not be reported as an exact bitrate.
		BitrateCode bitrate = BitrateCode.fromCode(bitRateCode);
		if (bitrate != null)
			streamInfo.setBitrateKbps(bitrate.getBitrateKbps());

		// These describe full-bandwidth channels; no LFE presence is signalled here.
		Integer channels = switch (numChannels) {
		case 0, 2 -> 2; // Dual mono or stereo.
		case 1, 8 -> 1;
		case 3, 4 -> 3;
		case 5, 6 -> 4;
		case 7 -> 5;
		default -> null; // Upper bounds (9..13) or reserved (14..15).
		};
		if (channels != null)
			streamInfo.setChannels(channels);

		log.debug(
				"ATSC AC-3 PID {}: sample_rate_code={}, bsid={}, bit_rate_code={}, surround_mode={}, "
						+ "bsmod={}, num_channels={}, full_service={}",
				streamInfo.getPid(), sampleRateCode, bsid, bitRateCode, surroundMode, bsmod, numChannels, fullService);
		// Optional trailing fields start with deprecated langcod; they are not audio format fields.
	}

	// -------------------------------------------------------------------------
	// SIT parser
	// -------------------------------------------------------------------------

	/**
	 * Parses a SIT (Selection Information Table, table_id 0x7F) TS packet and logs its service entries.
	 */
	private void parseSit(byte[] sp) {
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
		if (tableId != 0x7F) {
			log.warn("SIT: unexpected table_id 0x{}", Integer.toHexString(tableId));
			return;
		}

		int sectionLength = ((sp[payloadStart + 1] & 0x0F) << 8) | (sp[payloadStart + 2] & 0xFF);
		// bytes 3-4: reserved (0xFFFF)
		// bytes 5-7: version/current_next, section_number, last_section_number
		int selectionInfoLength = ((sp[payloadStart + 8] & 0x0F) << 8) | (sp[payloadStart + 9] & 0xFF);

		int serviceLoopStart = payloadStart + 10 + selectionInfoLength;
		int serviceLoopEnd = payloadStart + 3 + sectionLength - 4; // exclude CRC

		log.debug("SIT parsed: sectionLength={}, selectionInfoLength={}", sectionLength, selectionInfoLength);

		for (int pos = serviceLoopStart; pos + 3 < serviceLoopEnd && pos + 3 < sp.length;) {
			int serviceId = ((sp[pos] & 0xFF) << 8) | (sp[pos + 1] & 0xFF);
			int runningStatus = (sp[pos + 2] >> 4) & 0x07;
			int serviceLoopLength = ((sp[pos + 2] & 0x0F) << 8) | (sp[pos + 3] & 0xFF);
			log.debug("  SIT service: id=0x{}, running_status={}", Integer.toHexString(serviceId), runningStatus);
			pos += 4 + serviceLoopLength;
		}
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
		return StreamCodingType.fromByte(streamTypeByte, false);
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
