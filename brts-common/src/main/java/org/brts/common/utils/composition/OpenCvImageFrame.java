package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import org.bytedeco.opencv.opencv_core.Mat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC4;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2BGRA;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGRA;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * {@link ImageFrame} backed by an OpenCV {@link Mat} in 4-channel BGRA format (CV_8UC4).
 *
 * <p>
 * {@link #close()} releases the native Mat memory. The Mat is assumed to be contiguous (no row padding), which is
 * guaranteed for all Mats created within this framework.
 */
@RequiredArgsConstructor
public class OpenCvImageFrame implements ImageFrame {

	/**
	 * Takes ownership of {@code mat}. The Mat must be 4-channel BGRA (CV_8UC4) and contiguous.
	 *
	 * @param mat CV_8UC4 Mat; this frame becomes the owner and will release it on {@link #close()}
	 */
	private final @Getter Mat mat;

	@Override
	public int width() {
		return mat.cols();
	}

	@Override
	public int height() {
		return mat.rows();
	}

	/**
	 * Converts BGRA Mat data to a {@code TYPE_INT_ARGB} {@link BufferedImage}.
	 *
	 * <p>
	 * The returned image is fully independent of this frame and remains valid after {@link #close()}.
	 */
	@Override
	public BufferedImage toBufferedImage() {
		Mat bgra = ensureBgra(mat);
		try {
			int w = bgra.cols();
			int h = bgra.rows();
			int n = w * h;

			byte[] data = new byte[n * 4];
			bgra.data().position(0).get(data);

			BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			int[] argb = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
			for (int i = 0; i < n; i++) {
				int b = data[i * 4] & 0xFF;
				int g = data[i * 4 + 1] & 0xFF;
				int r = data[i * 4 + 2] & 0xFF;
				int a = data[i * 4 + 3] & 0xFF;
				argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
			}
			return result;
		} finally {
			if (bgra != mat) {
				bgra.release();
			}
		}
	}

	/** Releases the native Mat. Safe to call multiple times. */
	@Override
	public void close() {
		if (mat != null && !mat.isNull()) {
			mat.release();
			// mat = null; // mat is final, cannot be reassigned
		}
	}

	/**
	 * Ensures the returned Mat is CV_8UC4 BGRA. Returns {@code src} directly if it already is; otherwise allocates a
	 * converted Mat that the caller must release.
	 */
	static Mat ensureBgra(Mat src) {
		if (src.channels() == 4) {
			return src;
		}
		Mat bgra = new Mat();
		int convCode = (src.channels() == 1) ? COLOR_GRAY2BGRA : COLOR_BGR2BGRA;
		cvtColor(src, bgra, convCode);
		return bgra;
	}

	/**
	 * Converts a {@link BufferedImage} of any standard type to a CV_8UC4 BGRA {@link Mat}.
	 */
	static Mat fromBufferedImage(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();

		// Normalise to TYPE_INT_ARGB so we always work with a single known layout.
		if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
			BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = argb.createGraphics();
			g.drawImage(img, 0, 0, null);
			g.dispose();
			img = argb;
		}

		int[] argbPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		byte[] bgraData = new byte[w * h * 4];
		for (int i = 0; i < w * h; i++) {
			int pixel = argbPixels[i]; // ARGB: 0xAARRGGBB
			bgraData[i * 4] = (byte) pixel; // B
			bgraData[i * 4 + 1] = (byte) (pixel >> 8); // G
			bgraData[i * 4 + 2] = (byte) (pixel >> 16); // R
			bgraData[i * 4 + 3] = (byte) (pixel >> 24); // A
		}

		Mat mat = new Mat(h, w, CV_8UC4);
		mat.data().position(0).put(bgraData);
		return mat;
	}

}
