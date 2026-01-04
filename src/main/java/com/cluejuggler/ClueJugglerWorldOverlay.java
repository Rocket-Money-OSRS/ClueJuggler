package com.cluejuggler;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class ClueJugglerWorldOverlay extends Overlay
{
	private final ClueJugglerPlugin plugin;
	private final Client client;

	@Inject
	private ClueJugglerWorldOverlay(ClueJugglerPlugin plugin, Client client)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.plugin = plugin;
		this.client = client;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.getConfig().clueHighlights())
		{
			return null;
		}

		Map<Integer, ClueScroll> trackedClues = plugin.getTrackedClues();
		Map<String, ClueScroll> trackedCluesByText = plugin.getTrackedCluesByText();
		
		// Early exit if no tracked clues AND no beginner/master positions
		// Must check beginner positions here too, not just tracked clues
		if (trackedClues.isEmpty() && trackedCluesByText.isEmpty() && plugin.getBeginnerMasterCluePositions().isEmpty())
		{
			return null;
		}

		ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
		if (clueScrollPlugin == null)
		{
			return null;
		}

		ClueJugglerConfig config = plugin.getConfig();
		ClueJugglerPanel panel = plugin.getPanel();

		if (panel == null)
		{
			return null;
		}
		
		// Ensure panel has loaded all lists (good, bad, and custom) before we try to access them
		// This is safe to call multiple times
		panel.ensureListsLoaded();
		panel.ensureCustomListsLoaded();

		Scene scene = client.getScene();
		if (scene == null)
		{
			return null;
		}

		Tile[][][] tiles = scene.getTiles();
		if (tiles == null)
		{
			return null;
		}

		int plane = client.getPlane();

		for (int x = 0; x < 104; x++)
		{
			for (int y = 0; y < 104; y++)
			{
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}
				
				java.util.List<TileItem> groundItems = tile.getGroundItems();
				if (groundItems == null || groundItems.isEmpty())
				{
					continue;
				}

				for (TileItem item : groundItems)
				{
					if (item == null)
					{
						continue;
					}

					int itemId = item.getId();
					
					// First, check if this is a clue scroll item
					boolean isClueScroll = false;
					boolean isBeginnerOrMaster = false;
					
					// Check for beginner/master clues by itemId (fast check)
					// ItemID 23182 is also a beginner clue item
					if (itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_BEGINNER || 
						itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_MASTER ||
						itemId == 23182)
					{
						isClueScroll = true;
						isBeginnerOrMaster = true;
					}
					else
					{
						// Check if it's a regular clue scroll by name (same method as plugin)
						ItemComposition itemComp = client.getItemDefinition(itemId);
						if (itemComp != null)
						{
							String itemName = itemComp.getName();
							if (itemName != null && (itemName.startsWith("Clue scroll")
								|| itemName.startsWith("Challenge scroll")
								|| itemName.startsWith("Treasure scroll")))
							{
								isClueScroll = true;
							}
						}
					}
					
					// Only process clue scrolls
					if (!isClueScroll)
					{
						continue;
					}
					
					// Get the tracked clue - try by itemId first, then by text
					ClueScroll trackedClue = trackedClues.get(itemId);
					if (trackedClue == null && !trackedCluesByText.isEmpty())
					{
						trackedClue = trackedCluesByText.values().iterator().next();
					}
					
					// Try to get identifier from tracked clue for cache lookup
					String clueIdentifier = null;
					if (trackedClue != null)
					{
						clueIdentifier = plugin.getClueIdentifier(trackedClue);
					}
					// If no tracked clue (dropped item), try to get identifier from itemId mapping
					if (clueIdentifier == null || clueIdentifier.isEmpty())
					{
						clueIdentifier = plugin.getIdentifierForItemId(itemId);
					}
					
					// Now determine the status and render
					Color tileColor = null;
					Color textColor = null;
					String text = null;

					// For beginner/master clues, use the stored position status
					// Since we can't read clue text from dropped items, we assume the next dropped clue
					// at the tagged position is the one we just tagged
					if (isBeginnerOrMaster)
					{
						// Only show status if there are actually clues in lists
						// This prevents showing stale positions when lists are empty
						boolean hasCluesInLists = panel.hasAnyCluesInLists();
						
						if (!hasCluesInLists)
						{
							// No clues in any lists, show unsure
							tileColor = config.unsureClueTileColor();
							textColor = config.unsureClueTextColor();
							text = "Unsure";
						}
						else
						{
							WorldPoint tileWorldPoint = tile.getWorldLocation();
							
							// Also check player's current position as fallback (clue might be at player's feet)
							WorldPoint playerPosition = null;
							if (client.getLocalPlayer() != null)
							{
								playerPosition = client.getLocalPlayer().getWorldLocation();
							}
							
							// Check for custom list first - try tile position, then player position
							com.cluejuggler.ClueList customList = plugin.getBeginnerMasterClueCustomList(tileWorldPoint);
							if (customList == null && playerPosition != null)
							{
								customList = plugin.getBeginnerMasterClueCustomList(playerPosition);
							}
							
							if (customList != null)
							{
								tileColor = customList.getTileColor();
								textColor = customList.getOverlayTextColor();
								text = customList.getName();
							}
							else
							{
								// Get the stored status for this position (good/bad) - try tile position, then player position
								Boolean isGoodStatus = plugin.getBeginnerMasterClueStatus(tileWorldPoint);
								if (isGoodStatus == null && playerPosition != null)
								{
									isGoodStatus = plugin.getBeginnerMasterClueStatus(playerPosition);
								}
								
								if (isGoodStatus != null)
								{
									if (isGoodStatus)
									{
										tileColor = config.goodClueTileColor();
										textColor = config.goodClueTextColor();
										text = "Good Clue";
									}
									else
									{
										tileColor = config.badClueTileColor();
										textColor = config.badClueTextColor();
										text = "Bad Clue";
									}
								}
								else
								{
									tileColor = config.unsureClueTileColor();
									textColor = config.unsureClueTextColor();
									text = "Unsure";
								}
							}
						}
					}
					else
					{
						// For regular clues, ALWAYS check lists FIRST (source of truth)
						// This ensures we always show current status even if cache is stale
						boolean isGood = false;
						boolean isBad = false;
						com.cluejuggler.ClueList customList = null;
						
						// Check lists directly using identifier (most reliable and always up-to-date)
						if (clueIdentifier != null && !clueIdentifier.isEmpty())
						{
							isGood = panel.isGoodClueByIdentifier(clueIdentifier);
							isBad = panel.isBadClueByIdentifier(clueIdentifier);
							if (!isGood && !isBad)
							{
								customList = panel.getCustomListForClue(clueIdentifier);
							}
						}
						
						// If not found by identifier and we have trackedClue, try text-based matching
						if (!isGood && !isBad && customList == null && trackedClue != null)
						{
							String clueText = plugin.generateClueTextFromClue(trackedClue);
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
						
						// Determine colors and text based on what we found
						if (customList != null)
						{
							tileColor = customList.getTileColor();
							textColor = customList.getOverlayTextColor();
							text = customList.getName();
							// Update cache for next time
							plugin.cacheClueStatus("customList:" + itemId, customList);
							if (clueIdentifier != null && !clueIdentifier.isEmpty())
							{
								plugin.cacheClueStatus("customList:" + clueIdentifier, customList);
							}
							plugin.cacheClueStatus(itemId, 0);
							if (clueIdentifier != null && !clueIdentifier.isEmpty())
							{
								plugin.cacheClueStatus(clueIdentifier, 0);
							}
						}
						else if (isGood)
						{
							tileColor = config.goodClueTileColor();
							textColor = config.goodClueTextColor();
							text = "Good Clue";
							// Update cache for next time
							plugin.cacheClueStatus(itemId, 1);
							if (clueIdentifier != null && !clueIdentifier.isEmpty())
							{
								plugin.cacheClueStatus(clueIdentifier, 1);
							}
						}
						else if (isBad)
						{
							tileColor = config.badClueTileColor();
							textColor = config.badClueTextColor();
							text = "Bad Clue";
							// Update cache for next time
							plugin.cacheClueStatus(itemId, -1);
							if (clueIdentifier != null && !clueIdentifier.isEmpty())
							{
								plugin.cacheClueStatus(clueIdentifier, -1);
							}
						}
						else
						{
							// Not in any list - show unsure
							tileColor = config.unsureClueTileColor();
							textColor = config.unsureClueTextColor();
							text = "Unsure";
							plugin.cacheClueStatus(itemId, 0);
							if (clueIdentifier != null && !clueIdentifier.isEmpty())
							{
								plugin.cacheClueStatus(clueIdentifier, 0);
							}
						}
					}
					
					// Only render if we have valid colors and text
					if (tileColor != null && textColor != null && text != null)
					{
						WorldPoint worldPoint = tile.getWorldLocation();
						
						// Early visibility check before expensive operations
						if (worldPoint == null || !worldPoint.isInScene(client))
						{
							continue;
						}
						
						LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
						if (localPoint == null)
						{
							continue;
						}
						
						Polygon tilePoly = Perspective.getCanvasTilePoly(client, localPoint);
						if (tilePoly != null)
						{
							OverlayUtil.renderPolygon(graphics, tilePoly, tileColor);
						}

						net.runelite.api.Point textPoint = Perspective.localToCanvas(client, localPoint, client.getPlane(), 50);
						if (textPoint != null)
						{
							FontMetrics fontMetrics = graphics.getFontMetrics();
							int textWidth = fontMetrics.stringWidth(text);
							net.runelite.api.Point centeredTextPoint = new net.runelite.api.Point(
								textPoint.getX() - textWidth / 2,
								textPoint.getY()
							);
							OverlayUtil.renderTextLocation(graphics, centeredTextPoint, text, textColor);
						}
					}
				}
			}
		}

		return null;
	}

	private void logToFile(String action, String message)
	{
		// Remove file logging to prevent client thread blocking
		// Use slf4j logger instead if needed
	}
}
