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

	private ResizedImageComposition resize;

	private RotatedImageComposition rotation;

	@Getter
	@Setter
	public static class ResizedImageComposition {

		private Point bottomRight;

	}

	@Getter
	@Setter
	public static class RotatedImageComposition extends ResizedImageComposition {

		private ObjectExpression angle;

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
		if (resize != null) {
			Point br = resize.getBottomRight();
			double x = context.evalNumeric(br.x);
			double y = context.evalNumeric(br.y);
			double tlx = context.evalNumeric(topLeft.x);
			double tly = context.evalNumeric(topLeft.y);
			double scaleX = (double) (x - tlx) / w;
			double scaleY = (double) (y - tly) / h;
			result.scale(scaleX, scaleY);
		}
		if (rotation != null) {
			double tlx = context.evalNumeric(topLeft.x);
			double tly = context.evalNumeric(topLeft.y);
			Point br = rotation.getBottomRight();
			double brx = br != null && br.x != null ? context.evalNumeric(br.x) : (tlx + w);
			double bry = br != null && br.y != null ? context.evalNumeric(br.y) : (tly + h);
			double centerX = (tlx + brx) / 2.0;
			double centerY = (tly + bry) / 2.0;
			double angle = context.evalNumeric(rotation.getAngle());
			log.debug("input params for rotation: tl=({}, {}), br=({}, {}), center=({}, {}), angle={}", tlx, tly, brx,
					bry, centerX, centerY, angle);
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

}