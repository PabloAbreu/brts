package org.brts.lowlevel.pgs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/PgsParser.java' is part of BRTS.
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

import org.brts.lowlevel.igs.IgsParser;
import org.brts.lowlevel.igs.IgsPgsCodec;
import org.brts.lowlevel.igs.IgsSegmentType;
import org.brts.lowlevel.igs.model.CompositionDescriptor;
import org.brts.lowlevel.igs.model.CompositionObject;
import org.brts.lowlevel.igs.model.IgsRawSegment;
import org.brts.lowlevel.igs.model.VideoDescriptor;
import org.brts.lowlevel.pgs.model.PgsCompositionSegment;
import org.brts.lowlevel.pgs.model.PgsDisplaySet;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses PGS (Presentation Graphic Stream) segments — grouped by {@link IgsSegmentType#PG_COMPOSITION} instead of the
 * IGS {@code IG_COMPOSITION} — into {@link PgsDisplaySet}s.
 * <p>
 * PDS, WDS, and ODS segments are structurally identical between PGS and IGS, so the shared decoders from
 * {@link IgsParser} are reused directly.
 */
@Slf4j
public class PgsParser {

	private PgsParser() {
	}

	/**
	 * Groups raw segments into PGS display sets and reassembles fragmented ODS objects.
	 *
	 * @param segments ordered list of raw segments (PTS already resolved by the caller)
	 * @return ordered list of display sets
	 */
	public static List<PgsDisplaySet> parseGroupAndReassemble(List<IgsRawSegment> segments) {
		List<PgsDisplaySet> sets = groupIntoDisplaySets(segments);
		sets.forEach(ds -> ds.setObjects(IgsPgsCodec.reassembleObjects(ds.getObjects())));
		return sets;
	}

	/**
	 * Groups raw segments into display sets. A new display set begins at each PCS (PG_COMPOSITION) segment. The display
	 * set is marked complete when an END_OF_DISPLAY segment is encountered.
	 *
	 * @param segments ordered list of raw segments
	 * @return ordered list of display sets
	 */
	public static List<PgsDisplaySet> groupIntoDisplaySets(List<IgsRawSegment> segments) {
		List<PgsDisplaySet> sets = new ArrayList<>();
		PgsDisplaySet current = null;

		for (IgsRawSegment seg : segments) {
			if (seg.getType() == IgsSegmentType.PG_COMPOSITION) {
				current = new PgsDisplaySet();
				sets.add(current);
			}
			if (current == null) {
				log.warn("Segment type {} before first PCS, skipping", seg.getType());
				continue;
			}

			switch (seg.getType()) {
			case PG_COMPOSITION -> {
				PgsCompositionSegment pcs = decodePcs(seg);
				current.setCompositionSegment(pcs);
				if (pcs.getCompositionDescriptor() != null && pcs.getCompositionDescriptor().getState() == 2) {
					current.setEpochStart(true);
				}
			}
			case PALETTE_DEFINITION -> current.getPalettes().add(IgsParser.decodePalette(seg));
			case OBJECT_DEFINITION -> current.getObjects().add(IgsParser.decodeObject(seg));
			case WINDOW_DEFINITION -> current.getWindowDefinitions().add(IgsParser.decodeWindowDef(seg));
			case END_OF_DISPLAY -> {
				// no additional state to record beyond the segments already collected
			}
			default -> log.warn("Unexpected segment type {} in PGS display set", seg.getType());
			}
		}

		log.info("Grouped into {} PGS display sets", sets.size());
		return sets;
	}

	/**
	 * Decodes a Presentation Composition Segment (PCS). Binary layout matches {@code PgsMuxer#encodePcs}.
	 */
	public static PgsCompositionSegment decodePcs(IgsRawSegment seg) {
		byte[] d = seg.getSegmentData();
		PgsCompositionSegment pcs = new PgsCompositionSegment();
		pcs.setPts(seg.getPts());
		pcs.setDts(seg.getDts());

		if (d.length < 8)
			return pcs;

		int pos = 0;

		VideoDescriptor vd = new VideoDescriptor();
		vd.setWidth(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		vd.setHeight(((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF));
		vd.setFrameRateCode((d[pos + 4] & 0xFF) >> 4);
		pcs.setVideoDescriptor(vd);
		pos += 5;

		CompositionDescriptor cd = new CompositionDescriptor();
		cd.setNumber(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		cd.setState((d[pos + 2] & 0xFF) >> 6);
		pcs.setCompositionDescriptor(cd);
		pos += 3;

		pcs.setPaletteUpdateFlag((d[pos] & 0xFF) == 0x80);
		pos++;

		pcs.setPaletteIdRef(d[pos] & 0xFF);
		pos++;

		if (pos >= d.length)
			return pcs;
		int numObjects = d[pos] & 0xFF;
		pos++;

		List<CompositionObject> objects = new ArrayList<>();
		for (int i = 0; i < numObjects && pos + 8 <= d.length; i++) {
			CompositionObject co = new CompositionObject();
			co.setObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			co.setWindowIdRef(d[pos + 2] & 0xFF);
			int flags = d[pos + 3] & 0xFF;
			co.setForcedOn((flags & 0x40) != 0);
			co.setCropFlag((flags & 0x80) != 0);
			co.setX(((d[pos + 4] & 0xFF) << 8) | (d[pos + 5] & 0xFF));
			co.setY(((d[pos + 6] & 0xFF) << 8) | (d[pos + 7] & 0xFF));
			pos += 8;

			if (co.isCropFlag()) {
				if (pos + 8 > d.length)
					break;
				co.setCropX(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
				co.setCropY(((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF));
				co.setCropWidth(((d[pos + 4] & 0xFF) << 8) | (d[pos + 5] & 0xFF));
				co.setCropHeight(((d[pos + 6] & 0xFF) << 8) | (d[pos + 7] & 0xFF));
				pos += 8;
			}
			objects.add(co);
		}
		pcs.setCompositionObjects(objects);

		return pcs;
	}

}
