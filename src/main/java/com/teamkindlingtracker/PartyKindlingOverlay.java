package com.teamkindlingtracker;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.api.MenuAction;

/**
 * Overlay box listing each party member and the amount of kindling they currently
 * hold in their inventory.
 */
public class PartyKindlingOverlay extends OverlayPanel
{
	private final TeamKindlingTrackerPlugin plugin;
	private final TeamKindlingTrackerConfig config;

	@Inject
	private PartyKindlingOverlay(TeamKindlingTrackerPlugin plugin, TeamKindlingTrackerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Ice Demon kindling", "overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Rebuild from scratch each frame, otherwise rows accumulate without bound.
		panelComponent.getChildren().clear();

		if (!config.showPartyOverlay() || !plugin.isAboveHeadsActive())
		{
			return null;
		}

		final Map<String, Integer> byName = plugin.getKindlingByName();
		if (byName.isEmpty())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Kindling")
			.build());

		byName.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(e -> panelComponent.getChildren().add(LineComponent.builder()
				.left(e.getKey())
				.right(Integer.toString(e.getValue()))
				.build()));

		return super.render(graphics);
	}
}
