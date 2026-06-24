package com.teamkindlingtracker;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws the party-wide total of kindling added to each brazier, above the brazier.
 */
public class BrazierOverlay extends Overlay
{
	private final TeamKindlingTrackerPlugin plugin;
	private final TeamKindlingTrackerConfig config;

	@Inject
	private BrazierOverlay(TeamKindlingTrackerPlugin plugin, TeamKindlingTrackerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showBrazierCount() || !plugin.isInRoom())
		{
			return null;
		}

		for (int i = 0; i < plugin.getBrazierCount(); i++)
		{
			final GameObject obj = plugin.getBrazierObject(i);
			if (obj == null)
			{
				continue;
			}

			final String text = Integer.toString(plugin.getBrazierTotal(i));
			// GameObject has no logical height; use a fixed world-height offset above the tile.
			final Point point = obj.getCanvasTextLocation(graphics, text, 200);
			if (point != null)
			{
				OverlayUtil.renderTextLocation(graphics, point, text, config.brazierColor());
			}
		}

		return null;
	}
}
