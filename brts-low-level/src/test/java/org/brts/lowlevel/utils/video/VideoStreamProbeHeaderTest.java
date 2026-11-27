/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/VideoStreamProbeHeaderTest.java' is part of BRTS.
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
package org.brts.lowlevel.utils.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure header tests: no files, decoder, native codec calls, or conversion. */
class VideoStreamProbeHeaderTest {

	private static final Path SOURCE = Path.of("synthetic", "scan-mode.hevc");
	private static final byte[] NEXT_NAL = { 0, 0, 1, 0x46, 1, (byte) 0x80 }; // AUD delimiter
	private static final Method HEVC_PROGRESSIVE = parserMethod();

	@ParameterizedTest
	@CsvSource({ "3,false", "3,true", "4,false", "4,true" })
	void progressivePtlWithoutVuiAcceptsBothAnnexBStartCodes(int startCodeBytes, boolean eof) throws Exception {
		assertTrue(parse(nal(sps(true, false, null, false, 0), startCodeBytes, true), eof));
	}

	@Test
	void interlacedPtlWithoutVuiReturnsFalse() throws Exception {
		assertFalse(parse(nal(sps(false, true, null, false, 0), 3, true), false));
	}

	@ParameterizedTest
	@CsvSource({ "true,false", "false,true", "true,true", "false,false" })
	void vuiFieldSequenceOverridesEveryPtlFlagCombination(boolean progressive, boolean interlaced) throws Exception {
		assertFalse(parse(nal(sps(progressive, interlaced, true, false, 0), 3, true), false));
	}

	@Test
	void progressiveVuiFieldSequenceZeroReturnsTrue() throws Exception {
		assertTrue(parse(nal(sps(true, false, false, false, 0), 3, true), false));
	}

	@Test
	void vuiFieldSequenceZeroDoesNotOverrideInterlacedPtl() throws Exception {
		assertFalse(parse(nal(sps(false, true, false, false, 0), 3, true), false));
	}

	@ParameterizedTest
	@CsvSource({ "false,false,false", "true,true,false", "false,false,true", "true,true,true" })
	void ambiguousPtlFailsClosedWithOrWithoutVui(boolean progressive, boolean interlaced, boolean vui) {
		assertFailure(nal(sps(progressive, interlaced, vui ? Boolean.FALSE : null, false, 0), 3, true), false,
				"ambiguous SPS progressive/interlaced source flags");
	}

	@Test
	void completeSpsWithoutNextNalRequiresConfirmedEof() throws Exception {
		byte[] prefix = nal(sps(true, false, null, false, 0), 3, false);
		assertFailure(prefix, false, "incomplete SPS in captured prefix");
		assertTrue(parse(prefix, true));
	}

	@Test
	void truncatedSpsWithoutNextNalFailsAtPrefixBoundaryOrSyntaxDependingOnEof() {
		byte[] prefix = nal(Arrays.copyOf(sps(true, false, null, false, 0), 5), 3, false);
		assertFailure(prefix, false, "incomplete SPS in captured prefix");
		assertFailure(prefix, true, "truncated SPS syntax");
	}

	@Test
	void truncatedSpsWithNextNalStillFailsEvenWhenEofIsConfirmed() {
		assertFailure(nal(new byte[] { 1 }, 3, true), true, "truncated SPS syntax");
	}

	@Test
	void eofDoesNotProveCompletenessWhenPrefixExactlyFillsCaptureBudget() {
		byte[] prefix = Arrays.copyOf(nal(sps(true, false, null, false, 0), 3, false), 1024 * 1024);
		assertFailure(prefix, true, "incomplete SPS in captured prefix");
	}

	@Test
	void missingSpsFailsForEmptyInputNonSpsNalAndPartialStartCode() {
		for (byte[] prefix : new byte[][] { {}, NEXT_NAL, { 0, 0, 1, 0x42 }, { 0, 0, 0, 0 } }) {
			assertFailure(prefix, true, "no complete first SPS");
		}
	}

	@Test
	void skipsOtherNalTypesBeforeFirstSps() throws Exception {
		assertTrue(parse(concat(NEXT_NAL, nal(sps(true, false, null, false, 0), 4, true)), false));
	}

	@Test
	void onlyFirstSpsDeterminesScanMode() throws Exception {
		byte[] progressive = nal(sps(true, false, null, false, 0), 3, false);
		byte[] interlaced = nal(sps(false, true, null, false, 0), 4, false);
		assertTrue(parse(concat(progressive, interlaced), false));
		assertFalse(parse(concat(interlaced, progressive), false));
	}

	@Test
	void malformedFirstSpsDoesNotFallThroughToLaterValidSps() {
		assertFailure(concat(nal(new byte[] { 1 }, 3, false), nal(sps(true, false, null, false, 0), 3, true)), false,
				"truncated SPS syntax");
	}

	@ParameterizedTest
	@CsvSource({ "194,1", "67,1", "66,9", "66,0" })
	void rejectsForbiddenBitBothMultilayerHeaderEncodingsAndZeroTemporalId(int first, int second) {
		byte[] prefix = nal(sps(true, false, null, false, 0), 3, true);
		prefix[3] = (byte) first;
		prefix[4] = (byte) second;
		assertFailure(prefix, false, "unsupported SPS NAL header/layer");
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 7 })
	void acceptsNonzeroTemporalIdForSingleLayer(int temporalId) throws Exception {
		byte[] prefix = nal(sps(true, false, null, false, 0), 3, true);
		prefix[4] = (byte) temporalId;
		assertTrue(parse(prefix, false));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 6 })
	void traversesSublayerProfilesLevelsOrderingAndOptionalSyntax(int layers) throws Exception {
		// Includes explicit and inferred scaling matrices, PCM, explicit/predicted short-term RPS,
		// long-term references, separate colour planes, conformance window and all pre-field VUI fields.
		assertTrue(parse(nal(sps(true, false, false, true, layers), 4, true), false));
		assertFalse(parse(nal(sps(true, false, true, true, layers), 4, true), false));
	}

	@Test
	void rejectsUnsupportedSublayerCount() {
		BitWriter bits = new BitWriter();
		bits.bits(0, 4).bits(7, 3).flag(true);
		assertFailure(nal(bits.finish(), 3, true), false, "unsupported SPS sublayers");
	}

	@Test
	void rejectsOutOfBoundsAndOversizedExpGolombValues() {
		BitWriter outOfBounds = profile(true, false, 0);
		outOfBounds.ue(16); // sps_seq_parameter_set_id must be <= 15
		assertFailure(nal(outOfBounds.finish(), 3, true), false, "SPS syntax value exceeds probe bounds");
		BitWriter oversized = profile(true, false, 0);
		oversized.bits(0, 31).flag(true);
		assertFailure(nal(oversized.finish(), 3, true), false, "oversized SPS Exp-Golomb value");
	}

	@Test
	void removesEmulationPreventionBytesBeforeParsing() throws Exception {
		byte[] prefix = nal(sps(true, false, null, false, 0), 3, true);
		boolean escaped = false;
		for (int i = 5; i + 2 < prefix.length; i++) {
			escaped |= prefix[i] == 0 && prefix[i + 1] == 0 && prefix[i + 2] == 3;
		}
		assertTrue(escaped, "Zero-filled PTL constraints must exercise RBSP unescaping");
		assertTrue(parse(prefix, false));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3 })
	void acceptsLegalEmulationPreventionFollowers(int follower) throws Exception {
		byte[] prefix = concat(nal(sps(true, false, null, false, 0), 3, false), new byte[] { 0, 0, 3, (byte) follower },
				NEXT_NAL);
		assertTrue(parse(prefix, false));
	}

	@Test
	void rejectsDanglingOrIllegalEmulationPreventionEvenAfterScanSyntax() {
		byte[] prefix = nal(sps(true, false, null, false, 0), 3, false);
		assertFailure(concat(prefix, new byte[] { 0, 0, 3 }), true, "invalid SPS emulation prevention");
		assertFailure(concat(prefix, new byte[] { 0, 0, 3, 4 }, NEXT_NAL), false, "invalid SPS emulation prevention");
	}

	private static Method parserMethod() {
		try {
			Method method = VideoStreamProbe.class.getDeclaredMethod("hevcProgressive", byte[].class, boolean.class,
					Path.class);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Cannot access pure HEVC header parser", exception);
		}
	}

	private static boolean parse(byte[] prefix, boolean eof) throws Exception {
		try {
			return (boolean) HEVC_PROGRESSIVE.invoke(null, prefix, eof, SOURCE);
		} catch (InvocationTargetException exception) {
			// Assert the parser's IOException, not the reflection wrapper.
			if (exception.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw new AssertionError("Unexpected parser failure", exception.getCause());
		}
	}

	private static void assertFailure(byte[] prefix, boolean eof, String reason) {
		IOException exception = assertThrows(IOException.class, () -> parse(prefix, eof));
		assertEquals("Cannot establish HEVC scan mode within bounded first-header probe for " + SOURCE + ": " + reason,
				exception.getMessage());
		assertEquals(reason, assertInstanceOf(IllegalArgumentException.class, exception.getCause()).getMessage());
	}

	/** Writes only syntax consumed by this parser, not a decodable video or a conformance fixture. */
	private static byte[] sps(boolean progressive, boolean interlaced, Boolean fieldSequence, boolean complex,
			int layers) {
		BitWriter bits = profile(progressive, interlaced, layers);
		bits.ue(0).ue(complex ? 3 : 1); // SPS id, chroma_format_idc
		if (complex) {
			bits.flag(true); // separate_colour_plane_flag
		}
		bits.ue(64).ue(64).flag(complex);
		if (complex) {
			for (int i = 0; i < 4; i++) {
				bits.ue(1); // conformance window
			}
		}
		bits.ue(0).ue(0).ue(0).flag(complex); // depths, POC bits, ordering_info_present
		for (int i = complex ? 0 : layers; i <= layers; i++) {
			bits.ue(0).ue(0).ue(0);
		}
		for (int i = 0; i < 6; i++) {
			bits.ue(0);
		}
		bits.flag(complex); // scaling_list_enabled_flag
		if (complex) {
			bits.flag(true); // scaling_list_data_present_flag
			for (int size = 0; size < 4; size++) {
				for (int matrix = 0; matrix < 6; matrix += size == 3 ? 3 : 1) {
					bits.flag(matrix == 0);
					if (matrix == 0) {
						if (size > 1) {
							bits.ue(0); // scaling_list_dc_coef_minus8 (signed zero)
						}
						for (int j = 0; j < Math.min(64, 1 << (4 + 2 * size)); j++) {
							bits.ue(0); // scaling_list_delta_coef (signed zero)
						}
					} else {
						bits.ue(0); // scaling_list_pred_matrix_id_delta
					}
				}
			}
		}
		bits.flag(false).flag(false).flag(complex); // AMP, SAO, PCM
		if (complex) {
			bits.bits(0x77, 8).ue(0).ue(0).flag(false);
		}
		bits.ue(complex ? 3 : 0); // num_short_term_ref_pic_sets
		if (complex) {
			bits.ue(1).ue(1).ue(0).flag(true).ue(0).flag(false); // explicit negative/positive RPS
			bits.flag(true).flag(false).ue(0); // predicted second RPS
			bits.flag(true).flag(false).flag(true).flag(false).flag(false); // used, use_delta, neither
			bits.flag(false).ue(0).ue(0); // third RPS explicitly empty
		}
		bits.flag(complex); // long_term_ref_pics_present_flag
		if (complex) {
			bits.ue(2).bits(0, 4).flag(true).bits(1, 4).flag(false);
		}
		bits.flag(false).flag(false).flag(fieldSequence != null); // MVP, smoothing, VUI
		if (fieldSequence != null) {
			bits.flag(complex); // aspect_ratio_info_present_flag
			if (complex) {
				bits.bits(255, 8).bits(1, 16).bits(1, 16); // Extended_SAR
			}
			bits.flag(complex);
			if (complex) {
				bits.flag(false); // overscan_appropriate_flag
			}
			bits.flag(complex);
			if (complex) {
				bits.bits(5, 3).flag(false).flag(true).bits(0x010101, 24); // video signal and colours
			}
			bits.flag(complex);
			if (complex) {
				bits.ue(2).ue(2); // chroma location
			}
			bits.flag(false).flag(fieldSequence); // neutral chroma, field_seq_flag
		}
		return bits.finish();
	}

	private static BitWriter profile(boolean progressive, boolean interlaced, int layers) {
		BitWriter bits = new BitWriter();
		bits.bits(0, 4).bits(layers, 3).flag(true).bits(1, 8).bits(0, 32);
		bits.flag(progressive).flag(interlaced).bits(0, 46).bits(120, 8);
		for (int i = 0; i < layers; i++) {
			bits.flag(i % 2 == 0).flag(i % 2 != 0); // exercise profile-only and level-only sublayers
		}
		if (layers > 0) {
			bits.bits(0, (8 - layers) * 2);
		}
		for (int i = 0; i < layers; i++) {
			bits.bits(0, i % 2 == 0 ? 88 : 8);
		}
		return bits;
	}

	private static byte[] nal(byte[] rbsp, int startCodeBytes, boolean nextNal) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (int i = 1; i < startCodeBytes; i++) {
			bytes.write(0);
		}
		bytes.write(1);
		bytes.write(33 << 1); // forbidden_zero_bit=0, nal_unit_type=SPS, nuh_layer_id=0
		bytes.write(1); // nuh_temporal_id_plus1=1
		int zeros = 0;
		for (byte value : rbsp) {
			int unsigned = value & 0xff;
			if (zeros >= 2 && unsigned <= 3) {
				bytes.write(3);
				zeros = 0;
			}
			bytes.write(unsigned);
			zeros = unsigned == 0 ? zeros + 1 : 0;
		}
		if (nextNal) {
			bytes.writeBytes(NEXT_NAL);
		}
		return bytes.toByteArray();
	}

	private static byte[] concat(byte[]... arrays) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (byte[] array : arrays) {
			bytes.writeBytes(array);
		}
		return bytes.toByteArray();
	}

	private static final class BitWriter {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private int current;
		private int used;

		BitWriter flag(boolean value) {
			current = (current << 1) | (value ? 1 : 0);
			if (++used == 8) {
				bytes.write(current);
				current = 0;
				used = 0;
			}
			return this;
		}

		BitWriter bits(long value, int count) {
			for (int i = count - 1; i >= 0; i--) {
				// Counts above 64 occur only for zero-filled sublayer profiles.
				flag(i < Long.SIZE && ((value >>> i) & 1) != 0);
			}
			return this;
		}

		BitWriter ue(int value) {
			long code = (long) value + 1;
			int width = Long.SIZE - Long.numberOfLeadingZeros(code);
			return bits(0, width - 1).bits(code, width);
		}

		byte[] finish() {
			flag(true); // rbsp_stop_one_bit; remaining byte is zero-padded
			while (used != 0) {
				flag(false);
			}
			return bytes.toByteArray();
		}
	}
}
