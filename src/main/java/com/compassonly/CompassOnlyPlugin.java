package com.compassonly;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.util.IdentityHashMap;
import java.util.Map;
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
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Compass Only",
	description = "Big compass in place of the minimap, plus bigger side panels and a bigger, wider bank",
	tags = {"minimap", "compass", "hide", "hud", "immersion", "inventory", "prayer", "spellbook", "equipment", "bank", "scale", "resize", "zoom", "ui"}
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

	@Inject
	private MouseManager mouseManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private RuneLiteConfig runeLiteConfig;

	// Whether the minimap and compass have been changed and need putting back
	private boolean compassActive;

	private CompassOnlyOverlay layeredOverlay;
	private CompassOnlyOverlay fallbackOverlay;
	private InventoryScaleOverlay inventoryOverlay;
	private InventoryMouseMapper inventoryMouseMapper;
	private BankWidener bankWidener;

	/**
	 * Where the real and the enlarged inventory are, or null if it is not being enlarged.
	 * Read by the mouse mapper on the input thread, so replaced whole rather than changed.
	 */
	@Getter(AccessLevel.PACKAGE)
	private volatile InventoryGeometry inventoryGeometry;

	// The menu the game built for the real panel last frame, while the mouse was over its copy
	private SavedMenuEntry[] copyMenu;

	// Everything below is only touched on the client thread
	private int partsLayout = -1;
	private MinimapParts parts = new MinimapParts();
	// Every minimap widget moved into a compass spot, across all layouts, with how it was before
	private final Map<Widget, SavedLayout> movedMinimaps = new IdentityHashMap<>();

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

		bankWidener = new BankWidener(client);
		inventoryMouseMapper = new InventoryMouseMapper(client, this);
		inventoryOverlay = new InventoryScaleOverlay(client, this, inventoryMouseMapper, tooltipManager, runeLiteConfig);
		overlayManager.add(inventoryOverlay);
		mouseManager.registerMouseListener(inventoryMouseMapper);
		eventBus.register(copyMenuRecorder);
		mouseManager.registerMouseWheelListener(inventoryMouseMapper);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(layeredOverlay);
		overlayManager.remove(fallbackOverlay);
		layeredOverlay = null;
		fallbackOverlay = null;

		inventoryGeometry = null;
		eventBus.unregister(copyMenuRecorder);
		copyMenu = null;
		mouseManager.unregisterMouseListener(inventoryMouseMapper);
		mouseManager.unregisterMouseWheelListener(inventoryMouseMapper);
		overlayManager.remove(inventoryOverlay);
		inventoryMouseMapper = null;
		inventoryOverlay = null;

		final BankWidener widener = bankWidener;
		bankWidener = null;
		clientThread.invoke(widener::restore);
		clientThread.invoke(this::restoreWidgets);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CompassOnlyConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (CompassOnlyConfig.MINIMAP_IN_COMPASS.equals(event.getKey()) && !config.minimapInCompass())
		{
			clientThread.invoke(this::restoreAllMovedMinimaps);
		}
		else if (CompassOnlyConfig.HIDE_FRAME.equals(event.getKey()) && !config.hideFrame())
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
			inventoryGeometry = null;
			return;
		}

		// Widen the bank first, so its enlarged copy is sized from the widened window
		bankWidener.update(config.bankColumns());

		// The bank hides the normal inventory while it is open, so only one is ever enlarged
		final InventoryGeometry bank = computeBankGeometry();
		inventoryGeometry = bank != null ? bank : computeSidePanelGeometry();

		if (!config.showCompass())
		{
			if (compassActive)
			{
				restoreWidgets();
				compassActive = false;
			}
			return;
		}
		compassActive = true;

		locateParts();
		if (parts.minimap == null)
		{
			return;
		}

		hide(parts.compass);
		if (config.hideFrame())
		{
			hide(parts.frame);
		}

		final boolean swap = config.minimapInCompass() && canSwap();
		if (swap)
		{
			moveMinimapIntoCompassSpot();
			// The game's compass click area sits on top of that spot and would swallow
			// clicks meant for the minimap. The big compass has the same options.
			hide(parts.compassClick);
		}
		else
		{
			restoreMinimap(parts.minimap);
			hide(parts.minimap);
			show(parts.compassClick);
		}

		geometry = computeGeometry(swap);
	}

	/**
	 * Enlarges the whole side panel, frame and all, while a tab with a size setting is
	 * open. It grows from the panel's bottom-right corner, which sits against the edge of
	 * the window in every layout, and is kept inside the window.
	 */
	private InventoryGeometry computeSidePanelGeometry()
	{
		final int percent = openTabSize();
		if (percent <= CompassOnlyConfig.MIN_INVENTORY_SIZE)
		{
			return null;
		}

		final Rectangle source = sidePanelBounds();
		if (source == null || source.isEmpty())
		{
			return null;
		}

		final double scale = Math.min(percent, CompassOnlyConfig.MAX_INVENTORY_SIZE) / 100.0;
		final int width = (int) Math.round(source.width * scale);
		final int height = (int) Math.round(source.height * scale);
		final int x = Math.max(0, source.x + source.width - width);
		final int y = Math.max(0, source.y + source.height - height);
		return new InventoryGeometry(source, new Rectangle(x, y, width, height));
	}

	/**
	 * The size setting for the side tab that is open, or 100 if it has none. Each tab is
	 * its own interface, hidden while another tab is open. The bank shows its own copy
	 * of the inventory, so the inventory tab counts as closed while the bank is open.
	 */
	private int openTabSize()
	{
		if (isOpen(InterfaceID.Inventory.ITEMS))
		{
			return config.inventorySize();
		}
		if (isOpen(InterfaceID.PRAYERBOOK << 16))
		{
			return config.prayerSize();
		}
		if (isOpen(InterfaceID.COMBAT_INTERFACE << 16))
		{
			return config.combatSize();
		}
		if (isOpen(InterfaceID.WORNITEMS << 16))
		{
			return config.equipmentSize();
		}
		if (isOpen(InterfaceID.MAGIC_SPELLBOOK << 16))
		{
			return config.spellbookSize();
		}
		return CompassOnlyConfig.MIN_INVENTORY_SIZE;
	}

	private boolean isOpen(int componentId)
	{
		final Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}

	/**
	 * The side panel with its stone frame, in canvas pixels. In the classic layouts that
	 * includes the rows of tab buttons, so those are enlarged and clickable too.
	 */
	private Rectangle sidePanelBounds()
	{
		switch (client.getTopLevelInterfaceId())
		{
			case InterfaceID.TOPLEVEL_OSRS_STRETCH:
				return visibleBounds(InterfaceID.ToplevelOsrsStretch.SIDE_MENU);
			case InterfaceID.TOPLEVEL_PRE_EOC:
				return visibleBounds(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);
			case InterfaceID.TOPLEVEL:
			{
				final Rectangle panel = visibleBounds(InterfaceID.Toplevel.SIDE);
				final Rectangle top = visibleBounds(InterfaceID.Toplevel.SIDE_TOP_CONTAINER);
				final Rectangle bottom = visibleBounds(InterfaceID.Toplevel.SIDE_BOTTOM_CONTAINER);
				if (panel == null || top == null || bottom == null)
				{
					return null;
				}
				return panel.union(top).union(bottom);
			}
			default:
				return null;
		}
	}

	private Rectangle visibleBounds(int componentId)
	{
		final Widget widget = client.getWidget(componentId);
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		final Rectangle bounds = widget.getBounds();
		return bounds == null || bounds.isEmpty() ? null : new Rectangle(bounds);
	}

	/**
	 * Grows the bank window outwards from its centre, by as much as fits in the game window.
	 */
	private InventoryGeometry computeBankGeometry()
	{
		final int percent = config.bankSize();
		if (percent <= CompassOnlyConfig.MIN_BANK_SIZE)
		{
			return null;
		}

		final Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (bank == null || bank.isHidden())
		{
			return null;
		}

		final Rectangle source = bank.getBounds();
		if (source == null || source.isEmpty())
		{
			return null;
		}

		final int canvasWidth = client.getCanvasWidth();
		final int canvasHeight = client.getCanvasHeight();
		double scale = Math.min(percent, CompassOnlyConfig.MAX_BANK_SIZE) / 100.0;
		scale = Math.min(scale, Math.min((double) canvasWidth / source.width, (double) canvasHeight / source.height));
		if (scale <= 1)
		{
			return null;
		}

		final int width = (int) Math.round(source.width * scale);
		final int height = (int) Math.round(source.height * scale);
		final int x = (int) clamp(source.getCenterX() - width / 2.0, 0, canvasWidth - width);
		final int y = (int) clamp(source.getCenterY() - height / 2.0, 0, canvasHeight - height);
		return new InventoryGeometry(new Rectangle(source), new Rectangle(x, y, width, height));
	}

	/**
	 * The minimap can only take over the compass's spot if both live in the same
	 * container, so the compass's position means the same thing for the minimap.
	 */
	private boolean canSwap()
	{
		return parts.compass != null
			&& parts.compass.getParentId() == parts.minimap.getParentId()
			&& parts.compass.getWidth() > 0
			&& parts.compass.getHeight() > 0;
	}

	/**
	 * Moves the game's own minimap into the small compass's hole and shrinks it to fit,
	 * so it keeps everything the real minimap does: dots, click to walk, scroll to zoom.
	 * Its shape comes from the widget's mask sprite, so it takes the compass's mask too.
	 */
	private void moveMinimapIntoCompassSpot()
	{
		final Widget minimap = parts.minimap;
		final Widget compass = parts.compass;

		if (!movedMinimaps.containsKey(minimap))
		{
			movedMinimaps.put(minimap, SavedLayout.of(minimap));
		}

		show(minimap);

		final int x = compass.getRelativeX();
		final int y = compass.getRelativeY();
		final int width = compass.getWidth();
		final int height = compass.getHeight();
		final int mask = compass.getSpriteId();

		if (minimap.getRelativeX() == x && minimap.getRelativeY() == y
			&& minimap.getWidth() == width && minimap.getHeight() == height
			&& minimap.getSpriteId() == mask)
		{
			return;
		}

		minimap.setWidthMode(WidgetSizeMode.ABSOLUTE);
		minimap.setHeightMode(WidgetSizeMode.ABSOLUTE);
		minimap.setOriginalWidth(width);
		minimap.setOriginalHeight(height);
		minimap.setSpriteId(mask);
		// Unlike a normal position, a forced one survives the game re-laying out the interface
		minimap.setForcedPosition(x, y);
		minimap.revalidate();
	}

	private void restoreMinimap(Widget minimap)
	{
		final SavedLayout saved = movedMinimaps.remove(minimap);
		if (saved != null)
		{
			saved.applyTo(minimap);
		}
	}

	private void restoreAllMovedMinimaps()
	{
		movedMinimaps.forEach((widget, saved) -> saved.applyTo(widget));
		movedMinimaps.clear();
	}

	/**
	 * Gives the enlarged compass the same click options as the game's own compass.
	 */
	/**
	 * While the mouse is over an enlarged panel, the game is told it is over the real one,
	 * so the menu it builds each frame has the options for what is under the mouse there:
	 * Use, Drop, Eat and so on. Keep a copy, made after other plugins have changed the menu.
	 */
	private final Object copyMenuRecorder = new Object()
	{
		// RuneLite allows one handler per event per object, named after the event, so
		// this runs on its own to get its own, late, place in the order
		@Subscribe(priority = -100)
		public void onPostMenuSort(PostMenuSort event)
		{
			final InventoryMouseMapper mapper = inventoryMouseMapper;
			// After a right-click on the copy the game rebuilds the menu for whatever is
			// behind it, so keep the copy made before the click
			if (mapper == null || client.isMenuOpen() || mapper.isRightClickPending())
			{
				return;
			}
			copyMenu = mapper.getMappedFrom() != null ? SavedMenuEntry.copyOf(client.getMenu().getMenuEntries()) : null;
		}
	};

	/**
	 * A right-click on an enlarged panel is passed to the game where it really happened, so
	 * the menu opens at the cursor. The game fills that menu for whatever is behind the
	 * enlarged panel, so put back the options for what was under the mouse on the real one.
	 */
	@Subscribe(priority = 100)
	public void onMenuOpened(MenuOpened event)
	{
		final InventoryMouseMapper mapper = inventoryMouseMapper;
		final SavedMenuEntry[] saved = copyMenu;
		if (mapper == null || !mapper.takeRightClickOnCopy() || saved == null || saved.length == 0)
		{
			return;
		}

		final Menu menu = client.getMenu();
		SavedMenuEntry.restoreInto(menu, saved);
		event.setMenuEntries(menu.getMenuEntries());
	}

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

		// The small minimap overlaps the edge of the compass, and clicks there are for walking
		final Rectangle smallMinimap = geo.getSmallMinimapBounds();
		if (smallMinimap != null && smallMinimap.contains(mouse.getX(), mouse.getY()))
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

		// Widgets replaced by an interface reload are gone, so there is nothing to put back
		movedMinimaps.keySet().removeIf(widget -> !isCurrent(widget));
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

		found.compassClick = client.getWidget(compassClickId(layout));

		return found;
	}

	private static int compassClickId(int layout)
	{
		switch (layout)
		{
			case InterfaceID.TOPLEVEL:
				return InterfaceID.Toplevel.COMPASSCLICK;
			case InterfaceID.TOPLEVEL_OSRS_STRETCH:
				return InterfaceID.ToplevelOsrsStretch.COMPASSCLICK;
			case InterfaceID.TOPLEVEL_PRE_EOC:
				return InterfaceID.ToplevelPreEoc.COMPASSCLICK;
			default:
				return -1;
		}
	}

	private CompassGeometry computeGeometry(boolean swapped)
	{
		final Widget minimap = parts.minimap;

		// If the game's own setting or another plugin has hidden the whole minimap block,
		// orbs get rearranged into this space, so stay out of the way
		final Widget container = minimap.getParent();
		if (container == null || container.isHidden())
		{
			return null;
		}

		// The big compass goes where the minimap normally is, not where it has been moved to
		final SavedLayout saved = movedMinimaps.get(minimap);
		final Rectangle minimapBounds = saved != null ? saved.homeBounds(minimap) : canvasBounds(minimap);
		final int minimapMask = saved != null ? saved.spriteId : minimap.getSpriteId();
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
			minimapMask,
			compassBounds,
			compassMask,
			centerX,
			centerY,
			diameter,
			sourceDiameter,
			oversized,
			swapped ? canvasBounds(minimap) : null);
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
		restoreAllMovedMinimaps();

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
		show(target.compassClick);
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
		private Widget compassClick;
	}

	/**
	 * How a minimap widget was laid out before it was moved, so it can be put back.
	 */
	private static final class SavedLayout
	{
		private final int originalX;
		private final int originalY;
		private final int originalWidth;
		private final int originalHeight;
		private final int xMode;
		private final int yMode;
		private final int widthMode;
		private final int heightMode;
		private final int spriteId;

		private SavedLayout(Widget widget)
		{
			originalX = widget.getOriginalX();
			originalY = widget.getOriginalY();
			originalWidth = widget.getOriginalWidth();
			originalHeight = widget.getOriginalHeight();
			xMode = widget.getXPositionMode();
			yMode = widget.getYPositionMode();
			widthMode = widget.getWidthMode();
			heightMode = widget.getHeightMode();
			spriteId = widget.getSpriteId();
		}

		static SavedLayout of(Widget widget)
		{
			return new SavedLayout(widget);
		}

		void applyTo(Widget widget)
		{
			widget.setForcedPosition(-1, -1);
			widget.setWidthMode(widthMode);
			widget.setHeightMode(heightMode);
			widget.setOriginalWidth(originalWidth);
			widget.setOriginalHeight(originalHeight);
			widget.setXPositionMode(xMode);
			widget.setYPositionMode(yMode);
			widget.setOriginalX(originalX);
			widget.setOriginalY(originalY);
			widget.setSpriteId(spriteId);
			widget.revalidate();
		}

		/**
		 * Where the widget would be on the canvas if it had not been moved, worked out from
		 * its original layout and its container's current size.
		 */
		Rectangle homeBounds(Widget widget)
		{
			final Widget parent = widget.getParent();
			if (parent == null || widthMode != WidgetSizeMode.ABSOLUTE || heightMode != WidgetSizeMode.ABSOLUTE)
			{
				return new Rectangle();
			}

			final Rectangle parentBounds = canvasBounds(parent);
			final int x = position(xMode, originalX, parentBounds.width, originalWidth);
			final int y = position(yMode, originalY, parentBounds.height, originalHeight);
			return new Rectangle(parentBounds.x + x, parentBounds.y + y, originalWidth, originalHeight);
		}

		private static int position(int mode, int offset, int parentSize, int size)
		{
			switch (mode)
			{
				case WidgetPositionMode.ABSOLUTE_CENTER:
					return (parentSize - size) / 2 + offset;
				case WidgetPositionMode.ABSOLUTE_RIGHT:
					return parentSize - size - offset;
				default:
					return offset;
			}
		}
	}
}
