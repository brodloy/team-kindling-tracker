package com.teamkindlingtracker;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(TeamKindlingTrackerConfig.GROUP)
public interface TeamKindlingTrackerConfig extends Config
{
	String GROUP = "teamkindlingtracker";

	@ConfigItem(
		keyName = "showBrazierCount",
		name = "Count above braziers",
		description = "Draw the total kindling added to each brazier above the brazier.",
		position = 1
	)
	default boolean showBrazierCount()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "brazierColor",
		name = "Brazier text colour",
		description = "Colour of the number drawn above each brazier.",
		position = 2
	)
	default Color brazierColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
		keyName = "showPartyOverlay",
		name = "Party inventory overlay",
		description = "Show an overlay box listing each party member's kindling in inventory.",
		position = 3
	)
	default boolean showPartyOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCountAboveHeads",
		name = "Inventory count above heads",
		description = "Draw each party member's kindling-in-inventory count above their head.",
		position = 4
	)
	default boolean showCountAboveHeads()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "headColor",
		name = "Head text colour",
		description = "Colour of the kindling count drawn above party members' heads.",
		position = 5
	)
	default Color headColor()
	{
		return Color.YELLOW;
	}

	@Range(min = -100, max = 200)
	@ConfigItem(
		keyName = "headHeightOffset",
		name = "Head height offset",
		description = "Raises or lowers the count above each player's head. Higher values move it up.",
		position = 6
	)
	default int headHeightOffset()
	{
		return 80;
	}

	@Range(min = 8, max = 60)
	@ConfigItem(
		keyName = "headFontSize",
		name = "Head font size",
		description = "Size of the count drawn above players' heads.",
		position = 7
	)
	default int headFontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "headBold",
		name = "Head bold text",
		description = "Render the above-head count in bold.",
		position = 8
	)
	default boolean headBold()
	{
		return true;
	}
}
