package org.brts.lowlevel.pgs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/pgs/PgsSupReaderTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.brts.lowlevel.igs.model.CompositionDescriptor;
import org.brts.lowlevel.igs.model.CompositionObject;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsRawSegment;
import org.brts.lowlevel.igs.model.IgsWindow;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.igs.model.PaletteEntry;
import org.brts.lowlevel.igs.model.SequenceDescriptor;
import org.brts.lowlevel.igs.model.VideoDescriptor;
import org.brts.lowlevel.pgs.model.PgsCompositionSegment;
import org.brts.lowlevel.pgs.model.PgsDisplaySet;
import org.junit.jupiter.api.Test;

class PgsSupReaderTest {

	private static final long PTS_TICKS = 12_345_678L;

	@Test
	void parseSegments_roundTripsDisplaySetWrittenByPgsMuxer() throws Exception {
		PgsDisplaySet original = buildDisplaySet();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new PgsMuxer().writeDisplaySet(out, original, PTS_TICKS);

		List<IgsRawSegment> segments = PgsSupReader.parseSegments(out.toByteArray());
		assertThat(segments).allSatisfy(seg -> assertThat(seg.getPts()).isEqualTo(PTS_TICKS));

		List<PgsDisplaySet> displaySets = PgsParser.parseGroupAndReassemble(segments);
		assertThat(displaySets).singleElement().satisfies(ds -> {
			PgsCompositionSegment pcs = ds.getCompositionSegment();
			assertThat(pcs.getPts()).isEqualTo(PTS_TICKS);
			assertThat(pcs.getVideoDescriptor().getWidth()).isEqualTo(1920);
			assertThat(pcs.getVideoDescriptor().getHeight()).isEqualTo(1080);
			assertThat(pcs.getPaletteIdRef()).isEqualTo(1);
			assertThat(pcs.getCompositionObjects()).singleElement().satisfies(co -> {
				assertThat(co.getObjectIdRef()).isEqualTo(7);
				assertThat(co.getX()).isEqualTo(100);
				assertThat(co.getY()).isEqualTo(200);
			});

			assertThat(ds.getPalettes()).singleElement().satisfies(pal -> assertThat(pal.getEntries()).hasSize(1));
			assertThat(ds.getObjects()).singleElement().satisfies(obj -> {
				assertThat(obj.getWidth()).isEqualTo(4);
				assertThat(obj.getHeight()).isEqualTo(2);
				assertThat(obj.getRleData()).isEqualTo(new byte[] { 1, 2, 3, 4 });
			});
			assertThat(ds.getWindowDefinitions()).singleElement()
					.satisfies(wds -> assertThat(wds.getWindows()).hasSize(1));
		});
	}

	private PgsDisplaySet buildDisplaySet() {
		VideoDescriptor vd = new VideoDescriptor();
		vd.setWidth(1920);
		vd.setHeight(1080);
		vd.setFrameRateCode(1);

		CompositionDescriptor cd = new CompositionDescriptor();
		cd.setNumber(1);
		cd.setState(2);

		CompositionObject co = new CompositionObject();
		co.setObjectIdRef(7);
		co.setWindowIdRef(0);
		co.setX(100);
		co.setY(200);

		PgsCompositionSegment pcs = new PgsCompositionSegment();
		pcs.setVideoDescriptor(vd);
		pcs.setCompositionDescriptor(cd);
		pcs.setPaletteIdRef(1);
		pcs.setCompositionObjects(List.of(co));

		IgsPalette palette = new IgsPalette();
		palette.setId(1);
		palette.setVersion(0);
		PaletteEntry entry = new PaletteEntry();
		entry.setEntryId(1);
		entry.setY(200);
		entry.setCr(128);
		entry.setCb(128);
		entry.setAlpha(255);
		palette.setEntries(List.of(entry));

		IgsObject object = new IgsObject();
		object.setId(7);
		object.setVersion(0);
		SequenceDescriptor sd = new SequenceDescriptor();
		sd.setFirstInSequence(true);
		sd.setLastInSequence(true);
		object.setSequenceDescriptor(sd);
		object.setWidth(4);
		object.setHeight(2);
		object.setRleData(new byte[] { 1, 2, 3, 4 });

		IgsWindow window = new IgsWindow();
		window.setId(0);
		window.setX(100);
		window.setY(200);
		window.setWidth(4);
		window.setHeight(2);
		IgsWindowDefinition wds = new IgsWindowDefinition();
		wds.setWindows(List.of(window));

		PgsDisplaySet ds = new PgsDisplaySet();
		ds.setCompositionSegment(pcs);
		ds.setPalettes(List.of(palette));
		ds.setObjects(List.of(object));
		ds.setWindowDefinitions(List.of(wds));
		return ds;
	}

}
