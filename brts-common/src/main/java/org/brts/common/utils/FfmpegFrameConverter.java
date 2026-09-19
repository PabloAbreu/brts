package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/FfmpegFrameConverter.java' is part of BRTS.
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

import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;

/**
 * Converts decoded FFmpeg {@link AVFrame}s to {@link BufferedImage}s, caching the swscale context between calls.
 * <p>
 * Instances are not thread-safe and must be closed to release the native scaling context.
 */
public class FfmpegFrameConverter implements AutoCloseable {

	private SwsContext swsCtx;

	private int swsWidth = -1;

	private int swsHeight = -1;

	private int swsFormat = -1;

	/**
	 * Converts a decoded frame to a {@link BufferedImage#TYPE_3BYTE_BGR} image of the frame's own dimensions.
	 */
	public BufferedImage toBufferedImage(AVFrame frame) {
		int width = frame.width();
		int height = frame.height();
		int format = frame.format();

		if (swsCtx == null || width != swsWidth || height != swsHeight || format != swsFormat) {
			if (swsCtx != null) {
				sws_freeContext(swsCtx);
			}
			swsCtx = sws_getContext(width, height, format, width, height, AV_PIX_FMT_BGR24, SWS_BILINEAR, null, null,
					(double[]) null);
			swsWidth = width;
			swsHeight = height;
			swsFormat = format;
		}

		AVFrame bgrFrame = av_frame_alloc();
		bgrFrame.format(AV_PIX_FMT_BGR24);
		bgrFrame.width(width);
		bgrFrame.height(height);
		av_frame_get_buffer(bgrFrame, 0);

		sws_scale(swsCtx, frame.data(), frame.linesize(), 0, height, bgrFrame.data(), bgrFrame.linesize());

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		int linesize = bgrFrame.linesize(0);
		ByteBuffer buffer = bgrFrame.data(0).position(0).capacity((long) linesize * height).asByteBuffer();
		if (linesize == width * 3) {
			buffer.get(pixels);
		} else {
			// Handle padding in each row
			for (int y = 0; y < height; y++) {
				buffer.position(y * linesize);
				buffer.get(pixels, y * width * 3, width * 3);
			}
		}

		av_frame_free(bgrFrame);
		return image;
	}

	@Override
	public void close() {
		if (swsCtx != null) {
			sws_freeContext(swsCtx);
			swsCtx = null;
		}
	}

}
