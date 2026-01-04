package com.cluejuggler;

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

		// Get clue text using the same method as the panel
		String clueText = plugin.generateClueTextFromClue(clue);
		
		// Get clue identifier for matching (more reliable than text)
		String clueIdentifier = plugin.getClueIdentifier(clue);
		
		ClueJugglerPanel panel = plugin.getPanel();
		boolean isGood = false;
		boolean isBad = false;
		com.cluejuggler.ClueList customList = null;
		
		// Try identifier-based matching first (if identifier is available)
		if (clueIdentifier != null && !clueIdentifier.isEmpty())
		{
			isGood = panel.isGoodClueByIdentifier(clueIdentifier);
			isBad = panel.isBadClueByIdentifier(clueIdentifier);
			if (!isGood && !isBad)
			{
				customList = panel.getCustomListForClue(clueIdentifier);
			}
		}
		
		// Fallback to text-based matching if identifier didn't match or wasn't available
		if (!isGood && !isBad && customList == null)
		{
			if (clueText != null && !clueText.isEmpty())
			{
				isGood = panel.isGoodClue(clueText);
				isBad = panel.isBadClue(clueText);
				if (!isGood && !isBad)
				{
					customList = panel.getCustomListForClueByText(clueText);
				}
			}
		}
		
		String status;
		Color color;
		
		if (isGood)
		{
			status = "Good Clue";
			color = Color.GREEN;
		}
		else if (isBad)
		{
			status = "Bad Clue";
			color = Color.RED;
		}
		else if (customList != null)
		{
			status = customList.getName();
			color = customList.getTextColor();
		}
		else
		{
			status = "Unsure";
			color = Color.YELLOW;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Clue Juggler: " + status)
			.leftColor(color)
			.build());

		return super.render(graphics);
	}
}

