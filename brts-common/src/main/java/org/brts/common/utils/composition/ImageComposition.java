package org.brts.common.utils.composition;

import java.awt.geom.AffineTransform;

import org.brts.common.utils.expressions.ObjectExpression;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class ImageComposition {

	private String imageId; // to find ImageReference

	private Point topLeft;

	private ObjectExpression opacity; // 0.0 to 1.0, applied to the applied image

	private Size resize;

	private RotatedImageComposition rotation;

	// TODO : crop the input image to a rectangle defined by its own topLeft and size

	@Getter
	@Setter
	public static class RotatedImageComposition {

		private ObjectExpression angle;
		// TODO : center of rotation (x,y) can be added in the future, but for now it is assumed to be the center of the
		// image.

	}

	public boolean remainsConstantOverTime() {
		return true;
		// All numerical parameters (topLeft, bottomRight, angle) are constant for now,
		// but they might be time-varying in the future.
		// they will be defined as strings with placeholders for frameNumber, and the
		// actual values will be computed in toAffineTransform()
		// based on the current frame number.
	}

	public AffineTransform toAffineTransform(CompositionContext context, int w, int h) {
		AffineTransform result = new AffineTransform();
		if (resize == null && rotation == null) {
			return result; // identity
		}
		double scaleX = 1.0;
		double scaleY = 1.0;
		if (resize != null) {
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

	@Getter
	@Setter
	public static class Point {
		private ObjectExpression x;
		private ObjectExpression y;
	}

	@Getter
	@Setter
	public static class Size {
		private ObjectExpression width;
		private ObjectExpression height;
	}

}