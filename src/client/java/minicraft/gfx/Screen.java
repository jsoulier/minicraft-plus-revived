package minicraft.gfx;

import minicraft.core.Renderer;
import minicraft.core.Updater;
import minicraft.gfx.SpriteLinker.LinkedSprite;
import minicraft.gfx.SpriteLinker.SpriteType;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class Screen {

	public static final int w = Renderer.WIDTH; // Width of the screen
	public static final int h = Renderer.HEIGHT; // Height of the screen
	public static final Point center = new Point(w / 2, h / 2);

	private static final int MAXDARK = 128;

	/// x and y offset of screen:
	private int xOffset;
	private int yOffset;

	// Used for mirroring an image:
	private static final int BIT_MIRROR_X = 0x01; // Written in hexadecimal; binary: 01
	private static final int BIT_MIRROR_Y = 0x02; // Binary: 10

	private final BufferedImage image;
	private final int[] pixels;

	private final ArrayDeque<Rendering> renderings = new ArrayDeque<>();
	private final LightOverlay lightOverlay;
	private ClearRendering lastClearRendering = null;
	private boolean firstPerson;
	private Context.SpriteMode spriteMode = Context.SpriteMode.GROUND;
	private int spriteModeX, spriteModeY, spriteModeGroundOffset;

	// Outdated Information:
	// Since each sheet is 256x256 pixels, each one has 1024 8x8 "tiles"
	// So 0 is the start of the item sheet 1024 the start of the tile sheet, 2048 the start of the entity sheet,
	// And 3072 the start of the gui sheet

	public Screen(BufferedImage image) {
		/// Screen width and height are determined by the actual game window size, meaning the screen is only as big as the window.buffer = new BufferedImage(Screen.w, Screen.h);
		this.image = image;
		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		lightOverlay = new LightOverlay();
	}

	private interface Rendering {
		/** Invoked by {@link Renderer#render()}. */
		void render(Context context);
	}

	private void queue(Rendering rendering) {
		renderings.add(rendering);
	}

	private static abstract class ClearRendering implements Rendering {}

	private static class SolidClearRendering extends ClearRendering {
		private final int color;

		public SolidClearRendering(int color) {
			this.color = color;
		}

		@Override
		public void render(Context context) {
			context.clear(color);
		}
	}

	private static class PlainClearRendering extends ClearRendering {
		@Override
		public void render(Context context) {
			context.clear(0);
		}
	}

	private class SpriteRendering implements Rendering {
		private final int xp, yp, xt, yt, tw, th, mirrors, whiteTint, color;
		private final boolean fullBright;
		private final MinicraftImage sheet;

		public SpriteRendering(int xp, int yp, int xt, int yt, int tw, int th,
		                       int mirrors, int whiteTint, boolean fullBright, int color, MinicraftImage sheet) {
			this.xp = xp;
			this.yp = yp;
			this.xt = xt;
			this.yt = yt;
			this.tw = tw;
			this.th = th;
			this.mirrors = mirrors;
			this.whiteTint = whiteTint;
			this.fullBright = fullBright;
			this.color = color;
			this.sheet = sheet;
		}

		@Override
		public void render(Context context) {
			context.sprite(xp, yp, xt, yt, tw, th, (mirrors & BIT_MIRROR_X) > 0, (mirrors & BIT_MIRROR_Y) > 0, whiteTint, fullBright, color, sheet);
		}
	}

	private static class FillRectRendering implements Rendering {
		private final int xp, yp, w, h, color;

		public FillRectRendering(int xp, int yp, int w, int h, int color) {
			this.xp = xp;
			this.yp = yp;
			this.w = w;
			this.h = h;
			this.color = color;
		}

		@Override
		public void render(Context context) {
			context.fillRect(xp, yp, w, h, color);
		}
	}

	private static class DrawRectRendering implements Rendering {
		private final int xp, yp, w, h, color;

		public DrawRectRendering(int xp, int yp, int w, int h, int color) {
			this.xp = xp;
			this.yp = yp;
			this.w = w;
			this.h = h;
			this.color = color;
		}

		@Override
		public void render(Context context) {
			context.drawRect(xp, yp, w, h, color);
		}
	}

	private static class DrawLineRendering implements Rendering {
		private final int x0, y0, x1, y1, color;

		public DrawLineRendering(int x0, int y0, int x1, int y1, int color) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.color = color;
		}

		@Override
		public void render(Context context) {
			context.drawLine(x0, y0, x1, y1, color);
		}
	}

	/** Placeholder way, for Sign cursor rendering */
	private class DrawLineSpecialRendering implements Rendering {
		private final int x0, y0, l;
		private final @MagicConstant(intValues = {0, 1}) int axis; // 0: x-axis; 1: Y-axis

		public DrawLineSpecialRendering(int x0, int y0, int l, int axis) {
			this.x0 = x0;
			this.y0 = y0;
			this.l = l;
			this.axis = axis;
		}

		@Override
		public void render(Context context) {
			switch (axis) {
				case 0:
					context.drawLineSpecial(x0, y0, l, 1);
					break;
				case 1:
					context.drawLineSpecial(x0, y0, 1, l);
					break;
			}
		}
	}

	private static class BeginOffscreenRendering implements Rendering {
		@Override
		public void render(Context context) {
			context.beginOffscreen();
		}
	}

	private static class EndOffscreenRendering implements Rendering {
		private final int srcX, srcY, dstX, dstY, w, h;

		public EndOffscreenRendering(int srcX, int srcY, int dstX, int dstY, int w, int h) {
			this.srcX = srcX;
			this.srcY = srcY;
			this.dstX = dstX;
			this.dstY = dstY;
			this.w = w;
			this.h = h;
		}

		@Override
		public void render(Context context) {
			context.endOffscreen(srcX, srcY, dstX, dstY, w, h);
		}
	}

	private static class SetCameraRendering implements Rendering {
		private final int x, y;
		private final float dirX, dirY, eyeHeight;

		public SetCameraRendering(int x, int y, float dirX, float dirY, float eyeHeight) {
			this.x = x;
			this.y = y;
			this.dirX = dirX;
			this.dirY = dirY;
			this.eyeHeight = eyeHeight;
		}

		@Override
		public void render(Context context) {
			context.setCamera(x, y, dirX, dirY, eyeHeight);
		}
	}

	private static class SkyRendering implements Rendering {
		private final float time, windTime;
		private final int cameraX, cameraY;

		public SkyRendering(float time, float windTime, int cameraX, int cameraY) {
			this.time = time;
			this.windTime = windTime;
			this.cameraX = cameraX;
			this.cameraY = cameraY;
		}

		@Override
		public void render(Context context) {
			context.drawSky(time, windTime, cameraX, cameraY);
		}
	}

	private static class ResetCameraRendering implements Rendering {
		@Override
		public void render(Context context) {
			context.resetCamera();
		}
	}

	private static class SpriteModeRendering implements Rendering {
		private final Context.SpriteMode spriteMode;
		private final int x, y, groundOffset;

		public SpriteModeRendering(Context.SpriteMode spriteMode, int x, int y, int groundOffset) {
			this.spriteMode = spriteMode;
			this.x = x;
			this.y = y;
			this.groundOffset = groundOffset;
		}

		@Override
		public void render(Context context) {
			context.setSpriteMode(spriteMode, x, y, groundOffset);
		}
	}

	private class OverlayRendering implements Rendering {
		private final int currentLevel, xa, ya;
		private final double darkFactor;
		private final float[] lights;

		private OverlayRendering(int currentLevel, int xa, int ya, double darkFactor) {
			this.currentLevel = currentLevel;
			this.xa = xa;
			this.ya = ya;
			this.darkFactor = darkFactor;
			lights = lightOverlay.getLights();
		}

		@Override
		public void render(Context context) {
			double alpha = lightOverlay.getOverlayOpacity(currentLevel, darkFactor);
			context.overlay(lights, (float) alpha, xa, ya);
		}
	}

	/**
	 * Clears all the colors on the screen
	 */
	public void clear(int color) {
		lightOverlay.lights.clear();
		resetSpriteMode();
		// Turns each pixel into a single color (clearing the screen!)
		if (color == 0) {
			queueClearRendering(new PlainClearRendering());
		} else {
			queueClearRendering(new SolidClearRendering(color));
		}
	}

	private void queueClearRendering(ClearRendering clearRendering) {
		lastClearRendering = clearRendering;
		queue(clearRendering);
	}

	public void flush(Context context) {
		Rendering rendering;
		do { // Skips until the latest clear rendering is obtained.
			rendering = renderings.poll(); // This can prevent redundant renderings operated.
			if (rendering == null) return;
		} while (rendering != lastClearRendering);
		context.begin();
		do { // Renders all renderings until all are operated.
			rendering.render(context);
		} while ((rendering = renderings.poll()) != null);
		context.end();
	}

	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet) {
		render(xp, yp, xt, yt, bits, sheet, -1);
	}

	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint) {
		render(xp, yp, xt, yt, bits, sheet, whiteTint, false);
	}

	/**
	 * This method takes care of assigning the correct spritesheet to assign to the sheet variable
	 **/
	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint, boolean fullbright) {
		render(xp, yp, xt, yt, bits, sheet, whiteTint, fullbright, 0);
	}

	public void render(int xp, int yp, LinkedSprite sprite) {
		render(xp, yp, sprite.getSprite());
	}

	public void render(int xp, int yp, Sprite sprite) {
		render(xp, yp, sprite, false);
	}

	public void render(int xp, int yp, Sprite sprite, boolean fullbright) {
		render(xp, yp, sprite, 0, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite sprite, int mirror, boolean fullbright) {
		render(xp, yp, sprite, mirror, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite sprite, int mirror, boolean fullbright, int color) {
		boolean mirrorX = (mirror & BIT_MIRROR_X) > 0; // Horizontally.
		boolean mirrorY = (mirror & BIT_MIRROR_Y) > 0; // Vertically.
		for (int r = 0; r < sprite.spritePixels.length; r++) {
			int lr = mirrorY ? sprite.spritePixels.length - 1 - r : r;
			for (int c = 0; c < sprite.spritePixels[lr].length; c++) {
				Sprite.Px px = sprite.spritePixels[lr][mirrorX ? sprite.spritePixels[lr].length - 1 - c : c];
				render(xp + c * 8, yp + r * 8, px, mirror, sprite.color, fullbright, color);
			}
		}
	}

	public void render(int xp, int yp, Sprite.Px pixel) {
		render(xp, yp, pixel, -1);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int whiteTint) {
		render(xp, yp, pixel, 0, whiteTint);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint) {
		render(xp, yp, pixel, mirror, whiteTint, false);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint, boolean fullbright) {
		render(xp, yp, pixel, mirror, whiteTint, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint, boolean fullbright, int color) {
		render(xp, yp, pixel.x, pixel.y, pixel.mirror ^ mirror, pixel.sheet, whiteTint, fullbright, color);
	}

	/**
	 * Renders an object from the sprite sheet based on screen coordinates, tile (SpriteSheet location), colors, and bits (for mirroring). I believe that xp and yp refer to the desired position of the upper-left-most pixel.
	 */
	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint, boolean fullbright, int color) {
		if (sheet == null) return; // Verifying that sheet is not null.

		// xp and yp are originally in level coordinates, but offset turns them to screen coordinates.
		// xOffset and yOffset account for screen offset
		render(xp - xOffset, yp - yOffset, xt * 8, yt * 8, 8, 8, sheet, bits, whiteTint, fullbright, color);
	}

	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet) {
		render(xp, yp, xt, yt ,tw, th, sheet, 0);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors) {
		render(xp, yp, xt, yt ,tw, th, sheet, mirrors, -1);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint) {
		render(xp, yp, xt, yt, tw, th, sheet, mirrors, whiteTint, false);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint, boolean fullbright) {
		render(xp, yp, xt, yt, tw, th, sheet, mirrors, whiteTint, fullbright, 0);
	}
	// Any single pixel from the image can be rendered using this method.
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint, boolean fullBright, int color) {
		if (sheet == null) return; // Verifying that sheet is not null.

		// Validation check
		if (xt + tw > sheet.width && yt + th > sheet.height) {
			render(xp, yp, 0, 0, mirrors, Renderer.spriteLinker.missingSheet(SpriteType.Item));
			return;
		}

		queue(new SpriteRendering(xp, yp, xt, yt, tw, th, mirrors, whiteTint, fullBright, color, sheet));
	}

	public void render(int xp, int yp, MinicraftImage sheet) {
		render(xp - xOffset, yp - yOffset, 0, 0, sheet.width, sheet.height, sheet);
	}

	public void fillRect(int xp, int yp, int w, int h, int color) {
		queue(new FillRectRendering(xp, yp, w, h, color));
	}

	public void drawRect(int xp, int yp, int w, int h, int color) {
		queue(new DrawRectRendering(xp, yp, w, h, color));
	}

	/**
	 * Draw a straight line along an axis.
	 * @param axis The axis to draw along: {@code 0} for x-axis; {@code 1} for y-axis
	 * @param l The length of the line
	 */
	public void drawAxisLine(int xp, int yp, @MagicConstant(intValues = {0, 1}) int axis, int l, int color) {
		switch (axis) {
			case 0: queue(new DrawLineRendering(xp, yp, xp + l, yp, color)); break;
			case 1: queue(new DrawLineRendering(xp, yp, xp, yp + l, color)); break;
		}
	}

	public void drawLine(int x0, int y0, int x1, int y1, int color) {
		queue(new DrawLineRendering(x0, y0, x1, y1, color));
	}

	/** Placeholder line drawing method specialized for sign cursor drawing */
	public void drawLineSpecial(int x0, int y0, @MagicConstant(intValues = {0, 1}) int axis, int l) {
		queue(new DrawLineSpecialRendering(x0, y0, l, axis));
	}

	public void setCamera(int x, int y, float dirX, float dirY, float eyeHeight) {
		firstPerson = true;
		resetSpriteMode();
		queue(new SetCameraRendering(x - xOffset, y - yOffset, dirX, dirY, eyeHeight));
	}

	public void renderSky(int cameraX, int cameraY) {
		float time = (float) Updater.tickCount / Updater.dayLength;
		float windTime = (Updater.gameTime % 216000) / 60f;
		queue(new SkyRendering(time, windTime, cameraX, cameraY));
	}

	public void resetCamera() {
		firstPerson = false;
		resetSpriteMode();
		queue(new ResetCameraRendering());
	}

	public boolean isFirstPerson() {
		return firstPerson;
	}

	public void beginOffscreen() {
		queue(new BeginOffscreenRendering());
	}

	public void endOffscreen(int srcX, int srcY, int dstX, int dstY, int w, int h) {
		queue(new EndOffscreenRendering(srcX, srcY, dstX, dstY, w, h));
	}

	public void setSpriteMode(Context.SpriteMode spriteMode, int x, int y, int groundOffset) {
		int positionX = x - xOffset;
		int positionY = y - yOffset;
		if (spriteMode == Context.SpriteMode.GROUND) {
			positionX = 0;
			positionY = 0;
			groundOffset = 0;
		}
		boolean sameMode = spriteMode == this.spriteMode;
		boolean samePosition = positionX == spriteModeX && positionY == spriteModeY;
		boolean sameGroundOffset = groundOffset == spriteModeGroundOffset;
		if (sameMode && samePosition && sameGroundOffset) {
			return;
		}
		this.spriteMode = spriteMode;
		spriteModeX = positionX;
		spriteModeY = positionY;
		spriteModeGroundOffset = groundOffset;
		queue(new SpriteModeRendering(spriteMode, positionX, positionY, groundOffset));
	}

	private void resetSpriteMode() {
		spriteMode = Context.SpriteMode.GROUND;
		spriteModeX = 0;
		spriteModeY = 0;
		spriteModeGroundOffset = 0;
	}

	/**
	 * Sets the offset of the screen
	 */
	public void setOffset(int xOffset, int yOffset) {
		// This is called in few places, one of which is level.renderBackground, right before all the tiles are rendered. The offset is determined by the Game class (this only place renderBackground is called), by using the screen's width and the player's position in the level.
		// In other words, the offset is a conversion factor from level coordinates to screen coordinates. It makes a certain coord in the level the upper left corner of the screen, when subtracted from the tile coord.

		this.xOffset = xOffset;
		this.yOffset = yOffset;
	}

	/* Used for the scattered dots at the edge of the light radius underground.

		These values represent the minimum light level, on a scale from 0 to 25 (255/10), 0 being no light, 25 being full light (which will be portrayed as transparent on the overlay lightScreen pixels) that a pixel must have in order to remain lit (not black).
		each row and column is repeated every 4 pixels in the proper direction, so the pixel lightness minimum varies. It's highly worth note that, as the rows progress and loop, there's two sets or rows (1,4 and 2,3) whose values in the same column add to 15. The exact same is true for columns (sets are also 1,4 and 2,3), execpt the sums of values in the same row and set differ for each row: 10, 18, 12, 20. Which... themselves... are another set... adding to 30... which makes sense, sort of, since each column totals 15+15=30.
		In the end, "every other every row", will need, for example in column 1, 15 light to be lit, then 0 light to be lit, then 12 light to be lit, then 3 light to be lit. So, the pixels of lower light levels will generally be lit every other pixel, while the brighter ones appear more often. The reason for the variance in values is to provide EVERY number between 0 and 15, so that all possible light levels (below 16) are represented fittingly with their own pattern of lit and not lit.
		16 is the minimum pixel lighness required to ensure that the pixel will always remain lit.
	*/

	/**
	 * Overlays the screen with pixels
	 */
	public void overlay(int currentLevel, int xa, int ya) {
		double darkFactor = 0;
		if (currentLevel >= 3 && currentLevel < 5) {
			int transTime = Updater.dayLength / 4;
			double relTime = (Updater.tickCount % transTime) * 1.0 / transTime;

			switch (Updater.getTime()) {
				case Morning:
					darkFactor = Updater.pastDay1 ? (1 - relTime) * MAXDARK : 0;
					break;
				case Day:
					darkFactor = 0;
					break;
				case Evening:
					darkFactor = relTime * MAXDARK;
					break;
				case Night:
					darkFactor = MAXDARK;
					break;
			}

			if (currentLevel > 3) darkFactor -= (darkFactor < 10 ? darkFactor : 10);
		} else if (currentLevel >= 5)
			darkFactor = MAXDARK;

		// The Integer array of pixels to overlay the screen with.
		queue(new OverlayRendering(currentLevel, xa, ya, darkFactor));
	}

	public void renderLight(int x, int y, int r) {
		// Applies offsets:
		lightOverlay.renderLight(x - xOffset, y - yOffset, r);
	}

	private static class LightOverlay {
		public final HashMap<@NotNull Point, @NotNull Integer> lights = new HashMap<>();

		/**
		 * Gets the overlay light darkness opacity instantly.
		 * @param currentLevel the current level index of the target
		 * @param darkFactor the tint factor to darken, from {@code 1} to {@code 128}
		 * @return opacity of darkness from {@code 0} to {@code 1}
		 */
		public double getOverlayOpacity(int currentLevel, double darkFactor) {
			// The above if statement is simply comparing the light level stored in oPixels with the minimum light level stored in dither. if it is determined that the oPixels[i] is less than the minimum requirements, the pixel is considered "dark", and the below is executed...
			if (currentLevel < 3) { // if in caves...
				// in the caves, not being lit means being pitch black.
				return 1;
			} else {
				// Outside the caves, not being lit simply means being darker.
				return darkFactor / 160; // darkens the color one shade.
			}
		}

		public void renderLight(int x, int y, int r) {
			lights.put(new Point(x, y), r);
		}

		public float[] getLights() {
			float[] data = new float[lights.size() * 3];
			int i = 0;
			for (Map.Entry<Point, Integer> light : lights.entrySet()) {
				data[i] = light.getKey().x;
				data[i + 1] = light.getKey().y;
				data[i + 2] = light.getValue();
				i += 3;
			}
			return data;
		}
	}
}
