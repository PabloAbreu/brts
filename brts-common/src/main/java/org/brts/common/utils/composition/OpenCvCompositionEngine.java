package org.brts.common.utils.composition;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.javacpp.DoublePointer;

import lombok.extern.slf4j.Slf4j;

import static org.bytedeco.opencv.global.opencv_core.BORDER_TRANSPARENT;
import static org.bytedeco.opencv.global.opencv_core.CV_64FC1;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC4;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_UNCHANGED;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LANCZOS4;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.warpAffine;

/**
 * {@link CompositionEngine} implementation backed by OpenCV.
 *
 * Lit
 *
 * <p>
 * Overlay transforms are applied via {@code warpAffine} with {@code INTER_LANCZOS4} interpolation, which produces
 * significantly sharper results than Java2D bicubic for scaled and rotated overlays. Alpha blending follows the
 * standard SRC_OVER formula with per-pixel transparency support.
 *
 * <h3>Coordinate mapping</h3> In the Java2D pipeline, {@code g.translate(x,y)} followed by {@code g.transform(AT)} maps
 * an overlay pixel {@code (u,v)} to the background pixel:
 *
 * <pre>
 * px = x + AT.m00*u + AT.m01*v + AT.m02
 * py = y + AT.m10*u + AT.m11*v + AT.m12
 * </pre>
 *
 * This translates to the OpenCV 2×3 affine matrix {@code M}:
 *
 * <pre>
 * M = [[AT.m00, AT.m01, x + AT.m02],
 *      [AT.m10, AT.m11, y + AT.m12]]
 * </pre>
 *
 * where {@code AT.getMatrix(m)} fills {@code m} as {@code [m00, m10, m01, m11, m02, m12]}.
 */
@Slf4j
public class OpenCvCompositionEngine implements CompositionEngine {

	@Override
	public ImageFrame load(Path imagePath) {
		Mat src = imread(imagePath.toString(), IMREAD_UNCHANGED);
		if (src == null || src.empty()) {
			throw new RuntimeException("Failed to load image: " + imagePath);
		}
		Mat bgra = OpenCvImageFrame.ensureBgra(src);
		if (bgra != src) {
			src.release();
		}
		return new OpenCvImageFrame(bgra);
	}

	@Override
	public ImageFrame fromBufferedImage(BufferedImage img) {
		return new OpenCvImageFrame(OpenCvImageFrame.fromBufferedImage(img));
	}

	@Override
	public ImageFrame copy(ImageFrame src) {
		Mat srcMat = toMat(src);
		Mat copy = srcMat.clone();
		return new OpenCvImageFrame(copy);
	}

	@Override
	public ImageFrame resize(ImageFrame source, int targetWidth, int targetHeight) {
		Mat srcMat = toMat(source);
		Mat dst = new Mat();
		org.bytedeco.opencv.global.opencv_imgproc.resize(srcMat, dst, new Size(targetWidth, targetHeight), 0, 0,
				INTER_AREA);// since we are downscaling, INTER_AREA is better than INTER_LANCZOS4
		return new OpenCvImageFrame(dst);
	}

	@Override
	public ImageFrame compose(ImageFrame background, ImageFrame overlay, AffineTransform transform, int x, int y,
			float opacity, ImageFrame mask) {
		Mat bgMat = toMat(background);
		Mat ovMat = toMat(overlay);

		// Apply transparency mask (pre-warp, in overlay pixel space)
		boolean maskedCopy = false;
		if (mask != null) {
			ovMat = applyMask(ovMat, toMat(mask));
			maskedCopy = true;
		}

		int bgW = bgMat.cols();
		int bgH = bgMat.rows();

		// Build the 2×3 OpenCV affine matrix from the Java2D AffineTransform + translation.
		// AffineTransform.getMatrix(m) fills: m = {m00, m10, m01, m11, m02, m12}
		double[] m = new double[6];
		transform.getMatrix(m);

		// Full mapping: dst_x = m[0]*u + m[2]*v + (m[4]+x)
		// dst_y = m[1]*u + m[3]*v + (m[5]+y)
		double[] matData = { m[0], m[2], m[4] + x, m[1], m[3], m[5] + y };
		Mat M = new Mat(2, 3, CV_64FC1);
		new DoublePointer(M.data()).put(matData);

		// Warp overlay into background coordinate space.
		// BORDER_TRANSPARENT leaves destination pixels untouched where the inverse mapping
		// falls outside the source bounds, so initialising warped to fully-transparent zeros
		// means out-of-bounds areas stay transparent.
		Mat warped = new Mat(bgH, bgW, CV_8UC4, new Scalar(0, 0, 0, 0));
		warpAffine(ovMat, warped, M, new Size(bgW, bgH), INTER_LANCZOS4, BORDER_TRANSPARENT, new Scalar(0, 0, 0, 0));
		M.release();

		// Per-pixel SRC_OVER alpha blend: warped → bgMat (in place)
		blendSrcOver(bgMat, warped, opacity);
		warped.release();
		if (maskedCopy) {
			ovMat.release();
		}

		log.debug("Composed overlay onto background {}×{} at ({},{}) opacity={}", bgW, bgH, x, y, opacity);
		return background;
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the underlying {@link Mat} for {@code frame}. If {@code frame} is already an {@link OpenCvImageFrame},
	 * its Mat is returned directly (borrowed). Otherwise the frame is converted via {@link BufferedImage}.
	 */
	private static Mat toMat(ImageFrame frame) {
		if (frame instanceof OpenCvImageFrame) {
			return ((OpenCvImageFrame) frame).getMat();
		}
		// Fallback: convert via BufferedImage (should not happen in normal usage)
		return OpenCvImageFrame.fromBufferedImage(frame.toBufferedImage());
	}

	/**
	 * Returns a new CV_8UC4 BGRA {@link Mat} that is a clone of {@code ovMat} with its alpha channel multiplied
	 * per-pixel by the corresponding grayscale value from {@code maskMat}.
	 *
	 * <p>
	 * The mask is interpreted as an 8-bit grayscale image loaded via the standard BGRA pipeline (all three colour
	 * channels equal the original grey value, alpha is 255). Channel 0 (B) is used as the mask value. If the mask
	 * dimensions differ from the overlay's, it is resized to match using bilinear interpolation.
	 *
	 * <p>
	 * The returned Mat is a new allocation owned by the caller; the caller must release it.
	 *
	 * @param ovMat   overlay Mat (CV_8UC4 BGRA, unmodified)
	 * @param maskMat mask Mat (CV_8UC4 BGRA with R=G=B=grey, A=255)
	 * @return new Mat with modified alpha channel
	 */
	private static Mat applyMask(Mat ovMat, Mat maskMat) {
		int ovW = ovMat.cols();
		int ovH = ovMat.rows();

		// Resize mask if dimensions differ
		Mat resizedMask;
		boolean releaseMask;
		if (maskMat.cols() != ovW || maskMat.rows() != ovH) {
			resizedMask = new Mat();
			org.bytedeco.opencv.global.opencv_imgproc.resize(maskMat, resizedMask, new Size(ovW, ovH), 0, 0,
					org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR);
			releaseMask = true;
		} else {
			resizedMask = maskMat;
			releaseMask = false;
		}

		int n = ovW * ovH;
		byte[] ovData = new byte[n * 4];
		byte[] maskData = new byte[n * 4];
		ovMat.data().position(0).get(ovData);
		resizedMask.data().position(0).get(maskData);

		byte[] result = ovData.clone();
		for (int i = 0; i < n; i++) {
			int si = i * 4;
			int origAlpha = result[si + 3] & 0xFF;
			int maskValue = maskData[si] & 0xFF; // channel 0 (B) equals the grey value
			result[si + 3] = (byte) ((origAlpha * maskValue) / 255);
		}

		Mat out = ovMat.clone();
		out.data().position(0).put(result);

		if (releaseMask) {
			resizedMask.release();
		}
		return out;
	}

	/**
	 * Performs an in-place SRC_OVER alpha blend of {@code src} onto {@code dst}.
	 *
	 * <p>
	 * Both Mats must be CV_8UC4 (BGRA) with identical dimensions and contiguous data. The SRC_OVER formula:
	 *
	 * <pre>
	 * effectiveSrcA = srcA * globalOpacity
	 * outA          = effectiveSrcA + dstA * (1 - effectiveSrcA)
	 * outC          = (srcC * effectiveSrcA + dstC * dstA * (1 - effectiveSrcA)) / outA   (for each RGB channel)
	 * </pre>
	 *
	 * @param dst           destination Mat (modified in place)
	 * @param src           source Mat (read only)
	 * @param globalOpacity per-composition opacity multiplier in [0, 1]
	 */
	private static void blendSrcOver(Mat dst, Mat src, float globalOpacity) {
		int w = dst.cols();
		int h = dst.rows();
		int n = w * h;

		byte[] dstData = new byte[n * 4];
		byte[] srcData = new byte[n * 4];
		dst.data().position(0).get(dstData);
		src.data().position(0).get(srcData);

		for (int i = 0; i < n; i++) {
			int si = i * 4;
			float srcA = (srcData[si + 3] & 0xFF) * (1f / 255f) * globalOpacity;
			if (srcA < 1e-5f) {
				continue; // fully transparent source — dst unchanged
			}

			float dstA = (dstData[si + 3] & 0xFF) * (1f / 255f);
			float outA = srcA + dstA * (1f - srcA);

			if (outA < 1e-5f) {
				dstData[si] = dstData[si + 1] = dstData[si + 2] = dstData[si + 3] = 0;
				continue;
			}

			float invOutA = 1f / outA;
			float srcContrib = srcA * invOutA;
			float dstContrib = dstA * (1f - srcA) * invOutA;

			dstData[si] = clampToByte(
					Math.round((srcData[si] & 0xFF) * srcContrib + (dstData[si] & 0xFF) * dstContrib));
			dstData[si + 1] = clampToByte(
					Math.round((srcData[si + 1] & 0xFF) * srcContrib + (dstData[si + 1] & 0xFF) * dstContrib));
			dstData[si + 2] = clampToByte(
					Math.round((srcData[si + 2] & 0xFF) * srcContrib + (dstData[si + 2] & 0xFF) * dstContrib));
			dstData[si + 3] = clampToByte(Math.round(outA * 255f));
		}

		dst.data().position(0).put(dstData);
	}

	private static byte clampToByte(int v) {
		return (byte) Math.max(0, Math.min(255, v));
	}

}
