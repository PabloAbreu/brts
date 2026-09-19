package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/ImageComposition.java' is part of BRTS.
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

import java.awt.geom.AffineTransform;

import org.brts.common.utils.expressions.ObjectExpression;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Model for an image composition, including image reference, optional mask, positioning, resizing, rotation, and
 * cropping.
 */
@Getter
@Setter
@Slf4j
public class ImageComposition {

	/** Reference to the image ID in the ImageReference collection. */
	private String imageId; // to find ImageReference

	/**
	 * Optional reference to an entry in ImagesComposition.transparencyMasks; the 8-bit grayscale mask is multiplied
	 * with the overlay's alpha before compositing.
	 */
	private String maskImageId;

	/** Top-left corner of the image in the composition coordinate space. */
	private Point topLeft;

	/** Opacity of the image, ranging from 0.0 to 1.0, applied to the image. */
	private ObjectExpression opacity;

	/** Target size for resizing the image. */
	private Size resize;

	/** Rotation specification for the image. */
	private RotatedImageComposition rotation;

	/** Crop specification for the image. */
	private Crop crop;

	/** Crop specification for the image. */
	@Getter
	@Setter
	public static class Crop {
		/** Top-left corner of the crop rectangle in the source image. */
		private Point topLeft;
		/** Size of the crop rectangle in the source image. */
		private Size size;
	}

	/**
	 * Rotation specification for the image.
	 *
	 *
	 * TODO : center of rotation (x,y) can be added in the future, but for now it is assumed to be the center of the
	 * image.
	 */
	@Getter
	@Setter
	public static class RotatedImageComposition {
		/** Rotation angle for the image, in degrees. */
		private ObjectExpression angle;
	}

	/**
	 * Computes the target pixel dimensions after applying the {@code resize} specification to a source of size
	 * {@code srcW x srcH}.
	 *
	 * @return a two-element array {@code [targetWidth, targetHeight]}, or {@code null} when no resize is configured.
	 */
	public int[] computeTargetSize(CompositionContext context, int srcW, int srcH) {
		if (resize == null) {
			return null;
		}
		var width = resize.getWidth();
		var height = resize.getHeight();
		if (width == null && height == null) {
			throw new IllegalArgumentException("resize cannot miss both width/height");
		}
		double scaleX;
		double scaleY;
		if (width != null && height != null) {
			double wi = context.evalNumeric(width);
			double he = context.evalNumeric(height);
			scaleX = wi / srcW;
			scaleY = he / srcH;
		} else if (width != null) {
			double wi = context.evalNumeric(width);
			scaleX = wi / srcW;
			scaleY = scaleX;
		} else {
			double he = context.evalNumeric(height);
			scaleY = he / srcH;
			scaleX = scaleY;
		}
		int targetW = (int) Math.round(srcW * scaleX);
		int targetH = (int) Math.round(srcH * scaleY);
		return new int[] { targetW, targetH };
	}

	/**
	 * Evaluates the crop rectangle in source-image pixels and validates that it is fully contained in the source.
	 *
	 * @return a four-element array {@code [x, y, width, height]}, or {@code null} when no crop is configured.
	 */
	public int[] computeCropBounds(CompositionContext context, int srcW, int srcH) {
		if (crop == null) {
			return null;
		}
		if (crop.getTopLeft() == null || crop.getSize() == null || crop.getTopLeft().getX() == null
				|| crop.getTopLeft().getY() == null || crop.getSize().getWidth() == null
				|| crop.getSize().getHeight() == null) {
			throw new IllegalArgumentException("crop requires topLeft.x, topLeft.y, size.width, and size.height");
		}
		int x = requireIntegralCoordinate("crop.topLeft.x", context.evalNumeric(crop.getTopLeft().getX()));
		int y = requireIntegralCoordinate("crop.topLeft.y", context.evalNumeric(crop.getTopLeft().getY()));
		int width = requireIntegralCoordinate("crop.size.width", context.evalNumeric(crop.getSize().getWidth()));
		int height = requireIntegralCoordinate("crop.size.height", context.evalNumeric(crop.getSize().getHeight()));
		if (x < 0 || y < 0 || width <= 0 || height <= 0 || x > srcW - width || y > srcH - height) {
			throw new IllegalArgumentException("crop [" + x + ", " + y + ", " + width + ", " + height
					+ "] exceeds source bounds " + srcW + "x" + srcH);
		}
		return new int[] { x, y, width, height };
	}

	private static int requireIntegralCoordinate(String name, double value) {
		if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE
				|| value > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(name + " must evaluate to a finite integer, but was " + value);
		}
		return (int) value;
	}

	public AffineTransform toAffineTransform(CompositionContext context, int w, int h) {
		return toAffineTransform(context, w, h, true);
	}

	/**
	 * Builds the composition transform for an overlay of {@code w x h}. When {@code applyResize} is false, callers have
	 * already resized the overlay and only the rotation is applied.
	 */
	public AffineTransform toAffineTransform(CompositionContext context, int w, int h, boolean applyResize) {
		AffineTransform result = new AffineTransform();
		if ((!applyResize || resize == null) && rotation == null) {
			return result; // identity
		}
		double scaleX = 1.0;
		double scaleY = 1.0;
		if (applyResize && resize != null) {
			var width = resize.getWidth();
			var height = resize.getHeight();
			if (width == null && height == null) {
				throw new IllegalArgumentException("resize cannot miss both width/height");
			}
			if (width != null && height != null) {
				double wi = context.evalNumeric(width);
				double he = context.evalNumeric(height);
				scaleX = wi / w;
				scaleY = he / h;
			} else if (width != null) {
				double wi = context.evalNumeric(width);
				scaleX = wi / w;
				scaleY = scaleX; // keep aspect ratio
			} else if (height != null) {
				double he = context.evalNumeric(height);
				scaleY = he / h;
				scaleX = scaleY; // keep aspect ratio
			}
			result.scale(scaleX, scaleY);
		}
		if (rotation != null && rotation.getAngle() != null) {
			double centerX = w / 2.0;
			double centerY = h / 2.0;
			double angle = context.evalNumeric(rotation.getAngle());
			log.debug("Applying rotation: angle={} center=({}, {})", angle, centerX, centerY);
			result.rotate(Math.toRadians(angle), centerX, centerY);
		}
		return result;
	}

	/**
	 * Represents a point with x and y coordinates.
	 *
	 * X and Y can be specified as expressions that evaluate to numeric values.
	 */
	@Getter
	@Setter
	public static class Point {
		/** X coordinate as an expression that evaluates to a numeric value. */
		private ObjectExpression x;
		/** Y coordinate as an expression that evaluates to a numeric value. */
		private ObjectExpression y;
	}

	/**
	 * Represents a size with width and height.
	 *
	 * Size can be specified as expressions that evaluate to numeric values.
	 */
	@Getter
	@Setter
	public static class Size {
		/** Width as an expression that evaluates to a numeric value. */
		private ObjectExpression width;
		/** Height as an expression that evaluates to a numeric value. */
		private ObjectExpression height;
	}

}
