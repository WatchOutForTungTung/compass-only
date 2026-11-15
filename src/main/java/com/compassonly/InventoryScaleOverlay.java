package com.compassonly;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the inventory enlarged. It copies the pixels the game has just drawn for the
 * inventory and scales them up, so everything shown there carries over: item counts,
 * the selected item, an item being dragged, and other plugins' markings.
 *
 * This is drawn right after the game's interface and before the right-click menu,
 * so menus still appear on top of it.
 */
class InventoryScaleOverlay extends Overlay
{
	private final Client client;
	private final CompassOnlyPlugin compassPlugin;
	private final CompassOnlyConfig config;

	private BufferedImage copy;

	InventoryScaleOverlay(Client client, CompassOnlyPlugin plugin, CompassOnlyConfig config)
	{
		super(plugin);
		this.client = client;
		this.compassPlugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		// Among dynamic overlays a higher priority is drawn later, so other plugins'
		// inventory markings are in place before the copy is taken
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final InventoryGeometry geo = compassPlugin.getInventoryGeometry();
		if (geo == null)
		{
			return null;
		}

		final BufferProvider buffer = client.getBufferProvider();
		if (buffer == null)
		{
			return null;
		}

		final Rectangle source = geo.getSource();
		final int bufferWidth = buffer.getWidth();
		if (source.x < 0 || source.y < 0
			|| source.x + source.width > bufferWidth
			|| source.y + source.height > buffer.getHeight())
		{
			return null;
		}

		if (copy == null || copy.getWidth() != source.width || copy.getHeight() != source.height)
		{
			copy = new BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB);
		}

		// Copy first: the enlarged image is drawn over the same pixels it is read from
		final int[] pixels = buffer.getPixels();
		final int[] copyPixels = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
		for (int row = 0; row < source.height; row++)
		{
			System.arraycopy(pixels, (source.y + row) * bufferWidth + source.x, copyPixels, row * source.width, source.width);
		}

		final Rectangle target = geo.getTarget();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, config.smoothScaling()
			? RenderingHints.VALUE_INTERPOLATION_BILINEAR
			: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(copy, target.x, target.y, target.width, target.height, null);
		return null;
	}
}
