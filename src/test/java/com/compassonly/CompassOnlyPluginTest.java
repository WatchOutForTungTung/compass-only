package com.compassonly;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CompassOnlyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CompassOnlyPlugin.class);
		RuneLite.main(args);
	}
}
