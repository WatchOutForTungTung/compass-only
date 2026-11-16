package com.compassonly;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.config.TooltipPositionType;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TooltipComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Draws the inventory enlarged. It copies the pixels the game has just drawn for the
 * inventory and scales them up, so everything shown there carries over: item counts,
 * the selected item, an item being dragged, and other plugins' markings.
 *
 * This is drawn right after the game's interface and before the right-click menu,
 * so menus still appear on top of it.
 *
 * Over the enlarged copy the game believes the mouse is over the real, smaller panel,
 * so RuneLite would draw tooltips there. This draws them at the real cursor instead.
 */
class InventoryScaleOverlay extends Overlay
{
	private final Client client;
	private final CompassOnlyPlugin compassPlugin;
	// Matches RuneLite's own tooltip placement
	private static final int TOOLTIP_UNDER_OFFSET = 24;
	private static final int TOOLTIP_PADDING = 2;

	private final InventoryMouseMapper mouseMapper;
	private final TooltipManager tooltipManager;
	private final RuneLiteConfig runeLiteConfig;

	private BufferedImage copy;
	private int tooltipWidth;
	private int tooltipHeight;

	InventoryScaleOverlay(Client client, CompassOnlyPlugin plugin, InventoryMouseMapper mouseMapper,
		TooltipManager tooltipManager, RuneLiteConfig runeLiteConfig)
	{
		super(plugin);
		this.client = client;
		this.compassPlugin = plugin;
		this.mouseMapper = mouseMapper;
		this.tooltipManager = tooltipManager;
		this.runeLiteConfig = runeLiteConfig;

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
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(copy, target.x, target.y, target.width, target.height, null);

		final Point cursor = mouseMapper.getMappedFrom();
		if (cursor != null && !client.isMenuOpen())
		{
			drawTooltipsAt(graphics, cursor);
		}
		return null;
	}

	/**
	 * Draws the tooltips RuneLite has collected this frame, the way RuneLite does, but at
	 * the real cursor, then clears them so they are not drawn a second time.
	 */
	private void drawTooltipsAt(Graphics2D graphics, Point cursor)
	{
		final List<Tooltip> tooltips = tooltipManager.getTooltips();
		if (tooltips.isEmpty())
		{
			return;
		}

		try
		{
			graphics.setFont(runeLiteConfig.tooltipFont().getFont());

			final int x = Math.min(client.getCanvasWidth() - tooltipWidth, cursor.x);
			final int y = runeLiteConfig.tooltipPosition() == TooltipPositionType.ABOVE_CURSOR
				? Math.max(0, cursor.y - tooltipHeight)
				: Math.min(client.getCanvasHeight() - tooltipHeight, cursor.y + TOOLTIP_UNDER_OFFSET);

			int width = 0;
			int height = 0;
			for (Tooltip tooltip : tooltips)
			{
				final LayoutableRenderableEntity entity;
				if (tooltip.getComponent() != null)
				{
					entity = tooltip.getComponent();
					if (entity instanceof PanelComponent)
					{
						((PanelComponent) entity).setBackgroundColor(runeLiteConfig.overlayBackgroundColor());
					}
				}
				else
				{
					final TooltipComponent component = new TooltipComponent();
					component.setModIcons(client.getModIcons());
					component.setText(tooltip.getText());
					component.setBackgroundColor(runeLiteConfig.overlayBackgroundColor());
					entity = component;
				}

				entity.setPreferredLocation(new Point(x, y + height));
				final Dimension size = entity.render(graphics);
				height += size.height + TOOLTIP_PADDING;
				width = Math.max(width, size.width);
			}

			tooltipWidth = width;
			tooltipHeight = height;
		}
		finally
		{
			tooltipManager.clear();
		}
	}
}
