package com.icedemontracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class IceDemonTrackerTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IceDemonTrackerPlugin.class);
		RuneLite.main(args);
	}
}
