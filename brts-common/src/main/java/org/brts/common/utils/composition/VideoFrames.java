package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;

/**
 * Able to produce frames from a video.
 *
 * getFrame() might be called multiple times with the same frameNumber, or with frameNumbers that loop around the total
 * frame count.
 *
 */
public interface VideoFrames extends AutoCloseable {

	int getFrameCount();

	double getFps();

	BufferedImage getFrame(int frameNumber);

}