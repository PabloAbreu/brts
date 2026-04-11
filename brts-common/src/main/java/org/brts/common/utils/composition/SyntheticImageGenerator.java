package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;

/**
 * Produces synthetic images based on a description.
 *
 * Idea for implementation: support "<svg>...</svg>" descriptions and use an SVG rendering
 * library such as Apache Batikto produce the image. width and height need to be provided
 * in the SVG description, and will be the size of the produced image.
 *
 * FIXME : the implementation might need a frameRate (23.976) to produce timestamps from
 * frame numbers.
 *
 * Content is given at creation.
 *
 */
public interface SyntheticImageGenerator extends AutoCloseable {

	BufferedImage generate(int frameNumber);

}