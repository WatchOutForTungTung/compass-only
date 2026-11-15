package com.compassonly;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Paints the enlarged compass where the minimap used to be.
 *
 * Two of these are registered. The layered one is painted part-way through the game's own
 * interface drawing, at the point the minimap would have been painted, so the minimap frame
 * and the orbs end up on top of it exactly as they did with the minimap. The other is a
 * safety net that paints over the finished interface on any frame the layered one missed.
 */
class CompassOnlyOverlay extends Overlay
{
	private static final double JAU14_TO_RADIANS = Math.PI / 8192.0;

	// The game rotates the compass sprite about this pixel
	private static final double SPRITE_CENTER = 25.5;

	private static final Color OUTLINE_COLOR = new Color(20, 16, 10, 230);
	private static final double OUTLINE_WIDTH = 2;

	private static final double MIN_HOLE_COVERAGE = 0.4;
	private static final double MAX_HOLE_COVERAGE = 0.99;
	private static final int MASK_SIZE_TOLERANCE = 2;

	private final Client client;
	private final CompassOnlyPlugin compassPlugin;
	private final CompassOnlyConfig config;
	private final SpriteManager spriteManager;
	private final boolean layered;

	// Mask sprite id -> the hole it describes, or null if the sprite was not usable
	private final Map<Integer, Hole> holes = new HashMap<>();

	CompassOnlyOverlay(Client client, CompassOnlyPlugin plugin, CompassOnlyConfig config, SpriteManager spriteManager, boolean layered)
	{
		super(plugin);
		this.client = client;
		this.compassPlugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		this.layered = layered;

		setPosition(OverlayPosition.DYNAMIC);

		if (layered)
		{
			setLayer(OverlayLayer.MANUAL);
			// Resizable: the last piece drawn before the compass, the minimap and then the frame
			drawAfterLayer(InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_5);
			drawAfterLayer(InterfaceID.ToplevelPreEoc.MAP_NOCLICK_5);
			// Fixed: nothing usable comes before the frame, so go directly after it and before the orbs
			drawAfterLayer(InterfaceID.Toplevel.COMPASSCLICK);
		}
		else
		{
			setLayer(OverlayLayer.ABOVE_WIDGETS);
		}
	}

	@Override
	public String getName()
	{
		return layered ? "CompassOnlyOverlayLayered" : "CompassOnlyOverlayFallback";
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final CompassGeometry geo = compassPlugin.getGeometry();
		if (geo == null || compassPlugin.isCompassDrawn())
		{
			return null;
		}

		// A compass bigger than the minimap's space would be cut off by the frame and orbs
		// drawn after it, so leave it to the pass that draws over the finished interface
		if (layered && geo.isOversized())
		{
			return null;
		}

		final BufferedImage sprite = spriteManager.getSprite(SpriteID.COMPASS, 0);
		if (sprite == null)
		{
			return null;
		}

		final Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			draw(g, geo, sprite);
		}
		finally
		{
			g.dispose();
		}

		compassPlugin.setCompassDrawn(true);
		return null;
	}

	private void draw(Graphics2D g, CompassGeometry geo, BufferedImage sprite)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, config.smoothScaling()
			? RenderingHints.VALUE_INTERPOLATION_BILINEAR
			: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		if (!client.isResized())
		{
			prepareFixedMode(g, geo);
		}

		final double sourceRadius = geo.getSourceDiameter() / 2;
		final double scale = geo.getDiameter() / geo.getSourceDiameter();
		final double yaw = (client.getCameraYawTarget() & 0x3fff) * JAU14_TO_RADIANS;

		// From here on drawing is in compass sprite pixels, centred on the compass
		g.translate(geo.getCenterX(), geo.getCenterY());
		g.rotate(yaw);
		g.scale(scale, scale);

		final Ellipse2D dial = new Ellipse2D.Double(-sourceRadius, -sourceRadius, sourceRadius * 2, sourceRadius * 2);

		// The game treats the sprite as fully opaque, with its transparent pixels as black
		g.setColor(Color.BLACK);
		g.fill(dial);
		g.setPaint(new TexturePaint(sprite,
			new Rectangle2D.Double(-SPRITE_CENTER, -SPRITE_CENTER, sprite.getWidth(), sprite.getHeight())));
		g.fill(dial);

		// Keep the outline inside the dial so it never spills onto the minimap frame
		final double outlineWidth = OUTLINE_WIDTH / scale;
		final double outlineRadius = sourceRadius - outlineWidth / 2;
		g.setColor(OUTLINE_COLOR);
		g.setStroke(new BasicStroke((float) outlineWidth));
		g.draw(new Ellipse2D.Double(-outlineRadius, -outlineRadius, outlineRadius * 2, outlineRadius * 2));
	}

	/**
	 * In fixed mode the minimap and compass sit in holes cut out of the frame artwork, with
	 * nothing behind them. Once they are hidden those holes keep showing whatever was drawn
	 * there last, so paint them black, then confine the compass to the minimap's hole because
	 * here it is drawn on top of the frame rather than underneath it.
	 */
	private void prepareFixedMode(Graphics2D g, CompassGeometry geo)
	{
		final Rectangle minimapBounds = geo.getMinimapBounds();
		final Hole minimapHole = hole(geo.getMinimapMaskSprite(), minimapBounds);
		blackOut(g, minimapBounds, minimapHole);

		final Rectangle compassBounds = geo.getCompassBounds();
		if (compassBounds != null)
		{
			blackOut(g, compassBounds, hole(geo.getCompassMaskSprite(), compassBounds));
		}

		if (minimapHole != null && !geo.isOversized())
		{
			g.translate(minimapBounds.x, minimapBounds.y);
			g.clip(minimapHole.shape);
			g.translate(-minimapBounds.x, -minimapBounds.y);
		}
	}

	private static void blackOut(Graphics2D g, Rectangle bounds, Hole hole)
	{
		if (hole != null)
		{
			g.drawImage(hole.image, bounds.x, bounds.y, null);
		}
		else
		{
			g.setColor(Color.BLACK);
			g.fill(new Ellipse2D.Double(bounds.x, bounds.y, bounds.width, bounds.height));
		}
	}

	/**
	 * The minimap and compass widgets each carry a mask sprite whose shape is exactly the
	 * hole they show through.
	 */
	private Hole hole(int maskSpriteId, Rectangle bounds)
	{
		if (maskSpriteId < 0)
		{
			return null;
		}
		if (holes.containsKey(maskSpriteId))
		{
			return holes.get(maskSpriteId);
		}

		final BufferedImage mask = spriteManager.getSprite(maskSpriteId, 0);
		if (mask == null)
		{
			// Not loaded yet, try again next frame
			return null;
		}

		final Hole hole = Hole.fromMask(mask, bounds);
		holes.put(maskSpriteId, hole);
		return hole;
	}

	private static final class Hole
	{
		/** The hole filled solid black on a transparent background. */
		private final BufferedImage image;
		/** The outline of the hole, relative to the top left of the mask. */
		private final Area shape;

		private Hole(BufferedImage image, Area shape)
		{
			this.image = image;
			this.shape = shape;
		}

		private static Hole fromMask(BufferedImage mask, Rectangle bounds)
		{
			final int width = mask.getWidth();
			final int height = mask.getHeight();
			if (Math.abs(width - bounds.width) > MASK_SIZE_TOLERANCE || Math.abs(height - bounds.height) > MASK_SIZE_TOLERANCE)
			{
				return null;
			}

			// The corner of the sprite is always outside the hole, whichever way round the
			// mask happens to be stored. Each row of the hole runs from its first inside
			// pixel to its last.
			final boolean outsideIsOpaque = isOpaque(mask.getRGB(0, 0));
			final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			final Area shape = new Area();
			int covered = 0;

			for (int y = 0; y < height; y++)
			{
				int first = -1;
				int last = -1;
				for (int x = 0; x < width; x++)
				{
					if (isOpaque(mask.getRGB(x, y)) != outsideIsOpaque)
					{
						if (first == -1)
						{
							first = x;
						}
						last = x;
					}
				}

				if (first == -1)
				{
					continue;
				}

				final int length = last - first + 1;
				for (int x = first; x <= last; x++)
				{
					image.setRGB(x, y, 0xFF000000);
				}
				shape.add(new Area(new Rectangle(first, y, length, 1)));
				covered += length;
			}

			final double coverage = (double) covered / (width * height);
			if (coverage < MIN_HOLE_COVERAGE || coverage > MAX_HOLE_COVERAGE)
			{
				return null;
			}

			return new Hole(image, shape);
		}

		private static boolean isOpaque(int argb)
		{
			return (argb >>> 24) != 0;
		}
	}
}
