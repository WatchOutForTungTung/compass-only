package com.compassonly;

import com.google.inject.Provides;
import java.awt.Rectangle;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Compass Only",
	description = "Hides the minimap and shows an enlarged compass in its place",
	tags = {"minimap", "compass", "hide", "hud", "immersion"}
)
public class CompassOnlyPlugin extends Plugin
{
	// The game marks the widgets it paints the minimap and the compass into with these
	private static final int CONTENT_TYPE_MINIMAP = 1338;
	private static final int CONTENT_TYPE_COMPASS = 1339;

	// Fixed, resizable classic and resizable modern
	private static final int[] LAYOUTS = {
		InterfaceID.TOPLEVEL,
		InterfaceID.TOPLEVEL_OSRS_STRETCH,
		InterfaceID.TOPLEVEL_PRE_EOC
	};

	private static final int MAX_COMPONENTS = 400;
	private static final int MAX_DEPTH = 32;
	private static final double DEFAULT_SOURCE_DIAMETER = 33;
	private static final int MIN_SIZE_PERCENT = CompassOnlyConfig.MIN_SIZE;
	private static final int MAX_SIZE_PERCENT = CompassOnlyConfig.MAX_SIZE;

	// Camera yaw is measured in 1/16384ths of a turn, increasing anticlockwise from north
	private static final int YAW_NORTH = 0;
	private static final int YAW_WEST = 4096;
	private static final int YAW_SOUTH = 8192;
	private static final int YAW_EAST = 12288;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CompassOnlyConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SpriteManager spriteManager;

	private CompassOnlyOverlay layeredOverlay;
	private CompassOnlyOverlay fallbackOverlay;

	// Everything below is only touched on the client thread
	private int partsLayout = -1;
	private MinimapParts parts = new MinimapParts();

	/**
	 * Where the compass belongs on the frame being drawn, or null if it should not be drawn.
	 */
	@Getter(AccessLevel.PACKAGE)
	private CompassGeometry geometry;

	/**
	 * Whether the compass has been painted on the frame being drawn. Outside of rendering
	 * this still holds the answer for the last frame.
	 */
	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private boolean compassDrawn;

	@Provides
	CompassOnlyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CompassOnlyConfig.class);
	}

	@Override
	protected void startUp()
	{
		layeredOverlay = new CompassOnlyOverlay(client, this, config, spriteManager, true);
		fallbackOverlay = new CompassOnlyOverlay(client, this, config, spriteManager, false);
		overlayManager.add(layeredOverlay);
		overlayManager.add(fallbackOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(layeredOverlay);
		overlayManager.remove(fallbackOverlay);
		layeredOverlay = null;
		fallbackOverlay = null;
		clientThread.invoke(this::restoreWidgets);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CompassOnlyConfig.GROUP.equals(event.getGroup())
			&& CompassOnlyConfig.HIDE_FRAME.equals(event.getKey())
			&& !config.hideFrame())
		{
			clientThread.invoke(() ->
			{
				for (int layout : LAYOUTS)
				{
					show(findParts(layout).frame);
				}
			});
		}
	}

	/**
	 * The game's scripts un-hide interface pieces whenever they rebuild the layout, so the
	 * minimap is re-hidden right before every frame rather than once.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		compassDrawn = false;
		geometry = null;

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		locateParts();
		if (parts.minimap == null)
		{
			return;
		}

		hide(parts.minimap);
		hide(parts.compass);
		if (config.hideFrame())
		{
			hide(parts.frame);
		}

		geometry = computeGeometry();
	}

	/**
	 * Gives the enlarged compass the same click options as the game's own compass.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		final CompassGeometry geo = geometry;
		if (geo == null || !compassDrawn || !config.lookOptions()
			|| client.isMenuOpen() || client.isWidgetSelected())
		{
			return;
		}

		final Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || !geo.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		// Leave the menu alone if the mouse is over something of its own, such as an
		// orb or a window that happens to sit on top of the compass
		final Menu menu = client.getMenu();
		final MenuEntry[] entries = menu.getMenuEntries();
		if (entries.length > 0)
		{
			final MenuAction top = entries[entries.length - 1].getType();
			if (top != MenuAction.CANCEL && top != MenuAction.WALK)
			{
				return;
			}
		}

		// Each entry is added above the previous one, and the topmost is the left-click action
		addLookOption(menu, "Look West", YAW_WEST);
		addLookOption(menu, "Look East", YAW_EAST);
		addLookOption(menu, "Look South", YAW_SOUTH);
		addLookOption(menu, "Look North", YAW_NORTH);
	}

	private void addLookOption(Menu menu, String option, int yaw)
	{
		menu.createMenuEntry(-1)
			.setOption(option)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> client.setCameraYawTarget(yaw));
	}

	private void locateParts()
	{
		final int layout = client.getTopLevelInterfaceId();
		if (layout == partsLayout && isCurrent(parts.minimap))
		{
			return;
		}

		partsLayout = layout;
		parts = findParts(layout);
	}

	/**
	 * Widgets are rebuilt when an interface reloads, which leaves old references pointing at nothing.
	 */
	private boolean isCurrent(Widget widget)
	{
		return widget != null && client.getWidget(widget.getId()) == widget;
	}

	/**
	 * Finds the minimap pieces by what they are rather than by component id, so a game
	 * update that renumbers the interface does not make the plugin hide the wrong thing.
	 */
	private MinimapParts findParts(int layout)
	{
		final MinimapParts found = new MinimapParts();
		if (layout < 0)
		{
			return found;
		}

		for (int child = 0; child < MAX_COMPONENTS; child++)
		{
			final Widget widget = client.getWidget(layout, child);
			if (widget == null)
			{
				continue;
			}

			final int contentType = widget.getContentType();
			if (contentType == CONTENT_TYPE_MINIMAP)
			{
				found.minimap = widget;
			}
			else if (contentType == CONTENT_TYPE_COMPASS)
			{
				found.compass = widget;
			}
			else if (widget.getSpriteId() == SpriteID.OSRS_STRETCH_MAPSURROUND)
			{
				found.frame = widget;
			}
		}

		if (found.frame != null
			&& (found.minimap == null || found.frame.getParentId() != found.minimap.getParentId()))
		{
			found.frame = null;
		}

		return found;
	}

	private CompassGeometry computeGeometry()
	{
		final Widget minimap = parts.minimap;

		// If the game's own setting or another plugin has hidden the whole minimap block,
		// orbs get rearranged into this space, so stay out of the way
		final Widget container = minimap.getParent();
		if (container == null || container.isHidden())
		{
			return null;
		}

		final Rectangle minimapBounds = canvasBounds(minimap);
		if (minimapBounds.isEmpty())
		{
			return null;
		}

		Rectangle compassBounds = null;
		int compassMask = -1;
		double sourceDiameter = DEFAULT_SOURCE_DIAMETER;
		if (parts.compass != null)
		{
			compassBounds = canvasBounds(parts.compass);
			compassMask = parts.compass.getSpriteId();
			if (!compassBounds.isEmpty())
			{
				sourceDiameter = Math.min(compassBounds.width, compassBounds.height);
			}
		}

		final int sizePercent = Math.max(MIN_SIZE_PERCENT, Math.min(MAX_SIZE_PERCENT, config.compassSize()));
		final boolean oversized = sizePercent > 100;
		double diameter = Math.min(minimapBounds.width, minimapBounds.height) * sizePercent / 100.0;
		double centerX = minimapBounds.getCenterX();
		double centerY = minimapBounds.getCenterY();

		if (oversized)
		{
			// Grow from the minimap's centre, but slide back inwards so none of it is
			// pushed off the edge of the game window
			final int canvasWidth = client.getCanvasWidth();
			final int canvasHeight = client.getCanvasHeight();
			diameter = Math.min(diameter, Math.min(canvasWidth, canvasHeight));
			final double radius = diameter / 2;
			centerX = clamp(centerX, radius, canvasWidth - radius);
			centerY = clamp(centerY, radius, canvasHeight - radius);
		}

		return new CompassGeometry(
			minimapBounds,
			minimap.getSpriteId(),
			compassBounds,
			compassMask,
			centerX,
			centerY,
			diameter,
			sourceDiameter,
			oversized);
	}

	private static double clamp(double value, double min, double max)
	{
		return max < min ? (min + max) / 2 : Math.max(min, Math.min(max, value));
	}

	/**
	 * A hidden widget's reported canvas location goes stale because the game only updates it
	 * while drawing, so add up the offset within each parent instead.
	 */
	private static Rectangle canvasBounds(Widget widget)
	{
		int x = 0;
		int y = 0;
		Widget current = widget;
		for (int depth = 0; current != null && depth < MAX_DEPTH; depth++)
		{
			x += current.getRelativeX();
			y += current.getRelativeY();
			current = current.getParent();
		}
		return new Rectangle(x, y, widget.getWidth(), widget.getHeight());
	}

	private void restoreWidgets()
	{
		// A layout that was used earlier stays loaded with its pieces still hidden, so
		// restore all of them and not only the one on screen
		for (int layout : LAYOUTS)
		{
			restore(findParts(layout));
		}
		restore(parts);

		parts = new MinimapParts();
		partsLayout = -1;
		geometry = null;
		compassDrawn = false;
	}

	private static void restore(MinimapParts target)
	{
		show(target.minimap);
		show(target.compass);
		show(target.frame);
	}

	private static void hide(Widget widget)
	{
		if (widget != null && !widget.isSelfHidden())
		{
			widget.setHidden(true);
		}
	}

	private static void show(Widget widget)
	{
		if (widget != null && widget.isSelfHidden())
		{
			widget.setHidden(false);
		}
	}

	private static final class MinimapParts
	{
		private Widget minimap;
		private Widget compass;
		private Widget frame;
	}
}
