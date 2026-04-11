package org.brts.common.m2ts;

/**
 * Minimal subset of SPS fields required to reconstruct per-frame POC values and to locate
 * {@code pic_order_cnt_lsb} inside each slice header.
 */
public final class H264SpsInfo {

	int picOrderCntType;

	int log2MaxPicOrderCntLsbMinus4; // valid when picOrderCntType == 0

	int log2MaxFrameNumMinus4;

	boolean frameMbsOnly;

	boolean separateColourPlane;

	/** {@code num_reorder_frames} from VUI {@code bitstream_restriction}; 0 if absent. */
	int numReorderFrames;

}