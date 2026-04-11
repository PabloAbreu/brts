package org.brts.middle.preview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.swing.JPanel;

import org.brts.common.utils.ImageUtils;
import org.brts.common.utils.StopWatch;
import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsPage;

import lombok.extern.slf4j.Slf4j;

/**
 * Swing panel that renders the current state of a {@link DisplaySetPreviewModel}.
 * <p>
 * The panel maintains the original aspect ratio of the display set and scales all button
 * graphics proportionally when the window is resized. A programmatically generated
 * checkerboard pattern is used as a background to simulate the video plane.
 *
 * For the time being, only draws the start button image for each button state
 * (normal/selected/activated).
 */
@Slf4j
public class DisplaySetPanel extends JPanel {

	public static final int STATUS_BAR_HEIGHT = 24;

	private static final int CHECKER_SIZE = 32;

	private static final Color CHECKER_LIGHT = new Color(0x55, 0x55, 0x55);

	private static final Color CHECKER_DARK = new Color(0x33, 0x33, 0x33);

	private static final Color NORMAL_HIGHLIGHT = new Color(0x00, 0x77, 0xAA, 0x80);

	private static final Color SELECTION_HIGHLIGHT = new Color(0x00, 0xAA, 0xFF, 0x90);

	private static final Color ACTIVATION_HIGHLIGHT = new Color(0xFF, 0xAA, 0x00, 0xB0);

	private static final Color RATIO_2_40_HINT = new Color(0xFF, 0x00, 0x00, 0x80);

	private static final Color OVERLAY_BG = new Color(0x00, 0x00, 0x00, 0xCC);

	private static final Color OVERLAY_FG = Color.WHITE;

	private static final Color NAV_ARROW_COLOR = new Color(0x00, 0xFF, 0x80, 0xC0);

	private static final Color NAV_LABEL_BG = new Color(0x00, 0x00, 0x00, 0xB0);

	private static final Color NAV_LABEL_FG = new Color(0x80, 0xFF, 0xC0);

	private final DisplaySetPreviewModel model;

	/** Pre-rendered checkerboard background tile (unscaled). */
	private BufferedImage checkerboardTile;

	/**
	 * Cache of ghost images generated from a selected-state image when the corresponding
	 * normal-state image is fully transparent. Key = normal-state object id, Value =
	 * ghost image derived from the selected state.
	 */
	private final Map<Integer, BufferedImage> ghostImageCache = new HashMap<>();

	public DisplaySetPanel(DisplaySetPreviewModel model) {
		this.model = model;
		setBackground(Color.BLACK);
		setDoubleBuffered(true);
		buildCheckerboardTile();
	}

	// ── Aspect-ratio sizing ─────────────────────────────────────────────────

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(model.getScreenWidth(), model.getScreenHeight());
	}

	/**
	 * Computes the viewport rectangle that fits the display-set aspect ratio inside the
	 * current panel bounds.
	 */
	private Rectangle computeViewport() {
		int pw = getWidth();
		int ph = getHeight();
		double nativeW = model.getScreenWidth();
		double nativeH = model.getScreenHeight();
		double aspect = nativeW / nativeH;

		int viewW, viewH;
		if ((double) pw / ph > aspect) {
			// Panel is wider than needed — fit height
			viewH = ph;
			viewW = (int) Math.round(ph * aspect);
		}
		else {
			// Panel is taller than needed — fit width
			viewW = pw;
			viewH = (int) Math.round(pw / aspect);
		}
		int x = (pw - viewW) / 2;
		int y = 0;// (ph - viewH) / 2;
		return new Rectangle(x, y, viewW, viewH);
	}

	// ── Painting ────────────────────────────────────────────────────────────

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		StopWatch sw = new StopWatch(log::debug);
		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Rectangle vp = computeViewport();
			double scaleX = vp.width / (double) model.getScreenWidth();
			double scaleY = vp.height / (double) model.getScreenHeight();

			// Clip to viewport
			g2.setClip(vp.x, vp.y, vp.width, vp.height);

			// Draw checkerboard background
			sw.start("Draw checkerboard");
			drawCheckerboard(g2, vp);
			sw.stop();

			// Transform: translate to viewport origin then scale
			AffineTransform xf = new AffineTransform();
			xf.translate(vp.x, vp.y);
			xf.scale(scaleX, scaleY);
			g2.setTransform(xf);

			// Render buttons
			sw.start("Render page");
			renderPage(g2);
			sw.stop();

			// Draw command overlay (if any)
			g2.setTransform(new AffineTransform()); // reset to pixel coords
			sw.start("Draw overlay");
			drawOverlay(g2, vp);
			sw.stop();

			// Draw status bar
			g2.setClip(null);
			drawStatusBar(g2, vp);

			// visual hint for compatibility with 2.40 displays: a rectangle around the
			// whole viewport with a dashed outline
			if (model.isDisplayHints()) {
				g2.setColor(RATIO_2_40_HINT);
				Stroke savedStroke = g2.getStroke();
				g2.setStroke(
						new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 10, 10 }, 0));
				// compute Y coordinates for 2.40 aspect ratio (e.g. 1920x1080 → 1920x800)
				// centered within the viewport
				int ratioH = (int) Math.round(vp.width / 2.40);
				int ratioY = vp.y + (vp.height - ratioH) / 2;
				g2.drawRect(vp.x, ratioY, vp.width, ratioH);
				g2.setStroke(savedStroke);
				g2.drawString("2.40 aspect ratio", vp.x + 10, ratioY - 20);
			}

		}
		finally {
			g2.dispose();
		}
		sw.close();
	}

	private void drawCheckerboard(Graphics2D g2, Rectangle vp) {
		AffineTransform saved = g2.getTransform();
		g2.setTransform(new AffineTransform());

		for (int y = vp.y; y < vp.y + vp.height; y += CHECKER_SIZE * 2) {
			for (int x = vp.x; x < vp.x + vp.width; x += CHECKER_SIZE * 2) {
				// copy pre-rendered tile for better performance
				g2.drawImage(checkerboardTile, x, y, null);
			}
		}

		g2.setTransform(saved);
	}

	private void renderPage(Graphics2D g2) {
		IgsPage page = model.getCurrentPage();
		if (page == null)
			return;

		// Collect enabled buttons for drawing and for navigation lookup
		List<IgsButton> enabledButtons = new ArrayList<>();

		for (int bogIdx = 0; bogIdx < page.getBogs().size(); bogIdx++) {
			IgsBog bog = page.getBogs().get(bogIdx);
			Integer enabledId = model.getBogEnabledButtons().get(bogIdx);

			for (IgsButton btn : bog.getButtons()) {
				// Only draw the enabled button in each BOG
				if (enabledId != null && btn.getId() != enabledId)
					continue;

				drawButton(g2, btn);
				enabledButtons.add(btn);
			}
		}

		// Draw navigation arrows from each enabled button to its neighbours
		if (model.isDisplayHints())
			drawNavigationArrows(g2, enabledButtons);
	}

	private void drawButton(Graphics2D g2, IgsButton btn) {
		boolean isSelected = (btn.getId() == model.getSelectedButtonId());
		boolean isActivated = (btn.getId() == model.getActivatedButtonId());

		int bx = btn.getXPos();
		int by = btn.getYPos();
		log.debug("Drawing button #{} at ({}, {}){}{}", btn.getId(), bx, by, isSelected ? " [SELECTED]" : "",
				isActivated ? " [ACTIVATED]" : "");

		// Pick the image matching the button's current visual state:
		// activated > selected > normal
		BufferedImage img;
		if (isActivated) {
			img = model.getObjectImages().get(btn.getActivatedStartObjectIdRef());
			if (img == null)
				img = model.getObjectImages().get(btn.getSelectedStartObjectIdRef());
			if (img == null)
				img = resolveNormalImage(btn);
		}
		else if (isSelected) {
			img = model.getObjectImages().get(btn.getSelectedStartObjectIdRef());
			if (img == null)
				img = resolveNormalImage(btn);
		}
		else {
			img = resolveNormalImage(btn);
		}

		if (img != null) {
			g2.drawImage(img, bx, by, img.getWidth(), img.getHeight(), null);
		}
		else {
			// Draw a placeholder rectangle
			g2.setColor(new Color(0x80, 0x80, 0x80, 0x60));
			g2.fillRect(bx, by, 120, 40);
			g2.setColor(Color.GRAY);
			g2.drawRect(bx, by, 120, 40);
			g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
			g2.drawString("btn#" + btn.getId(), bx + 4, by + 24);
		}
		if (!model.isDisplayHints())
			return;
		// Draw selection/activation highlight outline
		if (isActivated) {
			drawHighlight(g2, btn, img, ACTIVATION_HIGHLIGHT, 3);
		}
		else if (isSelected) {
			drawHighlight(g2, btn, img, SELECTION_HIGHLIGHT, 2);
		}
		else {
			drawHighlight(g2, btn, img, NORMAL_HIGHLIGHT, 2);
		}
	}

	private void drawHighlight(Graphics2D g2, IgsButton btn, BufferedImage img, Color color, int thickness) {
		int bx = btn.getXPos();
		int by = btn.getYPos();
		int bw = img != null ? img.getWidth() : 120;
		int bh = img != null ? img.getHeight() : 40;

		Stroke savedStroke = g2.getStroke();
		g2.setStroke(new BasicStroke(thickness));
		g2.setColor(color);
		g2.drawRect(bx - thickness, by - thickness, bw + 2 * thickness, bh + 2 * thickness);
		g2.setStroke(savedStroke);
	}

	// ── Ghost images for fully-transparent normal states ─────────────────────

	/**
	 * Resolves the normal-state image for a button. If the original image is fully
	 * transparent (all alpha == 0) and a selected-state image exists, a "ghost"
	 * placeholder is returned instead — a faded copy of the selected image annotated with
	 * a marker symbol.
	 */
	private BufferedImage resolveNormalImage(IgsButton btn) {
		int normalId = btn.getNormalStartObjectIdRef();
		BufferedImage normalImg = model.getObjectImages().get(normalId);

		// Fast path: no image at all → nothing to ghost
		if (normalImg == null)
			return null;

		// Fast path: image has visible content → use it as-is
		if (!isFullyTransparent(normalId))
			return normalImg;

		// Already cached?
		BufferedImage ghost = ghostImageCache.get(normalId);
		if (ghost != null)
			return ghost;

		// Try to derive a ghost from the selected-state image
		BufferedImage selectedImg = model.getObjectImages().get(btn.getSelectedStartObjectIdRef());
		if (selectedImg == null || isFullyTransparent(btn.getSelectedStartObjectIdRef())) {
			// Nothing useful to derive from — fall back to original
			return normalImg;
		}

		ghost = ImageUtils.createGhostImage(selectedImg);
		ghostImageCache.put(normalId, ghost);
		return ghost;
	}

	private boolean isFullyTransparent(int imageId) {
		Boolean cached = transparencyCache.get(imageId);
		if (cached != null)
			return cached;

		BufferedImage img = model.getObjectImages().get(imageId);
		boolean transparent = (img == null) || ImageUtils.isFullyTransparent(img);
		transparencyCache.put(imageId, transparent);
		return transparent;
	}

	// stores whether an image (by ID) is transparent, to avoid expensive checks on every
	// render
	// not so expensive for reasonable image sizes and counts, but it could add up
	private final Map<Integer, Boolean> transparencyCache = new HashMap<>();

	// ── Navigation arrows ───────────────────────────────────────────────────

	/**
	 * Draws oriented navigation arrows from each enabled button to its neighbour targets
	 * (up, down, left, right). When multiple directions point to the same target, their
	 * labels are collapsed into a single annotated line (e.g. "left,down").
	 */
	private void drawNavigationArrows(Graphics2D g2, List<IgsButton> enabledButtons) {
		// Build a fast lookup: button id → enabled button (for centre computation)
		Map<Integer, IgsButton> enabledById = new LinkedHashMap<>();
		for (IgsButton btn : enabledButtons) {
			enabledById.put(btn.getId(), btn);
		}

		// Only draw arrows from the currently selected button
		int selectedId = model.getSelectedButtonId();
		IgsButton selected = enabledById.get(selectedId);
		if (selected == null)
			return;

		// Gather the four neighbour directions and group by target id
		int[][] neighbours = { { selected.getUpperButtonIdRef(), 0 }, // 0 = up
				{ selected.getLowerButtonIdRef(), 1 }, // 1 = down
				{ selected.getLeftButtonIdRef(), 2 }, // 2 = left
				{ selected.getRightButtonIdRef(), 3 }, // 3 = right
		};
		String[] dirLabels = { "\u2191 up", "\u2193 down", "\u2190 left", "\u2192 right" };

		// Collapse: targetId → list of direction labels
		Map<Integer, List<String>> targetDirections = new LinkedHashMap<>();
		for (int[] entry : neighbours) {
			int targetId = entry[0];
			int dirIndex = entry[1];

			// Skip "no neighbour" (0xFFFF) and self-referencing
			if (targetId == 0xFFFF || targetId == selected.getId())
				continue;
			// Skip targets that are not currently enabled/visible
			if (!enabledById.containsKey(targetId))
				continue;

			targetDirections.computeIfAbsent(targetId, k -> new ArrayList<>()).add(dirLabels[dirIndex]);
		}

		// Draw one arrow per distinct target
		for (Map.Entry<Integer, List<String>> e : targetDirections.entrySet()) {
			IgsButton target = enabledById.get(e.getKey());
			if (target == null)
				continue;

			StringJoiner label = new StringJoiner(", ");
			for (String d : e.getValue())
				label.add(d);

			drawArrowBetween(g2, selected, target, label.toString());
		}
	}

	/**
	 * Returns the centre point of a button, taking its image size into account.
	 */
	private int[] buttonCentre(IgsButton btn) {
		int[] dimensions = buttonDimensions(btn);
		return new int[] { btn.getXPos() + dimensions[0] / 2, btn.getYPos() + dimensions[1] / 2 };
	}

	/**
	 * Returns the bounding rectangle {x, y, w, h} of a button.
	 */
	private int[] buttonBounds(IgsButton btn) {
		int[] dimensions = buttonDimensions(btn);
		return new int[] { btn.getXPos(), btn.getYPos(), dimensions[0], dimensions[1] };
	}

	private int[] buttonDimensions(IgsButton btn) {
		BufferedImage img = model.getObjectImages().get(btn.getNormalStartObjectIdRef());
		if (img == null)
			img = model.getObjectImages().get(btn.getSelectedStartObjectIdRef());
		if (img == null)
			img = model.getObjectImages().get(btn.getActivatedStartObjectIdRef());
		int w = img != null ? img.getWidth() : 120;
		int h = img != null ? img.getHeight() : 40;
		return new int[] { w, h };
	}

	/**
	 * Draws an arrow from the edge of {@code src} towards the edge of {@code dst},
	 * annotated with the given label.
	 */
	private void drawArrowBetween(Graphics2D g2, IgsButton src, IgsButton dst, String label) {
		int[] srcC = buttonCentre(src);
		int[] dstC = buttonCentre(dst);

		double dx = dstC[0] - srcC[0];
		double dy = dstC[1] - srcC[1];
		double dist = Math.sqrt(dx * dx + dy * dy);
		if (dist < 1)
			return; // overlapping buttons

		// Normalised direction
		double nx = dx / dist;
		double ny = dy / dist;

		// Clip start / end to button edges (approximate with half-size)
		int[] srcB = buttonBounds(src);
		int[] dstB = buttonBounds(dst);

		double srcMargin = edgeDistance(srcB[2], srcB[3], nx, ny);
		double dstMargin = edgeDistance(dstB[2], dstB[3], nx, ny);

		// Extra pixel margin so the line doesn't touch the button outlines
		double gap = 4;
		double x1 = srcC[0] + nx * (srcMargin + gap);
		double y1 = srcC[1] + ny * (srcMargin + gap);
		double x2 = dstC[0] - nx * (dstMargin + gap);
		double y2 = dstC[1] - ny * (dstMargin + gap);

		// Don't draw if start and end are almost the same (very close buttons)
		double lineDist = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
		if (lineDist < 8)
			return;

		Stroke savedStroke = g2.getStroke();
		Font savedFont = g2.getFont();

		// Draw line
		g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(NAV_ARROW_COLOR);
		g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);

		// Draw arrowhead at destination end
		drawArrowhead(g2, x2, y2, nx, ny, 10);

		// Draw label at midpoint
		double mx = (x1 + x2) / 2;
		double my = (y1 + y2) / 2;
		g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		FontMetrics fm = g2.getFontMetrics();
		int tw = fm.stringWidth(label);
		int th = fm.getAscent() + fm.getDescent();
		int pad = 3;

		// Background pill behind label text
		g2.setColor(NAV_LABEL_BG);
		g2.fillRoundRect((int) mx - tw / 2 - pad, (int) my - th / 2 - pad, tw + 2 * pad, th + 2 * pad, 6, 6);

		// Label text
		g2.setColor(NAV_LABEL_FG);
		g2.drawString(label, (int) mx - tw / 2, (int) my + fm.getAscent() / 2);

		g2.setStroke(savedStroke);
		g2.setFont(savedFont);
	}

	/**
	 * Approximates the distance from a rectangle's centre to its edge along a given
	 * normalised direction vector (nx, ny).
	 */
	private static double edgeDistance(int w, int h, double nx, double ny) {
		double hw = w / 2.0;
		double hh = h / 2.0;
		double ax = Math.abs(nx);
		double ay = Math.abs(ny);
		if (ax < 1e-9 && ay < 1e-9)
			return 0;
		// time to hit vertical edge vs horizontal edge
		double tx = ax > 1e-9 ? hw / ax : Double.MAX_VALUE;
		double ty = ay > 1e-9 ? hh / ay : Double.MAX_VALUE;
		return Math.min(tx, ty);
	}

	/**
	 * Draws a small filled arrowhead at (tipX, tipY) pointing in the direction (nx, ny).
	 */
	private static void drawArrowhead(Graphics2D g2, double tipX, double tipY, double nx, double ny, double size) {
		// Perpendicular vector
		double px = -ny;
		double py = nx;
		double halfW = size * 0.45;

		Path2D arrow = new Path2D.Double();
		arrow.moveTo(tipX, tipY);
		arrow.lineTo(tipX - nx * size + px * halfW, tipY - ny * size + py * halfW);
		arrow.lineTo(tipX - nx * size - px * halfW, tipY - ny * size - py * halfW);
		arrow.closePath();
		g2.fill(arrow);
	}

	private void drawOverlay(Graphics2D g2, Rectangle vp) {
		String msg = model.getCommandOverlayMessage();
		long ts = model.getCommandOverlayTimestamp();
		if (msg == null || msg.isEmpty())
			return;

		// Auto-expire after 2.5 seconds
		if (System.currentTimeMillis() - ts > 2500) {// TODO use constant from the other
														// class
			return;
		}

		// Semi-transparent banner at the bottom of the viewport
		int bannerH = 48;
		int bannerY = vp.y + vp.height - bannerH - 10;
		g2.setColor(OVERLAY_BG);
		g2.fillRoundRect(vp.x + 20, bannerY, vp.width - 40, bannerH, 12, 12);

		g2.setColor(OVERLAY_FG);
		g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		FontMetrics fm = g2.getFontMetrics();

		// Truncate if too long
		String display = msg;
		int maxW = vp.width - 60;
		if (fm.stringWidth(display) > maxW) {
			while (fm.stringWidth(display + "…") > maxW && display.length() > 10) {
				display = display.substring(0, display.length() - 1);
			}
			display += "…";
		}

		int textX = vp.x + 30;
		int textY = bannerY + (bannerH + fm.getAscent() - fm.getDescent()) / 2;
		g2.drawString(display, textX, textY);
	}

	private void drawStatusBar(Graphics2D g2, Rectangle vp) {
		String status = String.format(
				"Page %d/%d  |  Button #%d  |  %d×%d  |  ↑↓←→ Navigate  Enter Activate  PgUp/PgDn Pages",
				model.getCurrentPageIndex() + 1, model.getPages().size(), model.getSelectedButtonId(),
				model.getScreenWidth(), model.getScreenHeight());

		int barH = STATUS_BAR_HEIGHT;
		int barY = vp.y + vp.height + 2;
		if (barY + barH > getHeight()) {
			barY = getHeight() - barH;
		}

		g2.setColor(new Color(0x20, 0x20, 0x30));
		g2.fillRect(0, barY, getWidth(), barH);
		g2.setColor(new Color(0xDD, 0xAA, 0xAA));
		g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g2.drawString(status, 8, barY + (int) (barH * 2 / 3));// looks good with 2/3
	}

	// ── Checkerboard tile ───────────────────────────────────────────────────

	private void buildCheckerboardTile() {
		int size = CHECKER_SIZE * 2;
		checkerboardTile = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D tg = checkerboardTile.createGraphics();
		tg.setColor(CHECKER_LIGHT);
		tg.fillRect(0, 0, size, size);
		tg.setColor(CHECKER_DARK);
		tg.fillRect(0, CHECKER_SIZE, CHECKER_SIZE, CHECKER_SIZE);
		tg.fillRect(CHECKER_SIZE, 0, CHECKER_SIZE, CHECKER_SIZE);
		tg.dispose();
	}

}
