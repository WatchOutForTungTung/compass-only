package com.compassonly;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CompassOnlyConfig.GROUP)
public interface CompassOnlyConfig extends Config
{
	String GROUP = "compassonly";
	String HIDE_FRAME = "hideFrame";
	int MIN_SIZE = 10;
	int MAX_SIZE = 1000;

	@Range(min = MIN_SIZE, max = MAX_SIZE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "compassSize",
		name = "Compass size",
		description = "Size of the compass. 100% fills the minimap's space. Above 100% it grows past the minimap and is drawn on top of the interface.",
		position = 0
	)
	default int compassSize()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "smoothScaling",
		name = "Smooth scaling",
		description = "Smooth the enlarged compass instead of keeping hard pixel edges",
		position = 1
	)
	default boolean smoothScaling()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lookOptions",
		name = "Click to look north",
		description = "Left-click the compass to face north, right-click it for south, east and west",
		position = 2
	)
	default boolean lookOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDE_FRAME,
		name = "Hide minimap frame",
		description = "Also hide the stone frame around the minimap, leaving the orbs. Resizable layouts only.",
		position = 3
	)
	default boolean hideFrame()
	{
		return false;
	}
}
