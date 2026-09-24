package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/PgsStreamLoader.java' is part of BRTS.
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

import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.igs.RleConverter;
import org.brts.lowlevel.igs.model.CompositionObject;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.pgs.PgsM2tsExtractor;
import org.brts.lowlevel.pgs.PgsM2tsExtractor.EsData;
import org.brts.lowlevel.pgs.PgsParser;
import org.brts.lowlevel.pgs.PgsSupReader;
import org.brts.lowlevel.pgs.model.PgsCompositionSegment;
import org.brts.lowlevel.pgs.model.PgsDisplaySet;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads a standalone PGS ({@code .sup}) file or an M2TS file into a {@link PgsPreviewModel}, decoding every subtitle
 * bitmap up-front so that navigation between items is instantaneous.
 * <p>
 * This is the "middle-level" bridge that combines low-level M2TS parsing, PGS parsing, and RLE decoding into a single
 * convenient entry point, analogous to {@link DisplaySetLoader} for IGS.
 */
@Slf4j
public class PgsStreamLoader {

	private final RleConverter rleConverter = new RleConverter();

	/**
	 * Loads {@code input}, selecting the PGS stream by index or PID when {@code input} is an M2TS file.
	 *
	 * @param input   path to a standalone {@code .sup} file or an {@code .m2ts} file
	 * @param pgIndex zero-based index among PGS streams found in the M2TS (ignored for standalone input); mutually
	 *                exclusive with {@code pid}
	 * @param pid     explicit PID of the PGS stream to select (ignored for standalone input); mutually exclusive with
	 *                {@code pgIndex}
	 * @return a fully populated preview model
	 * @throws IOException              if parsing fails or no PGS stream is found
	 * @throws IllegalArgumentException if both {@code pgIndex} and {@code pid} are given
	 */
	public PgsPreviewModel load(Path input, Integer pgIndex, Integer pid) throws IOException {
		if (pgIndex != null && pid != null) {
			throw new IllegalArgumentException("Specify either --pg-index or --pid, not both.");
		}

		if (isM2ts(input)) {
			return loadFromM2ts(input, pgIndex, pid);
		}
		if (pgIndex != null || pid != null) {
			throw new IllegalArgumentException(
					"--pg-index and --pid only apply to M2TS input; " + input + " is a standalone PGS file.");
		}
		return loadFromSup(input);
	}

	// -------------------------------------------------------------------------
	// Standalone .sup input
	// -------------------------------------------------------------------------

	private PgsPreviewModel loadFromSup(Path supFile) throws IOException {
		log.info("Loading standalone PGS file: {}", supFile);
		List<PgsDisplaySet> displaySets = PgsParser.parseGroupAndReassemble(PgsSupReader.parseSegments(supFile));
		if (displaySets.isEmpty()) {
			throw new IOException("No PGS display sets found in " + supFile);
		}

		PgsPreviewModel model = buildModel(displaySets);
		model.setBackgroundMode(PgsPreviewModel.BackgroundMode.CHECKERBOARD);
		return model;
	}

	// -------------------------------------------------------------------------
	// M2TS input
	// -------------------------------------------------------------------------

	private PgsPreviewModel loadFromM2ts(Path m2tsFile, Integer pgIndex, Integer pid) throws IOException {
		log.info("Loading PGS from M2TS: {}", m2tsFile);

		M2tsInfo info = new M2tsParser().parse(m2tsFile);
		List<M2tsStreamInfo> pgsStreams = PgsM2tsExtractor.listPgsStreams(info);
		if (pgsStreams.isEmpty()) {
			throw new IOException("No Presentation Graphics stream found in " + m2tsFile);
		}

		M2tsStreamInfo selected = selectPgsStream(pgsStreams, pgIndex, pid, m2tsFile);
		log.info("Using PGS stream at PID 0x{}", Integer.toHexString(selected.getPid()));

		EsData esData = PgsM2tsExtractor.demuxToMemory(m2tsFile, info, selected.getPid());
		List<org.brts.lowlevel.igs.model.IgsRawSegment> segments = PgsM2tsExtractor.parseSegments(esData.esBytes(),
				esData.boundaries());
		List<PgsDisplaySet> displaySets = PgsParser.parseGroupAndReassemble(segments);
		if (displaySets.isEmpty()) {
			throw new IOException("No PGS display sets found in " + m2tsFile);
		}

		PgsPreviewModel model = buildModel(displaySets);

		boolean hasVideo = info.getStreams().stream().anyMatch(s -> isVideo(s.getCodingType()));
		if (hasVideo) {
			model.setBackgroundMode(PgsPreviewModel.BackgroundMode.VIDEO_SNAPSHOT);
			model.setVideoSource(m2tsFile);
		} else {
			log.warn("No video stream found in {}, falling back to checkerboard background", m2tsFile);
			model.setBackgroundMode(PgsPreviewModel.BackgroundMode.CHECKERBOARD);
		}
		return model;
	}

	private M2tsStreamInfo selectPgsStream(List<M2tsStreamInfo> pgsStreams, Integer pgIndex, Integer pid, Path m2tsFile)
			throws IOException {
		if (pid != null) {
			return pgsStreams.stream().filter(s -> s.getPid() == pid).findFirst().orElseThrow(
					() -> new IOException("No Presentation Graphics stream with PID 0x" + Integer.toHexString(pid)
							+ " in " + m2tsFile + ". Available PIDs: " + pidList(pgsStreams)));
		}
		int index = pgIndex != null ? pgIndex : 0;
		if (index < 0 || index >= pgsStreams.size()) {
			throw new IOException("Requested --pg-index " + index + " but only " + pgsStreams.size()
					+ " Presentation Graphics stream(s) found in " + m2tsFile + ". Available PIDs: "
					+ pidList(pgsStreams));
		}
		return pgsStreams.get(index);
	}

	private String pidList(List<M2tsStreamInfo> streams) {
		return streams.stream().map(s -> "0x" + Integer.toHexString(s.getPid()))
				.collect(java.util.stream.Collectors.joining(", "));
	}

	private boolean isVideo(StreamCodingType codingType) {
		return codingType == StreamCodingType.MPEG2_VIDEO || codingType == StreamCodingType.H264_AVC
				|| codingType == StreamCodingType.H265_HEVC || codingType == StreamCodingType.VC1;
	}

	// -------------------------------------------------------------------------
	// Common: display sets → model
	// -------------------------------------------------------------------------

	private PgsPreviewModel buildModel(List<PgsDisplaySet> displaySets) {
		PgsPreviewModel model = new PgsPreviewModel();

		PgsDisplaySet first = displaySets.get(0);
		if (first.getCompositionSegment() != null) {
			model.setVideoDescriptor(first.getCompositionSegment().getVideoDescriptor());
		}

		PgsSubtitleItem pendingItem = null;
		for (int i = 0; i < displaySets.size(); i++) {
			PgsDisplaySet ds = displaySets.get(i);
			PgsCompositionSegment pcs = ds.getCompositionSegment();
			if (pcs == null) {
				continue;
			}

			if (pcs.getCompositionObjects().isEmpty()) {
				// A "clear" display set: closes the previously shown item.
				if (pendingItem != null) {
					pendingItem.setHidePtsTicks(pcs.getPts());
					pendingItem = null;
				}
				continue;
			}

			PgsSubtitleItem item = new PgsSubtitleItem();
			item.setIndex(model.getItems().size());
			item.setShowPtsTicks(pcs.getPts());
			item.setRenderedObjects(decodeObjects(ds, pcs));
			model.getItems().add(item);
			pendingItem = item;
		}

		// BD-authored M2TS clips do not necessarily start their PTS numbering at zero; remember the clip's own
		// baseline so displayed timestamps can be shown relative to it (video seeking still uses the raw PTS).
		model.setBasePtsTicks(model.getItems().stream().mapToLong(PgsSubtitleItem::getShowPtsTicks).min().orElse(0L));

		log.info("Parsed {} display sets into {} navigable subtitle item(s)", displaySets.size(),
				model.getItems().size());
		return model;
	}

	private List<PgsSubtitleItem.RenderedObject> decodeObjects(PgsDisplaySet ds, PgsCompositionSegment pcs) {
		Map<Integer, IgsPalette> palettesById = new HashMap<>();
		for (IgsPalette p : ds.getPalettes()) {
			palettesById.put(p.getId(), p);
		}
		IgsPalette palette = palettesById.get(pcs.getPaletteIdRef());

		Map<Integer, IgsObject> objectsById = new HashMap<>();
		for (IgsObject o : ds.getObjects()) {
			objectsById.put(o.getId(), o);
		}

		List<PgsSubtitleItem.RenderedObject> rendered = new java.util.ArrayList<>();
		for (CompositionObject co : pcs.getCompositionObjects()) {
			IgsObject obj = objectsById.get(co.getObjectIdRef());
			if (obj == null || palette == null) {
				log.warn("Missing object 0x{} or palette 0x{} for composition object, skipping",
						Integer.toHexString(co.getObjectIdRef()), Integer.toHexString(pcs.getPaletteIdRef()));
				continue;
			}
			BufferedImage image = rleConverter.decodeRle(obj.getRleData(), obj.getWidth(), obj.getHeight(), palette);
			rendered.add(new PgsSubtitleItem.RenderedObject(co, image));
		}
		return rendered;
	}

	// -------------------------------------------------------------------------
	// Input type sniffing
	// -------------------------------------------------------------------------

	/**
	 * Sniffs the input file: a standalone {@code .sup} starts with the {@code "PG"} magic, while an M2TS source packet
	 * carries the {@code 0x47} sync byte at offset 4 (after the 4-byte TP_extra_header).
	 */
	private boolean isM2ts(Path input) throws IOException {
		byte[] head = new byte[8];
		try (InputStream in = Files.newInputStream(input)) {
			int read = in.readNBytes(head, 0, head.length);
			if (read < 8) {
				throw new IOException("File too small to be a valid PGS/M2TS input: " + input);
			}
		}
		if ((head[0] & 0xFF) == 'P' && (head[1] & 0xFF) == 'G') {
			return false;
		}
		if ((head[4] & 0xFF) == 0x47) {
			return true;
		}
		throw new IOException(
				"Could not determine input type for " + input + " (expected a 'PG'-framed .sup file or an .m2ts file)");
	}

}
