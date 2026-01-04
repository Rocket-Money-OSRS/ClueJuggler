package com.cluejuggler.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;

@Singleton
public class ClueTextService
{
	private final Client client;
	private final ItemManager itemManager;
	private final ClueScrollPlugin clueScrollPlugin;
	
	@Inject
	public ClueTextService(Client client, ItemManager itemManager, ClueScrollPlugin clueScrollPlugin)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.clueScrollPlugin = clueScrollPlugin;
	}
	
	public String generateClueTextFromClue(ClueScroll clue)
	{
		String clueType = getClueTypeName(clue);
		String description = null;
		
		if (clue instanceof CrypticClue)
		{
			description = ((CrypticClue) clue).getText();
		}
		else if (clue instanceof CipherClue)
		{
			description = ((CipherClue) clue).getText();
		}
		else if (clue instanceof FaloTheBardClue)
		{
			description = ((FaloTheBardClue) clue).getText();
		}
		else if (clue instanceof ThreeStepCrypticClue)
		{
			description = ((ThreeStepCrypticClue) clue).getText();
		}
		else if (clue instanceof EmoteClue)
		{
			description = ((EmoteClue) clue).getText();
		}
		else if (clue instanceof BeginnerMapClue || clue instanceof MapClue)
		{
			description = generateMapClueText(clue);
		}
		
		if (description != null && !description.isEmpty())
		{
			String shortDesc = description.length() > 50 ? description.substring(0, 47) + "..." : description;
			return clueType + ": " + shortDesc;
		}
		
		return clueType;
	}
	
	public String generateFormattedClueText(ClueScroll clue)
	{
		StringBuilder sb = new StringBuilder();
		
		String clueType = getClueTypeName(clue);
		sb.append(clueType);
		
		String npcName = getNpcName(clue);
		if (npcName != null && !npcName.isEmpty())
		{
			sb.append("\n").append("NPC: ").append(npcName);
		}
		
		String locationText = getLocationText(clue);
		if (locationText != null && !locationText.isEmpty())
		{
			sb.append("\n").append(locationText);
		}
		
		String itemRequirements = getItemRequirements(clue);
		if (itemRequirements != null && !itemRequirements.isEmpty())
		{
			sb.append("\n").append(itemRequirements);
		}
		
		return sb.toString();
	}
	
	public String getClueDifficulty(ClueScroll clue, ClueLookupService lookupService, ClueTrackingService trackingService)
	{
		if (clueScrollPlugin == null)
		{
			return "Unknown";
		}
		
		if (clue instanceof BeginnerMapClue)
		{
			return "Beginner";
		}
		
		if (!client.isClientThread())
		{
			return "Unknown";
		}
		
		int itemId = -1;
		net.runelite.api.ItemContainer inventory = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
		if (inventory != null)
		{
			for (net.runelite.api.Item item : inventory.getItems())
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}
				
				ItemComposition itemComp = itemManager.getItemComposition(item.getId());
				if (itemComp != null && (itemComp.getName().startsWith("Clue scroll")
					|| itemComp.getName().startsWith("Challenge scroll")
					|| itemComp.getName().startsWith("Treasure scroll")))
				{
					ClueScroll foundClue = lookupService.findClueScroll(item.getId());
					if (foundClue == clue)
					{
						itemId = item.getId();
						break;
					}
					else if (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == 23182)
					{
						if (clue instanceof BeginnerMapClue)
						{
							itemId = item.getId();
							break;
						}
						String itemName = itemComp.getName();
						if (itemName != null && itemName.toLowerCase().contains("beginner"))
						{
							itemId = item.getId();
							break;
						}
					}
				}
			}
		}
		
		if (itemId <= 0)
		{
			itemId = lookupService.getCurrentClueItemId(trackingService);
		}
		
		if (itemId > 0)
		{
			if (itemId == ItemID.TRAIL_CLUE_BEGINNER || itemId == 23182)
			{
				return "Beginner";
			}
			
			ItemComposition itemComp = itemManager.getItemComposition(itemId);
			if (itemComp != null)
			{
				String itemName = itemComp.getName();
				if (itemName != null)
				{
					if (itemName.toLowerCase().contains("beginner"))
					{
						return "Beginner";
					}
					else if (itemName.contains("(easy)"))
					{
						return "Easy";
					}
					else if (itemName.contains("(medium)"))
					{
						return "Medium";
					}
					else if (itemName.contains("(hard)"))
					{
						return "Hard";
					}
					else if (itemName.contains("(elite)"))
					{
						return "Elite";
					}
					else if (itemName.contains("(master)"))
					{
						return "Master";
					}
				}
			}
		}
		
		if (inventory != null)
		{
			for (net.runelite.api.Item item : inventory.getItems())
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}
				
				ItemComposition itemComp = itemManager.getItemComposition(item.getId());
				if (itemComp != null)
				{
					String itemName = itemComp.getName();
					if (itemName != null && (itemName.startsWith("Clue scroll")
						|| itemName.startsWith("Challenge scroll")
						|| itemName.startsWith("Treasure scroll")))
					{
						if (itemName.toLowerCase().contains("beginner"))
						{
							return "Beginner";
						}
					}
				}
			}
		}
		
		String currentClueText = generateClueTextFromClue(clue);
		if (currentClueText != null && trackingService.isTrackedByText(currentClueText))
		{
			for (java.util.Map.Entry<Integer, ClueScroll> entry : trackingService.getTrackedClues().entrySet())
			{
				ClueScroll trackedClue = entry.getValue();
				if (trackedClue == null)
				{
					continue;
				}
				String trackedClueText = generateClueTextFromClue(trackedClue);
				if (trackedClueText != null && trackedClueText.equals(currentClueText))
				{
					return "Unknown";
				}
			}
			return "Master";
		}
		
		return "Unknown";
	}
	
	public String getClueTypeName(ClueScroll clue)
	{
		if (clue instanceof BeginnerMapClue)
		{
			return "Beginner Clue";
		}
		else if (clue instanceof MapClue)
		{
			return "Map Clue";
		}
		else if (clue instanceof CrypticClue)
		{
			return "Cryptic Clue";
		}
		else if (clue instanceof CipherClue)
		{
			return "Cipher Clue";
		}
		else if (clue instanceof EmoteClue)
		{
			return "Emote Clue";
		}
		else if (clue instanceof FaloTheBardClue)
		{
			return "Falo Clue";
		}
		else if (clue instanceof ThreeStepCrypticClue)
		{
			return "Three Step";
		}
		else if (clue instanceof AnagramClue)
		{
			return "Anagram Clue";
		}
		else if (clue instanceof CoordinateClue)
		{
			return "Coordinate Clue";
		}
		else if (clue instanceof MusicClue)
		{
			return "Music Clue";
		}
		else if (clue instanceof HotColdClue)
		{
			return "Hot/Cold Clue";
		}
		else
		{
			return "Clue";
		}
	}
	
	public String getNpcName(ClueScroll clue)
	{
		if (clue instanceof AnagramClue)
		{
			AnagramClue anagramClue = (AnagramClue) clue;
			try
			{
				String[] npcs = anagramClue.getNpcs(clueScrollPlugin);
				if (npcs != null && npcs.length > 0 && npcs[0] != null && !npcs[0].isEmpty())
				{
					return npcs[0];
				}
			}
			catch (Exception e)
			{
			}
		}
		else if (clue instanceof CipherClue)
		{
			CipherClue cipherClue = (CipherClue) clue;
			try
			{
				String[] npcs = cipherClue.getNpcs(clueScrollPlugin);
				if (npcs != null && npcs.length > 0 && npcs[0] != null && !npcs[0].isEmpty())
				{
					return npcs[0];
				}
			}
			catch (Exception e)
			{
			}
		}
		else if (clue instanceof CrypticClue)
		{
			CrypticClue crypticClue = (CrypticClue) clue;
			try
			{
				String[] npcs = crypticClue.getNpcs(clueScrollPlugin);
				if (npcs != null && npcs.length > 0 && npcs[0] != null && !npcs[0].isEmpty())
				{
					return npcs[0];
				}
			}
			catch (Exception e)
			{
			}
		}
		
		return null;
	}
	
	public String getLocationText(ClueScroll clue)
	{
		if (clue instanceof MapClue || clue instanceof BeginnerMapClue)
		{
			return generateMapClueText(clue);
		}
		else if (clue instanceof CrypticClue)
		{
			CrypticClue crypticClue = (CrypticClue) clue;
			String solution = crypticClue.getSolution(clueScrollPlugin);
			if (solution != null && !solution.isEmpty())
			{
				return solution;
			}
			return crypticClue.getText();
		}
		else if (clue instanceof CipherClue)
		{
			CipherClue cipherClue = (CipherClue) clue;
			try
			{
				net.runelite.api.coords.WorldPoint location = cipherClue.getLocation(clueScrollPlugin);
				if (location != null)
				{
					return location.getX() + ", " + location.getY();
				}
			}
			catch (Exception e)
			{
			}
			return cipherClue.getText();
		}
		else if (clue instanceof AnagramClue)
		{
			AnagramClue anagramClue = (AnagramClue) clue;
			net.runelite.api.coords.WorldPoint location = anagramClue.getLocation(clueScrollPlugin);
			if (location != null)
			{
				return "Location: " + location.getX() + ", " + location.getY();
			}
			return "Anagram Clue";
		}
		else if (clue instanceof CoordinateClue)
		{
			CoordinateClue coordClue = (CoordinateClue) clue;
			net.runelite.api.coords.WorldPoint location = coordClue.getLocation(clueScrollPlugin);
			if (location != null)
			{
				return location.getX() + ", " + location.getY();
			}
		}
		else if (clue instanceof MusicClue)
		{
			MusicClue musicClue = (MusicClue) clue;
			return musicClue.getSong();
		}
		else if (clue instanceof EmoteClue)
		{
			EmoteClue emoteClue = (EmoteClue) clue;
			return emoteClue.getText();
		}
		
		return null;
	}
	
	public String getItemRequirements(ClueScroll clue)
	{
		if (clue instanceof EmoteClue)
		{
			EmoteClue emoteClue = (EmoteClue) clue;
			ItemRequirement[] requirements = emoteClue.getItemRequirements();
			if (requirements != null && requirements.length > 0)
			{
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < requirements.length; i++)
				{
					if (i > 0)
					{
						sb.append(", ");
					}
					sb.append(requirements[i].getCollectiveName(clueScrollPlugin.getClient()));
				}
				return sb.toString();
			}
		}
		else if (clue instanceof SkillChallengeClue)
		{
			SkillChallengeClue skillClue = (SkillChallengeClue) clue;
			ItemRequirement[] requirements = skillClue.getItemRequirements();
			if (requirements != null && requirements.length > 0)
			{
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < requirements.length; i++)
				{
					if (i > 0)
					{
						sb.append(", ");
					}
					sb.append(requirements[i].getCollectiveName(clueScrollPlugin.getClient()));
				}
				return sb.toString();
			}
		}
		
		if (clue.isRequiresSpade())
		{
			return "Requires: Spade";
		}
		
		return null;
	}
	
	public String generateMapClueText(ClueScroll clue)
	{
		if (clue instanceof MapClue)
		{
			MapClue mapClue = (MapClue) clue;
			net.runelite.api.coords.WorldPoint location = mapClue.getLocation(clueScrollPlugin);
			if (location != null)
			{
				return "Map: " + location.getX() + ", " + location.getY();
			}
		}
		else if (clue instanceof BeginnerMapClue)
		{
			BeginnerMapClue beginnerMapClue = (BeginnerMapClue) clue;
			net.runelite.api.coords.WorldPoint location = beginnerMapClue.getLocation(clueScrollPlugin);
			if (location != null)
			{
				return "Map: " + location.getX() + ", " + location.getY();
			}
		}
		
		return "Map Clue";
	}
}

