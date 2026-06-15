package org.brts.common.utils.composition;

/**
 * Able to produce frames from a video.
 *
 * <p>
 * {@link #getFrame} may be called multiple times with the same frameNumber, or with frameNumbers that loop around the
 * total frame count. Returned frames are <em>borrowed</em> (owned by the implementation's cache); callers must
 * <strong>not</strong> call {@link ImageFrame#close()} on them.
 */
public interface VideoFrames extends AutoCloseable {

	int getFrameCount();

	double getFps();

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame index. Do not close the returned frame.
	 */
	ImageFrame getFrame(int frameNumber);

}