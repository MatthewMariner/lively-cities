package com.matthewmariner.livelycities;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LivelyCitiesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LivelyCitiesPlugin.class);
		RuneLite.main(args);
	}
}