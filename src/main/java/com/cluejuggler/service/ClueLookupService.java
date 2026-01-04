package com.cluejuggler.service;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FairyRingClue;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;

@Singleton
public class ClueLookupService
{
	private final Client client;
	private final ItemManager itemManager;
	private final ClueScrollPlugin clueScrollPlugin;
	private final ClueTextService textService;
	
	@Inject
	public ClueLookupService(Client client, ItemManager itemManager, ClueScrollPlugin clueScrollPlugin, ClueTextService textService)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.clueScrollPlugin = clueScrollPlugin;
		this.textService = textService;
	}
	
	public ClueScroll findClueScroll(int itemId)
	{
		if (itemId == ItemID.TRAIL_CLUE_BEGINNER || itemId == ItemID.TRAIL_CLUE_MASTER)
		{
			return null;
		}
		
		ClueScroll clue = MapClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = MusicClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = CoordinateClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = AnagramClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = CipherClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = CrypticClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = EmoteClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		clue = FairyRingClue.forItemId(itemId);
		if (clue != null)
		{
			return clue;
		}
		
		return null;
	}
	
	public String getClueIdentifier(ClueScroll clue, ClueTrackingService trackingService)
	{
		if (clue == null)
		{
			return null;
		}
		
		int itemId = getCurrentClueItemId(trackingService);
		if (itemId > 0 && itemId != ItemID.TRAIL_CLUE_BEGINNER && itemId != ItemID.TRAIL_CLUE_MASTER)
		{
			return "item:" + itemId;
		}
		
		if (clue instanceof BeginnerMapClue)
		{
			String clueText = textService.generateClueTextFromClue(clue);
			if (clueText != null && !clueText.isEmpty())
			{
				return "beginner:" + clueText.hashCode();
			}
			return "beginner:" + Integer.toHexString(clue.hashCode());
		}
		
		String clueText = textService.generateClueTextFromClue(clue);
		if (clueText != null && !clueText.isEmpty())
		{
			return "master:" + clueText.hashCode();
		}
		
		return "clue:" + Integer.toHexString(clue.hashCode());
	}
	
	public int getCurrentClueItemId(ClueTrackingService trackingService)
	{
		if (!client.isClientThread())
		{
			return -1;
		}
		
		if (clueScrollPlugin == null)
		{
			return -1;
		}
		
		ClueScroll currentClue = clueScrollPlugin.getClue();
		if (currentClue == null)
		{
			return -1;
		}
		
		if (currentClue instanceof BeginnerMapClue)
		{
			net.runelite.api.ItemContainer inventory = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
			if (inventory != null)
			{
				for (net.runelite.api.Item item : inventory.getItems())
				{
					if (item == null || item.getId() <= 0)
					{
						continue;
					}
					
					if (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == 23182)
					{
						ItemComposition itemComp = itemManager.getItemComposition(item.getId());
						if (itemComp != null && (itemComp.getName().startsWith("Clue scroll")
							|| itemComp.getName().startsWith("Challenge scroll")
							|| itemComp.getName().startsWith("Treasure scroll")))
						{
							return item.getId();
						}
					}
				}
			}
			return ItemID.TRAIL_CLUE_BEGINNER;
		}
		
		String currentClueText = textService.generateClueTextFromClue(currentClue);
		if (currentClueText != null && !currentClueText.isEmpty() && trackingService.isTrackedByText(currentClueText))
		{
			for (Map.Entry<Integer, ClueScroll> entry : trackingService.getTrackedClues().entrySet())
			{
				ClueScroll trackedClue = entry.getValue();
				if (trackedClue == null)
				{
					continue;
				}
				String trackedClueText = textService.generateClueTextFromClue(trackedClue);
				if (trackedClueText != null && trackedClueText.equals(currentClueText))
				{
					return entry.getKey();
				}
			}
		}
		
		if (currentClueText == null || currentClueText.isEmpty())
		{
			return -1;
		}
		
		for (Map.Entry<Integer, ClueScroll> entry : trackingService.getTrackedClues().entrySet())
		{
			ClueScroll trackedClue = entry.getValue();
			if (trackedClue == null)
			{
				continue;
			}
			
			String trackedClueText = textService.generateClueTextFromClue(trackedClue);
			if (trackedClueText != null && trackedClueText.equals(currentClueText))
			{
				return entry.getKey();
			}
		}
		
		return -1;
	}
	
	public boolean isClueScrollItem(int itemId)
	{
		if (itemId == ItemID.TRAIL_CLUE_BEGINNER || 
			itemId == ItemID.TRAIL_CLUE_MASTER || 
			itemId == 23182)
		{
			return true;
		}
		
		ItemComposition itemComp = itemManager.getItemComposition(itemId);
		if (itemComp != null)
		{
			String itemName = itemComp.getName();
			if (itemName != null && (itemName.startsWith("Clue scroll")
				|| itemName.startsWith("Challenge scroll")
				|| itemName.startsWith("Treasure scroll")))
			{
				return true;
			}
		}
		
		return false;
	}
	
	public boolean isBeginnerOrMasterClue(int itemId)
	{
		return itemId == ItemID.TRAIL_CLUE_BEGINNER || 
			itemId == ItemID.TRAIL_CLUE_MASTER || 
			itemId == 23182;
	}
	
	public ClueScroll getCurrentClue()
	{
		return clueScrollPlugin != null ? clueScrollPlugin.getClue() : null;
	}
}

