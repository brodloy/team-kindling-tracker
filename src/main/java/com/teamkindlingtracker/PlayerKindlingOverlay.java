package com.teamkindlingtracker;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/**
 * Draws each party member's kindling-in-inventory count above their head in the world,
 * with a configurable height offset and font.
 */
public class PlayerKindlingOverlay extends Overlay
{
	private final Client client;
	private final TeamKindlingTrackerPlugin plugin;
	private final TeamKindlingTrackerConfig config;

	@Inject
	private PlayerKindlingOverlay(Client client, TeamKindlingTrackerPlugin plugin, TeamKindlingTrackerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showCountAboveHeads() || !plugin.isAboveHeadsActive())
		{
			return null;
		}

		final Map<String, Integer> byName = plugin.getKindlingByName();
		if (byName.isEmpty())
		{
			return null;
		}

		final int style = config.headBold() ? Font.BOLD : Font.PLAIN;
		graphics.setFont(new Font(FontManager.getRunescapeFont().getName(), style, config.headFontSize()));
		final FontMetrics metrics = graphics.getFontMetrics();

		for (Player player : client.getPlayers())
		{
			if (player == null || player.getName() == null)
			{
				continue;
			}

			final Integer count = byName.get(Text.sanitize(player.getName()));
			if (count == null)
			{
				continue;
			}

			final LocalPoint lp = player.getLocalLocation();
			if (lp == null)
			{
				continue;
			}

			final String text = Integer.toString(count);
			final int height = player.getLogicalHeight() + config.headHeightOffset();
			final Point anchor = Perspective.localToCanvas(client, lp, client.getPlane(), height);
			if (anchor == null)
			{
				continue;
			}

			// Centre the text horizontally over the player.
			final Point textLocation = new Point(anchor.getX() - metrics.stringWidth(text) / 2, anchor.getY());
			OverlayUtil.renderTextLocation(graphics, textLocation, text, config.headColor());
		}

		return null;
	}
}
