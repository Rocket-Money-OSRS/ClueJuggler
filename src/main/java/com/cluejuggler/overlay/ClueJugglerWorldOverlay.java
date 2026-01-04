package com.cluejuggler.overlay;

import com.cluejuggler.ClueJugglerConfig;
import com.cluejuggler.ClueJugglerPlugin;
import com.cluejuggler.model.ClueList;
import com.cluejuggler.service.ClueListService;
import com.cluejuggler.service.ClueTrackingService;
import com.cluejuggler.service.ClueTrackingService.DroppedClueInfo;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
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

		ClueTrackingService trackingService = plugin.getTrackingService();
		ClueListService listService = plugin.getListService();
		ClueJugglerConfig config = plugin.getConfig();
		
		if (trackingService.getDroppedClues().isEmpty())
		{
			return null;
		}

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

				boolean hasClueOnTile = false;
				for (TileItem item : groundItems)
				{
					if (item == null)
					{
						continue;
					}

					int itemId = item.getId();
					if (itemId == ItemID.TRAIL_CLUE_BEGINNER || 
						itemId == ItemID.TRAIL_CLUE_MASTER ||
						itemId == 23182)
					{
						hasClueOnTile = true;
						break;
					}
					
					ItemComposition itemComp = client.getItemDefinition(itemId);
					if (itemComp != null)
					{
						String itemName = itemComp.getName();
						if (itemName != null && (itemName.startsWith("Clue scroll")
							|| itemName.startsWith("Challenge scroll")
							|| itemName.startsWith("Treasure scroll")))
						{
							hasClueOnTile = true;
							break;
						}
					}
				}
				
				if (!hasClueOnTile)
				{
					continue;
				}
				
				WorldPoint tileWorldPoint = tile.getWorldLocation();
				DroppedClueInfo info = trackingService.getDroppedClueInfo(tileWorldPoint);
				
				if (info == null)
				{
					continue;
				}
				
				Color tileColor;
				Color textColor;
				String text;
				
				if (info.customList != null)
				{
					tileColor = info.customList.getTileColor();
					textColor = info.customList.getOverlayTextColor();
					text = info.customList.getName();
				}
				else if (info.isGood != null)
				{
					if (info.isGood)
					{
						tileColor = config.goodClueTileColor();
						textColor = config.goodClueTextColor();
						text = "Good";
					}
					else
					{
						tileColor = config.badClueTileColor();
						textColor = config.badClueTextColor();
						text = "Bad";
					}
				}
				else
				{
					tileColor = config.unsureClueTileColor();
					textColor = config.unsureClueTextColor();
					text = "Unsure";
				}
				
				if (!tileWorldPoint.isInScene(client))
				{
					continue;
				}
				
				LocalPoint localPoint = LocalPoint.fromWorld(client, tileWorldPoint);
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

		return null;
	}
}

