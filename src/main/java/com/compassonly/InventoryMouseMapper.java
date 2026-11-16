package com.compassonly;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * Makes the enlarged inventory clickable by moving the mouse, as far as the game can
 * tell, onto the matching spot of the real inventory underneath.
 *
 * Right-clicks are left where they are. The game builds the right-click menu from what
 * the mouse was last over, which is already the right item, and opens it where the
 * right-click happened, so the menu appears at the cursor and not over the small
 * inventory. While a menu is open nothing is moved, so its options can be clicked.
 */
class InventoryMouseMapper extends MouseAdapter implements MouseWheelListener
{
	private final Client client;
	private final CompassOnlyPlugin plugin;

	// Set while a left-button press that started on the enlarged inventory is held, so a
	// dragged item keeps following the mouse even if it leaves the enlarged area
	private boolean holding;

	// Where the mouse really is, while it is being mapped onto the real inventory
	private volatile java.awt.Point mappedFrom;

	// When a right-click on the enlarged copy happened, until the menu it opens has been
	// fixed up. A menu opens within a frame or two, so an old one is ignored.
	private static final long RIGHT_CLICK_WINDOW_MS = 1000;
	private volatile long rightClickedCopyAt;

	InventoryMouseMapper(Client client, CompassOnlyPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (event.getButton() == MouseEvent.BUTTON3)
		{
			// Left where it is so the menu opens at the cursor. The plugin then swaps in
			// the options for what the mouse was over on the real panel.
			rightClickedCopyAt = mappable(event) != null ? System.currentTimeMillis() : 0;
			return event;
		}
		rightClickedCopyAt = 0;

		final InventoryGeometry geo = mappable(event);
		if (geo == null)
		{
			return event;
		}

		holding = true;
		return map(event, geo);
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (event.getButton() == MouseEvent.BUTTON3)
		{
			return event;
		}

		final InventoryGeometry geo = plugin.getInventoryGeometry();
		if (holding)
		{
			holding = false;
			return geo != null ? map(event, geo) : event;
		}

		final InventoryGeometry inside = mappable(event);
		return inside != null ? map(event, inside) : event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		if (event.getButton() == MouseEvent.BUTTON3)
		{
			return event;
		}

		final InventoryGeometry geo = mappable(event);
		return geo != null ? map(event, geo) : event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		final MouseEvent result = mouseDraggedMapped(event);
		mappedFrom = result != event ? event.getPoint() : null;
		return result;
	}

	private MouseEvent mouseDraggedMapped(MouseEvent event)
	{
		if (SwingUtilities.isRightMouseButton(event))
		{
			return event;
		}

		final InventoryGeometry geo = plugin.getInventoryGeometry();
		if (geo == null)
		{
			holding = false;
			return event;
		}

		if (holding || geo.getTarget().contains(event.getX(), event.getY()))
		{
			return map(event, geo);
		}
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		holding = false;
		final InventoryGeometry geo = mappable(event);
		mappedFrom = geo != null ? event.getPoint() : null;
		return geo != null ? map(event, geo) : event;
	}

	/**
	 * Whether the menu being opened came from a right-click on the enlarged copy. Answers
	 * once per right-click.
	 */
	boolean takeRightClickOnCopy()
	{
		final boolean was = isRightClickPending();
		rightClickedCopyAt = 0;
		return was;
	}

	/**
	 * Whether a right-click on the enlarged copy is waiting for its menu to open. Until
	 * then the game sees the mouse where the click really was, over whatever is behind.
	 */
	boolean isRightClickPending()
	{
		final long at = rightClickedCopyAt;
		return at != 0 && System.currentTimeMillis() - at < RIGHT_CLICK_WINDOW_MS;
	}

	/**
	 * Where the mouse really is, if it is currently over the enlarged copy, or null.
	 */
	java.awt.Point getMappedFrom()
	{
		return plugin.getInventoryGeometry() != null ? mappedFrom : null;
	}

	/**
	 * Scrolling, so the bank scrolls when the wheel is used anywhere over its enlarged copy.
	 */
	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		final InventoryGeometry geo = mappable(event);
		if (geo == null)
		{
			return event;
		}

		return new MouseWheelEvent((Component) event.getSource(), event.getID(), event.getWhen(),
			event.getModifiersEx(), mapX(event, geo), mapY(event, geo), event.getXOnScreen(), event.getYOnScreen(),
			event.getClickCount(), event.isPopupTrigger(), event.getScrollType(), event.getScrollAmount(),
			event.getWheelRotation(), event.getPreciseWheelRotation());
	}

	/**
	 * The current layout, if this event is over the enlarged inventory and should be moved.
	 */
	private InventoryGeometry mappable(MouseEvent event)
	{
		final InventoryGeometry geo = plugin.getInventoryGeometry();
		if (geo == null || client.isMenuOpen())
		{
			return null;
		}
		return geo.getTarget().contains(event.getX(), event.getY()) ? geo : null;
	}

	private static MouseEvent map(MouseEvent event, InventoryGeometry geo)
	{
		return new MouseEvent((Component) event.getSource(), event.getID(), event.getWhen(),
			event.getModifiersEx(), mapX(event, geo), mapY(event, geo), event.getClickCount(), event.isPopupTrigger(), event.getButton());
	}

	// Clamped so a drag that leaves the enlarged area stays at the edge of the real one
	private static int mapX(MouseEvent event, InventoryGeometry geo)
	{
		return clamp(geo.toSourceX(event.getX()), geo.getSource().x, geo.getSource().x + geo.getSource().width - 1);
	}

	private static int mapY(MouseEvent event, InventoryGeometry geo)
	{
		return clamp(geo.toSourceY(event.getY()), geo.getSource().y, geo.getSource().y + geo.getSource().height - 1);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
