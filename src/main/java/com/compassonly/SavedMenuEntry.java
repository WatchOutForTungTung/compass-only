package com.compassonly;

import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

/**
 * A copy of one right-click menu option, kept after the game has moved on, so the same
 * option can be put back into a later menu and still do the same thing.
 */
final class SavedMenuEntry
{
	private final String option;
	private final String target;
	private final MenuAction type;
	private final int identifier;
	private final int param0;
	private final int param1;
	private final int itemId;
	private final int worldViewId;
	private final boolean deprioritized;
	private final boolean forceLeftClick;

	private SavedMenuEntry(MenuEntry entry)
	{
		option = entry.getOption();
		target = entry.getTarget();
		type = entry.getType();
		identifier = entry.getIdentifier();
		param0 = entry.getParam0();
		param1 = entry.getParam1();
		itemId = entry.getItemId();
		worldViewId = entry.getWorldViewId();
		deprioritized = entry.isDeprioritized();
		forceLeftClick = entry.isForceLeftClick();
	}

	static SavedMenuEntry[] copyOf(MenuEntry[] entries)
	{
		final SavedMenuEntry[] saved = new SavedMenuEntry[entries.length];
		for (int i = 0; i < entries.length; i++)
		{
			saved[i] = new SavedMenuEntry(entries[i]);
		}
		return saved;
	}

	/**
	 * Replaces everything in the menu with the saved options, in the same order.
	 */
	static void restoreInto(Menu menu, SavedMenuEntry[] saved)
	{
		menu.setMenuEntries(new MenuEntry[0]);
		for (SavedMenuEntry entry : saved)
		{
			menu.createMenuEntry(-1)
				.setOption(entry.option)
				.setTarget(entry.target)
				.setType(entry.type)
				.setIdentifier(entry.identifier)
				.setParam0(entry.param0)
				.setParam1(entry.param1)
				.setItemId(entry.itemId)
				.setWorldViewId(entry.worldViewId)
				.setDeprioritized(entry.deprioritized)
				.setForceLeftClick(entry.forceLeftClick);
		}
	}
}
