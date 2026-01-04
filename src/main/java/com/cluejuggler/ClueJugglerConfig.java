package com.cluejuggler;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("cluejuggler")
public interface ClueJugglerConfig extends Config
{
	@ConfigItem(
		keyName = "showIndicator",
		name = "Show Clue Indicator",
		description = "Display whether clues are good, bad, or unsure",
		position = 0
	)
	default boolean showIndicator()
	{
		return true;
	}

	@ConfigSection(
		name = "Clue Highlighter",
		description = "Settings for highlighting clue tiles",
		position = 10
	)
	String clueHighlighterSection = "clueHighlighterSection";

	@ConfigItem(
		keyName = "clueHighlights",
		name = "Enable Clue Highlights",
		description = "Highlight the clue tile and show good/bad/unsure text on top of it",
		section = clueHighlighterSection,
		position = 1
	)
	default boolean clueHighlights()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "goodClueTileColor",
		name = "Good Clue Tile Color",
		description = "Color for highlighting good clue tiles",
		section = clueHighlighterSection,
		position = 2
	)
	default Color goodClueTileColor()
	{
		return new Color(0, 255, 0, 100);
	}

	@Alpha
	@ConfigItem(
		keyName = "badClueTileColor",
		name = "Bad Clue Tile Color",
		description = "Color for highlighting bad clue tiles",
		section = clueHighlighterSection,
		position = 3
	)
	default Color badClueTileColor()
	{
		return new Color(255, 0, 0, 100);
	}

	@Alpha
	@ConfigItem(
		keyName = "unsureClueTileColor",
		name = "Unsure Clue Tile Color",
		description = "Color for highlighting unsure clue tiles",
		section = clueHighlighterSection,
		position = 4
	)
	default Color unsureClueTileColor()
	{
		return new Color(255, 255, 0, 100);
	}

	@Alpha
	@ConfigItem(
		keyName = "goodClueTextColor",
		name = "Good Clue Text Color",
		description = "Color for good clue text",
		section = clueHighlighterSection,
		position = 5
	)
	default Color goodClueTextColor()
	{
		return Color.GREEN;
	}

	@Alpha
	@ConfigItem(
		keyName = "badClueTextColor",
		name = "Bad Clue Text Color",
		description = "Color for bad clue text",
		section = clueHighlighterSection,
		position = 6
	)
	default Color badClueTextColor()
	{
		return Color.RED;
	}

	@Alpha
	@ConfigItem(
		keyName = "unsureClueTextColor",
		name = "Unsure Clue Text Color",
		description = "Color for unsure clue text",
		section = clueHighlighterSection,
		position = 7
	)
	default Color unsureClueTextColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "deprioritizeBadClues",
		name = "Deprioritize Bad Clues",
		description = "Makes 'Walk here' the default action for bad clues to prevent accidental pickup",
		position = 20
	)
	default boolean deprioritizeBadClues()
	{
		return true;
	}

	@ConfigItem(
		keyName = "customLists",
		name = "Custom Lists",
		description = "JSON string of custom clue lists",
		hidden = true
	)
	default String customLists()
	{
		return "[]";
	}
}

