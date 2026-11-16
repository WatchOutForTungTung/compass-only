package com.compassonly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;

/**
 * Makes the bank wider so it shows more items per row.
 *
 * The game's bank script always lays items out 8 to a row in a fixed-width area, and
 * resets that width every time it rebuilds the bank. So this widens the window and then
 * moves the item slots into a wider grid itself, again after every rebuild. It is still
 * the real bank, so clicking and dragging items work as normal.
 */
class BankWidener
{
	static final int DEFAULT_COLUMNS = 8;

	// Taken from the game's bank layout script
	private static final int SLOT_WIDTH = 36;
	private static final int SLOT_HEIGHT = 32;
	private static final int ROW_HEIGHT = 36;
	private static final int COLUMN_SPACING = 48;
	private static final int FIRST_COLUMN_X = 51;
	private static final int SCROLL_PADDING = 8;
	private static final int DIVIDER_LINE_HEIGHT = 2;
	private static final int DIVIDER_LABEL_HEIGHT = 15;
	// Space the game leaves beside the dividers: 51 on the left and 35 on the right
	private static final int DIVIDER_SIDE_SPACE = 86;

	// Taken from RuneLite's Bank Tags plugin, which adds its tabs to the bank
	private static final int TAG_TAB_FIRST_CHILD = 4;
	private static final int TAG_TAB_WIDTH = 39;
	private static final int TAG_TAB_HEIGHT = 40;
	private static final int TAG_TAB_MARGIN = 1;
	private static final int TAG_ICON_INSET_X = 3;
	private static final int TAG_ICON_INSET_Y = 4;

	private static final Comparator<Widget> TOP_TO_BOTTOM = Comparator
		.comparingInt(Widget::getOriginalY)
		.thenComparingInt(Widget::getOriginalX);

	private final Client client;

	// The bank's widths before they were changed, or -1 while it is untouched
	private int baseWindowWidth = -1;
	private int baseItemsWidth = -1;
	private int appliedColumns = DEFAULT_COLUMNS;

	// The game's shared window box, widened while the bank is, and its normal width
	private Widget modalWidget;
	private int baseModalWidth = -1;

	private boolean tabRowApplied;
	private boolean hidTagArrows;

	BankWidener(Client client)
	{
		this.client = client;
	}

	/**
	 * Called before every frame. Cheap unless the bank has just been rebuilt.
	 */
	void update(int requestedColumns)
	{
		final Widget window = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		final Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (window == null || items == null || window.isHidden())
		{
			// The bank has closed, so give other windows their normal space back
			restoreModal();
			baseWindowWidth = -1;
			baseItemsWidth = -1;
			appliedColumns = DEFAULT_COLUMNS;
			tabRowApplied = false;
			hidTagArrows = false;
			return;
		}

		// The bank sits inside a fixed-width box the game uses for all of its windows,
		// which would cut off anything wider. That box is centred in the space left of
		// the side panel, and that space is the real limit.
		final Widget holder = window.getParent();
		final Widget modal = holder == null ? null : holder.getParent();
		final Widget space = modal == null ? null : modal.getParent();
		if (space == null)
		{
			return;
		}

		final boolean touched = baseWindowWidth != -1;
		final int modalWidth = modalWidget == modal ? baseModalWidth : modal.getOriginalWidth();
		final int maxColumns = DEFAULT_COLUMNS + Math.max(0, space.getWidth() - modalWidth) / COLUMN_SPACING;
		final int columns = Math.min(Math.max(requestedColumns, DEFAULT_COLUMNS), maxColumns);
		if (columns == DEFAULT_COLUMNS && !touched)
		{
			layoutTabRow(true);
			return;
		}

		if (!touched)
		{
			baseWindowWidth = window.getOriginalWidth();
			baseItemsWidth = items.getOriginalWidth();
		}
		if (modalWidget != modal)
		{
			restoreModal();
			modalWidget = modal;
			baseModalWidth = modal.getOriginalWidth();
		}

		final int extra = (columns - DEFAULT_COLUMNS) * COLUMN_SPACING;
		final int wantedModalWidth = baseModalWidth + extra;
		final int wantedWindowWidth = baseWindowWidth + extra;
		final int wantedItemsWidth = baseItemsWidth + extra;

		// The game puts the item area back to its normal width whenever it rebuilds the
		// bank, which is also the sign that the slots are back in 8 columns
		final boolean rebuilt = items.getOriginalWidth() != wantedItemsWidth;
		if (rebuilt || modal.getOriginalWidth() != wantedModalWidth
			|| window.getOriginalWidth() != wantedWindowWidth || columns != appliedColumns)
		{
			items.setOriginalWidth(wantedItemsWidth);
			window.setOriginalWidth(wantedWindowWidth);
			modal.setOriginalWidth(wantedModalWidth);
			modal.revalidate();
			modal.revalidateScroll();
			// The bank's outermost layer fills the box. It must not be re-laid out on its
			// own: the game then sizes it to the whole game window, which pushes the bank
			// out of view. Size it to the box directly if the box did not already.
			if (holder.getWidth() != modal.getWidth() || holder.getHeight() != modal.getHeight())
			{
				holder.setWidth(modal.getWidth());
				holder.setHeight(modal.getHeight());
			}
			window.revalidate();
			window.revalidateScroll();
			arrange(items, columns);
			appliedColumns = columns;
		}

		// Checked every frame, because the game and the Bank Tags plugin put tabs back whenever they like
		layoutTabRow(true);

		if (columns == DEFAULT_COLUMNS)
		{
			restoreModal();
			baseWindowWidth = -1;
			baseItemsWidth = -1;
		}
	}

	/**
	 * Lines everything tab-like up along the top, from the left: the Bank Tags plugin's
	 * new-tab button, the bank's own tabs, then Bank Tags' tag tabs, which normally run
	 * down the left side. Tag tabs that do not fit stay in their column.
	 */
	private void layoutTabRow(boolean inRow)
	{
		if (!inRow && !tabRowApplied)
		{
			return;
		}

		final Widget tabs = client.getWidget(InterfaceID.Bankmain.TABS);
		final Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		if (tabs == null || container == null)
		{
			return;
		}

		final Widget[] extras = container.getDynamicChildren();
		final boolean hasTagTabs = extras != null && extras.length >= TAG_TAB_FIRST_CHILD
			&& extras[TAG_TAB_FIRST_CHILD - 1] != null
			&& extras[TAG_TAB_FIRST_CHILD - 1].getOriginalWidth() == TAG_TAB_WIDTH;

		if (!inRow)
		{
			setPosition(tabs, WidgetPositionMode.ABSOLUTE_CENTER, 0, tabs.getOriginalY());
			if (hasTagTabs)
			{
				placeTagTabs(extras, container, Integer.MAX_VALUE);
			}
			tabRowApplied = false;
			return;
		}

		// The bank centres its tabs inside their strip, so move the strip rather than the
		// tabs. With Bank Tags, leave room for its new-tab button in the corner.
		int firstTabX = Integer.MAX_VALUE;
		int lastTabRight = 0;
		final Widget[] tabChildren = tabs.getDynamicChildren();
		for (int i = 0; tabChildren != null && i < tabChildren.length; i++)
		{
			final Widget child = tabChildren[i];
			if (child != null && !child.isSelfHidden())
			{
				firstTabX = Math.min(firstTabX, child.getOriginalX());
				lastTabRight = Math.max(lastTabRight, child.getOriginalX() + child.getOriginalWidth());
			}
		}
		if (firstTabX == Integer.MAX_VALUE)
		{
			return;
		}

		final int rowStart = hasTagTabs ? TAG_TAB_MARGIN + TAG_TAB_WIDTH + TAG_TAB_MARGIN : 0;
		final int stripX = rowStart - firstTabX;
		setPosition(tabs, WidgetPositionMode.ABSOLUTE_LEFT, stripX, tabs.getOriginalY());

		if (hasTagTabs)
		{
			placeTagTabs(extras, container, stripX + lastTabRight + TAG_TAB_MARGIN);
		}
		tabRowApplied = true;
	}

	/**
	 * Bank Tags keeps, after four controls, a background and an icon for each tag tab.
	 * Puts the ones it is currently showing in a row starting at rowX, or back in their
	 * column when rowX is past the edge of the bank.
	 */
	private void placeTagTabs(Widget[] extras, Widget container, int rowX)
	{
		final Widget scrollArea = extras[0];
		final int columnTop = scrollArea == null ? 0 : scrollArea.getOriginalY() + TAG_TAB_MARGIN;
		final int width = container.getWidth();

		int x = rowX;
		int shown = 0;
		boolean allInRow = true;
		for (int i = TAG_TAB_FIRST_CHILD; i + 1 < extras.length; i += 2)
		{
			final Widget background = extras[i];
			final Widget icon = extras[i + 1];
			if (background == null || icon == null)
			{
				break;
			}
			if (background.isSelfHidden())
			{
				// Scrolled out of view in Bank Tags' own list
				allInRow = false;
				continue;
			}

			if (x <= width - TAG_TAB_WIDTH)
			{
				setPosition(background, WidgetPositionMode.ABSOLUTE_LEFT, x, 0);
				setPosition(icon, WidgetPositionMode.ABSOLUTE_LEFT, x + TAG_ICON_INSET_X, TAG_ICON_INSET_Y);
				x += TAG_TAB_WIDTH + TAG_TAB_MARGIN;
			}
			else
			{
				final int y = columnTop + shown * (TAG_TAB_HEIGHT + TAG_TAB_MARGIN);
				setPosition(background, WidgetPositionMode.ABSOLUTE_LEFT, TAG_TAB_MARGIN, y);
				setPosition(icon, WidgetPositionMode.ABSOLUTE_LEFT, TAG_TAB_MARGIN + TAG_ICON_INSET_X, y + TAG_ICON_INSET_Y);
				allInRow = false;
			}
			shown++;
		}

		// The scroll arrows are only needed while some tag tabs are still in the column
		final boolean hideArrows = allInRow && rowX != Integer.MAX_VALUE;
		if (hideArrows || hidTagArrows)
		{
			for (int i = 1; i <= 2; i++)
			{
				if (extras[i] != null && extras[i].isSelfHidden() != hideArrows)
				{
					extras[i].setHidden(hideArrows);
				}
			}
			hidTagArrows = hideArrows;
		}
	}

	private static void setPosition(Widget widget, int xMode, int x, int y)
	{
		if (widget.getXPositionMode() == xMode && widget.getOriginalX() == x && widget.getOriginalY() == y)
		{
			return;
		}
		widget.setXPositionMode(xMode);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.revalidate();
	}

	/**
	 * Puts the bank back to its normal width, if it is loaded.
	 */
	void restore()
	{
		update(DEFAULT_COLUMNS);
		layoutTabRow(false);
		restoreModal();
		baseWindowWidth = -1;
		baseItemsWidth = -1;
		appliedColumns = DEFAULT_COLUMNS;
	}

	private void restoreModal()
	{
		if (modalWidget == null)
		{
			return;
		}

		if (client.getWidget(modalWidget.getId()) == modalWidget && modalWidget.getOriginalWidth() != baseModalWidth)
		{
			modalWidget.setOriginalWidth(baseModalWidth);
			modalWidget.revalidate();
			modalWidget.revalidateScroll();
		}
		modalWidget = null;
		baseModalWidth = -1;
	}

	/**
	 * Moves the item slots into rows of the given length. In the view of all tabs, each
	 * tab's items start on a new row under that tab's divider, and the dividers move up
	 * to close the rows that were saved.
	 */
	private void arrange(Widget items, int columns)
	{
		final Widget[] children = items.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		final List<Widget> visible = new ArrayList<>();
		for (Widget child : children)
		{
			if (child != null && !child.isSelfHidden())
			{
				visible.add(child);
			}
		}
		visible.sort(TOP_TO_BOTTOM);

		int shift = 0;
		boolean inSection = false;
		int column = 0;
		int rowY = 0;
		int lastOldY = 0;
		int lastNewY = -1;

		for (Widget widget : visible)
		{
			final int oldY = widget.getOriginalY();

			if (isItemSlot(widget))
			{
				if (!inSection)
				{
					inSection = true;
					column = 0;
					rowY = oldY + shift;
				}
				else if (column == columns)
				{
					column = 0;
					rowY += ROW_HEIGHT;
				}

				widget.setOriginalX(FIRST_COLUMN_X + column * COLUMN_SPACING);
				widget.setOriginalY(rowY);
				widget.revalidate();

				column++;
				lastOldY = oldY;
				lastNewY = rowY;
			}
			else
			{
				// Part of a divider between two tabs
				if (inSection)
				{
					inSection = false;
					shift = lastNewY - lastOldY;
				}
				// The game sizes the divider's line and label once, to the normal bank width
				final boolean spansGrid = widget.getOriginalHeight() == DIVIDER_LINE_HEIGHT
					|| widget.getOriginalHeight() == DIVIDER_LABEL_HEIGHT;
				final int width = spansGrid ? items.getOriginalWidth() - DIVIDER_SIDE_SPACE : widget.getOriginalWidth();
				if (shift != 0 || width != widget.getOriginalWidth())
				{
					widget.setOriginalY(oldY + shift);
					widget.setOriginalWidth(width);
					widget.revalidate();
				}
			}
		}

		updateScrolling(items, lastNewY == -1 ? 0 : lastNewY + SLOT_HEIGHT + SCROLL_PADDING);
	}

	private void updateScrolling(Widget items, int contentHeight)
	{
		final int viewHeight = items.getHeight();
		final boolean scrolls = contentHeight > viewHeight;
		final int scrollY = scrolls ? Math.min(items.getScrollY(), contentHeight - viewHeight) : 0;

		items.setScrollHeight(scrolls ? contentHeight : 0);
		items.setScrollY(scrollY);
		items.revalidateScroll();
		client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scrollY);
	}

	private static boolean isItemSlot(Widget widget)
	{
		return widget.getOriginalWidth() == SLOT_WIDTH && widget.getOriginalHeight() == SLOT_HEIGHT;
	}
}
