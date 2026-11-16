package com.compassonly;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CompassOnlyConfig.GROUP)
public interface CompassOnlyConfig extends Config
{
	String GROUP = "compassonly";
	String HIDE_FRAME = "hideFrame";
	String MINIMAP_IN_COMPASS = "minimapInCompass";
	String BANK_COLUMNS = "bankColumns";
	int MIN_SIZE = 10;
	int MAX_SIZE = 1000;
	int MIN_INVENTORY_SIZE = 100;
	int MAX_INVENTORY_SIZE = 300;
	int MIN_BANK_SIZE = 100;
	int MAX_BANK_SIZE = 300;
	int MAX_BANK_COLUMNS = 24;

	@ConfigSection(
		name = "Compass",
		description = "The big compass and the small minimap",
		position = 0
	)
	String compassSection = "compass";

	@ConfigSection(
		name = "Side panel",
		description = "Enlarging the side panel, per tab",
		position = 1
	)
	String sidePanelSection = "sidePanel";

	@ConfigSection(
		name = "Bank",
		description = "Enlarging and widening the bank window",
		position = 2
	)
	String bankSection = "bank";

	// ---------------------------------------------------------------- Compass

	@ConfigItem(
		keyName = "showCompass",
		name = "Big compass",
		description = "Replace the minimap with an enlarged compass. Turn off to leave the minimap and compass as the game draws them.",
		position = 0,
		section = compassSection
	)
	default boolean showCompass()
	{
		return true;
	}

	@Range(min = MIN_SIZE, max = MAX_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "compassSize",
		name = "Compass size",
		description = "Size of the compass. 100% fills the minimap's space. Above 100% it grows past the minimap and is drawn on top of the interface.",
		position = 1,
		section = compassSection
	)
	default int compassSize()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "lookOptions",
		name = "Click to look north",
		description = "Left-click the compass to face north, right-click it for south, east and west",
		position = 2,
		section = compassSection
	)
	default boolean lookOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = MINIMAP_IN_COMPASS,
		name = "Small minimap",
		description = "Show the minimap, shrunk down, in the small circle where the game's compass used to be. It still works as normal: click to walk, scroll to zoom.",
		position = 3,
		section = compassSection
	)
	default boolean minimapInCompass()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDE_FRAME,
		name = "Hide minimap frame",
		description = "Also hide the stone frame around the minimap, leaving the orbs. Resizable layouts only.",
		position = 4,
		section = compassSection
	)
	default boolean hideFrame()
	{
		return false;
	}

	// ---------------------------------------------------------------- Side panel

	@Range(min = MIN_INVENTORY_SIZE, max = MAX_INVENTORY_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "inventorySize",
		name = "Inventory size",
		description = "Size of the side panel while the inventory is open. 100% is normal. Larger sizes grow up and to the left from its bottom-right corner, and everything in it can still be clicked, right-clicked and dragged.",
		position = 0,
		section = sidePanelSection
	)
	default int inventorySize()
	{
		return 100;
	}

	@Range(min = MIN_INVENTORY_SIZE, max = MAX_INVENTORY_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "prayerSize",
		name = "Prayer size",
		description = "Size of the side panel while the prayer tab is open. 100% is normal. Works like Inventory size.",
		position = 1,
		section = sidePanelSection
	)
	default int prayerSize()
	{
		return 100;
	}

	@Range(min = MIN_INVENTORY_SIZE, max = MAX_INVENTORY_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "combatSize",
		name = "Combat options size",
		description = "Size of the side panel while the combat options tab is open. 100% is normal. Works like Inventory size.",
		position = 2,
		section = sidePanelSection
	)
	default int combatSize()
	{
		return 100;
	}

	@Range(min = MIN_INVENTORY_SIZE, max = MAX_INVENTORY_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "equipmentSize",
		name = "Equipment size",
		description = "Size of the side panel while the equipment tab is open. 100% is normal. Works like Inventory size.",
		position = 3,
		section = sidePanelSection
	)
	default int equipmentSize()
	{
		return 100;
	}

	@Range(min = MIN_INVENTORY_SIZE, max = MAX_INVENTORY_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "spellbookSize",
		name = "Spellbook size",
		description = "Size of the side panel while the spellbook is open. 100% is normal. Works like Inventory size.",
		position = 4,
		section = sidePanelSection
	)
	default int spellbookSize()
	{
		return 100;
	}

	// ---------------------------------------------------------------- Bank

	@Range(min = MIN_BANK_SIZE, max = MAX_BANK_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "bankSize",
		name = "Bank size",
		description = "Size of the bank window. 100% is normal. Larger sizes grow outwards from its centre, as far as fits in the game window, and everything in it can still be clicked, dragged and scrolled.",
		position = 0,
		section = bankSection
	)
	default int bankSize()
	{
		return 100;
	}

	@Range(min = BankWidener.DEFAULT_COLUMNS, max = MAX_BANK_COLUMNS)
	@ConfigItem(
		keyName = BANK_COLUMNS,
		name = "Bank columns",
		description = "How many items the bank shows per row. 8 is normal. More columns make the bank window wider, up to what fits beside the side panel.",
		position = 1,
		section = bankSection
	)
	default int bankColumns()
	{
		return BankWidener.DEFAULT_COLUMNS;
	}
}
