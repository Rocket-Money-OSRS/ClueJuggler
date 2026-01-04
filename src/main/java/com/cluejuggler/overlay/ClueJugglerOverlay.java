package com.cluejuggler.overlay;

import com.cluejuggler.ClueJugglerPlugin;
import com.cluejuggler.model.ClueList;
import com.cluejuggler.service.ClueListService;
import com.cluejuggler.service.ClueLookupService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class ClueJugglerOverlay extends OverlayPanel
{
	private final ClueJugglerPlugin plugin;

	@Inject
	private ClueJugglerOverlay(ClueJugglerPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.getConfig().showIndicator())
		{
			return null;
		}

		ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
		if (clueScrollPlugin == null)
		{
			return null;
		}

		ClueScroll clue = clueScrollPlugin.getClue();
		if (clue == null)
		{
			return null;
		}

		ClueListService listService = plugin.getListService();
		ClueLookupService lookupService = plugin.getLookupService();
		
		String clueIdentifier = lookupService.getClueIdentifier(clue, plugin.getTrackingService());
		
		boolean isGood = false;
		boolean isBad = false;
		ClueList customList = null;
		
		if (clueIdentifier != null && !clueIdentifier.isEmpty())
		{
			isGood = listService.isGoodClueByIdentifier(clueIdentifier);
			isBad = listService.isBadClueByIdentifier(clueIdentifier);
			if (!isGood && !isBad)
			{
				customList = listService.getCustomListForClue(clueIdentifier);
			}
		}

		String text;
		Color color;
		
		if (customList != null)
		{
			text = customList.getName();
			color = customList.getTextColor();
		}
		else if (isGood)
		{
			text = "Good Clue";
			color = Color.GREEN;
		}
		else if (isBad)
		{
			text = "Bad Clue";
			color = Color.RED;
		}
		else
		{
			text = "Unsure";
			color = Color.YELLOW;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Clue Status:")
			.right(text)
			.rightColor(color)
			.build());

		return super.render(graphics);
	}
}

