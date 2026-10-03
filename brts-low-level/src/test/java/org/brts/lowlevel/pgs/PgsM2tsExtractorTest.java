package org.brts.lowlevel.pgs;



import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsRawSegment;
import org.brts.lowlevel.pgs.PgsM2tsExtractor.PtsBoundary;
import org.junit.jupiter.api.Test;

class PgsM2tsExtractorTest {

	@Test
	void parseSegments_attributesPtsFromEnclosingBoundary() {
		byte[] seg1 = segment(0x16, new byte[] { 0x01 }); // PG_COMPOSITION
		byte[] seg2 = segment(0x17, new byte[] { 0x02 }); // WINDOW_DEFINITION
		byte[] seg3 = segment(0x80, new byte[0]); // END_OF_DISPLAY

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		buf.writeBytes(seg1);
		int offsetSeg2 = buf.size();
		buf.writeBytes(seg2);
		int offsetSeg3 = buf.size();
		buf.writeBytes(seg3);

		List<PtsBoundary> boundaries = List.of(new PtsBoundary(0, 1000L), new PtsBoundary(offsetSeg2, 1000L),
				new PtsBoundary(offsetSeg3, 2000L));

		List<IgsRawSegment> segments = PgsM2tsExtractor.parseSegments(buf.toByteArray(), boundaries);

		assertThat(segments).hasSize(3);
		assertThat(segments.get(0).getPts()).isEqualTo(1000L);
		assertThat(segments.get(1).getPts()).isEqualTo(1000L);
		assertThat(segments.get(2).getPts()).isEqualTo(2000L);
	}

	private static byte[] segment(int type, byte[] data) {
		byte[] out = new byte[3 + data.length];
		out[0] = (byte) type;
		out[1] = (byte) ((data.length >> 8) & 0xFF);
		out[2] = (byte) (data.length & 0xFF);
		System.arraycopy(data, 0, out, 3, data.length);
		return out;
	}

}
