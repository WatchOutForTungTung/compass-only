package com.compassonly;

import java.awt.Component;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.input.MouseAdapter;

/**
 * Makes the enlarged inventory clickable by moving the mouse, as far as the game can
 * tell, onto the matching spot of the real inventory underneath.
 *
 * Right-clicks are left where they are. The game builds the right-click menu from what
 * the mouse was last over, which is already the right item, and opens it where the
 * right-click happened, so the menu appears at the cursor and not over the small
 * inventory. While a menu is open nothing is moved, so its options can be clicked.
 */
class InventoryMouseMapper extends MouseAdapter
{
	private final Client client;
	private final CompassOnlyPlugin plugin;

	// Set while a left-button press that started on the enlarged inventory is held, so a
	// dragged item keeps following the mouse even if it leaves the enlarged area
	private boolean holding;

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
			return event;
		}

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
		return geo != null ? map(event, geo) : event;
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
		// Clamp so a drag that leaves the enlarged area stays at the edge of the real one
		final int x = clamp(geo.toSourceX(event.getX()), geo.getSource().x, geo.getSource().x + geo.getSource().width - 1);
		final int y = clamp(geo.toSourceY(event.getY()), geo.getSource().y, geo.getSource().y + geo.getSource().height - 1);

		return new MouseEvent((Component) event.getSource(), event.getID(), event.getWhen(),
			event.getModifiersEx(), x, y, event.getClickCount(), event.isPopupTrigger(), event.getButton());
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
