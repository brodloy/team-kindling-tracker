package com.teamkindlingtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TeamKindlingTrackerTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TeamKindlingTrackerPlugin.class);
		RuneLite.main(args);
	}
}
