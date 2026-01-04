package com.cluejuggler;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.TileItem;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.ClueScrollConfig;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FairyRingClue;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Clue Juggler",
	description = "Track good and bad clue steps for juggling",
	tags = {"clues", "juggling", "tracker"}
)
@PluginDependency(ClueScrollPlugin.class)
public class ClueJugglerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClueJugglerOverlay overlay;

	@Inject
	private ClueJugglerWorldOverlay worldOverlay;

	@Inject
	private ClueScrollPlugin clueScrollPlugin;

	@Inject
	private ClueJugglerConfig config;

	@Inject
	private ConfigManager configManager;

	@Getter
	private final Map<Integer, ClueScroll> trackedClues = new ConcurrentHashMap<>();
	
	// For beginner/master clues: map of clueText -> ClueScroll
	// Since all beginner clues share the same itemId, we need to track by text
	@Getter
	private final Map<String, ClueScroll> trackedCluesByText = new ConcurrentHashMap<>();
	
	// Cache clue status (good/bad/unsure) to avoid expensive lookups every frame
	// Map: itemId or clueIdentifier -> Integer (1 = good, -1 = bad, 0 = unsure) or ClueList for custom lists
	private final Map<Object, Object> clueStatusCache = new ConcurrentHashMap<>();
	private final Map<Integer, String> itemIdToIdentifier = new ConcurrentHashMap<>();

	private ClueJugglerPanel panel;
	private NavigationButton navButton;
	private String currentClueText = null;
	private Integer lastMarkedItemId = null;
	private boolean lastMarkedWasGood = false;
	
	// Track the last clue we processed to avoid re-processing every tick
	private net.runelite.client.plugins.cluescrolls.clues.ClueScroll lastProcessedClue = null;
	private boolean lastClueWasTracked = false;
	
	// Track beginner/master clue positions and their status
	// Map: WorldPoint -> Object (Boolean true = good, Boolean false = bad, ClueList = custom list)
	// When a beginner/master clue is marked, we store the position where the player is standing
	// When a beginner/master clue item appears on the ground at that position, we show the stored status
	// Original simple logic: store position, check exact match
	private final Map<net.runelite.api.coords.WorldPoint, Object> beginnerMasterCluePositions = new ConcurrentHashMap<>();
	
	// Track if we need to mark the next dropped beginner/master clue
	// When you change a beginner clue's status, we set this flag
	// When the clue is dropped (ItemSpawned), we mark that position
	private Object pendingBeginnerMasterStatus = null;
	

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(ClueJugglerPanel.class);
		panel.setPlugin(this);
		
		// Don't load lists during startup - load them lazily when first needed
		// This prevents any config access from blocking startup
		// Lists will be loaded when:
		// - Panel is first viewed
		// - Overlay needs to check clue status
		// - User interacts with lists

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/cluejuggler.png");

		navButton = NavigationButton.builder()
			.tooltip("Clue Juggler")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		overlayManager.add(worldOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		overlayManager.remove(worldOverlay);
		currentClueText = null;
		trackedClues.clear();
		trackedCluesByText.clear();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Use the same widget IDs as ClueScrollPlugin - see ClueScrollPlugin.java line 726-750
		if (event.getGroupId() >= InterfaceID.TRAIL_MAP01 && event.getGroupId() <= InterfaceID.TRAIL_MAP11)
		{
			// For map clues, update immediately when the widget loads
			updateClueTextFromWidget();
		}
		else if (event.getGroupId() == InterfaceID.TRAIL_CLUETEXT)
		{
			updateClueTextFromWidget();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Only update if we have an active clue and it's different from the last one we processed
		net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
		if (clue != null && clue != lastProcessedClue)
		{
			lastProcessedClue = clue;
			lastClueWasTracked = false;
			clientThread.invokeLater(() -> updateClueTextFromWidget());
		}
		else if (clue == null)
		{
			lastProcessedClue = null;
			lastClueWasTracked = false;
		}
	}

	private void updateClueTextFromWidget()
	{
		net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
		if (clue == null)
		{
			return;
		}

		// Track the clue if we're in "on read" mode and haven't tracked it yet
		// Find the clue's item ID from inventory
		if (!shouldTrackOnPickup() && !lastClueWasTracked)
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

					net.runelite.api.ItemComposition itemComp = itemManager.getItemComposition(item.getId());
					if (itemComp != null && (itemComp.getName().startsWith("Clue scroll")
						|| itemComp.getName().startsWith("Challenge scroll")
						|| itemComp.getName().startsWith("Treasure scroll")))
					{
						// Check if this item matches the current clue
						ClueScroll foundClue = findClueScroll(item.getId());
						
						boolean shouldTrack = false;
						
						// For regular clues, match by foundClue == clue
						if (foundClue == clue)
						{
							shouldTrack = true;
							logToFile("TRACKING", "Matched regular clue by foundClue == clue");
						}
						// For beginner clues, check if item is TRAIL_CLUE_BEGINNER or 23182 and clue is BeginnerMapClue or AnagramClue
						else if ((item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == 23182) && 
							(clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
							 clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue))
						{
							shouldTrack = true;
							logToFile("TRACKING", "Matched beginner clue - itemId: " + item.getId() + " | clue type: " + clue.getClass().getSimpleName());
						}
						// For master clues, check if item is TRAIL_CLUE_MASTER and clue is not a regular type
						else if (item.getId() == ItemID.TRAIL_CLUE_MASTER && 
							!(clue instanceof net.runelite.client.plugins.cluescrolls.clues.CrypticClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.CipherClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.CoordinateClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.MusicClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.EmoteClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.MapClue ||
							  clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue))
						{
							shouldTrack = true;
							logToFile("TRACKING", "Matched master clue - itemId: " + item.getId() + " | clue type: " + clue.getClass().getSimpleName());
						}
						
						if (shouldTrack)
						{
							// Track it if not already tracked
							// For beginner/master clues, only track by text (not by itemId, since they all share the same ID)
							if (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == ItemID.TRAIL_CLUE_MASTER)
							{
								String clueText = generateClueTextFromClue(clue);
								if (clueText != null && !clueText.isEmpty())
								{
									if (!trackedCluesByText.containsKey(clueText))
									{
										trackedCluesByText.put(clueText, clue);
										logToFile("TRACKING", "Tracked beginner/master clue by text! itemId: " + item.getId() + " | clueText: " + clueText + " | clue: " + clue.getClass().getSimpleName());
									}
									else
									{
										logToFile("TRACKING", "Beginner/master clue already tracked by text: " + clueText);
									}
								}
								else
								{
									logToFile("TRACKING", "Failed to generate clue text for beginner/master clue");
								}
							}
							else
							{
								// For regular clues, track by itemId
								if (!trackedClues.containsKey(item.getId()))
								{
									trackedClues.put(item.getId(), clue);
									logToFile("TRACKING", "Tracked clue from updateClueTextFromWidget! itemId: " + item.getId() + " | clue: " + clue.getClass().getSimpleName());
								}
							}
							lastClueWasTracked = true;
							break;
						}
						else
						{
							logToFile("TRACKING", "Item did not match clue - itemId: " + item.getId() + " | foundClue: " + (foundClue != null ? foundClue.getClass().getSimpleName() : "null") + " | clue: " + clue.getClass().getSimpleName());
						}
					}
				}
			}
		}

		clientThread.invokeLater(() ->
		{
			// Get difficulty level and formatted clue text
			String difficulty = getClueDifficulty(clue);
			String formattedText = generateFormattedClueText(clue);
			
			// Update panel with difficulty and formatted text
			if (formattedText != null && !formattedText.isEmpty())
			{
				currentClueText = formattedText;
				panel.setCurrentStep(difficulty, formattedText);
			}
			
			// For beginner/master clues that are already in a list, tag the position for tracking
			// This ensures the overlay works even if you just read a clue that's already marked
			String clueText = generateClueTextFromClue(clue);
			if (clueText != null && !clueText.isEmpty() && panel != null)
			{
				boolean isGood = panel.isGoodClue(clueText);
				boolean isBad = panel.isBadClue(clueText);
				com.cluejuggler.ClueList customList = null;
				
				// Check if it's in a custom list
				String identifier = getClueIdentifier(clue);
				if (identifier == null || identifier.isEmpty())
				{
					identifier = "text:" + clueText.hashCode();
				}
				customList = panel.getCustomListForClue(identifier);
				if (customList == null)
				{
					customList = panel.getCustomListForClueByText(clueText);
				}
				
				// Check if it's a beginner or master clue
				boolean isBeginnerOrMaster = clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
					clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
				
				// Also check by item ID
				if (!isBeginnerOrMaster && client.isClientThread())
				{
					net.runelite.api.ItemContainer inventory = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
					if (inventory != null)
					{
						for (net.runelite.api.Item item : inventory.getItems())
						{
							if (item != null && (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || 
								item.getId() == ItemID.TRAIL_CLUE_MASTER || item.getId() == 23182))
							{
								isBeginnerOrMaster = true;
								break;
							}
						}
					}
				}
				
				if (isBeginnerOrMaster)
				{
					// Clear old position first to avoid conflicts when changing lists
					if (client.getLocalPlayer() != null)
					{
						net.runelite.api.coords.WorldPoint position = client.getLocalPlayer().getWorldLocation();
						beginnerMasterCluePositions.remove(position);
					}
					
					if (customList != null)
					{
						// Tag the position for custom list
						setBeginnerMasterCluePosition(clueText, identifier, customList);
					}
					else if (isGood || isBad)
					{
						// Tag the position for good/bad
						setBeginnerMasterCluePosition(clueText, identifier, isGood);
					}
				}
			}
		});
	}
	
	public String generateClueTextFromClue(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		String clueType = getClueTypeName(clue);
		String description = null;
		
		// Try to get text from clue types that have getText() method
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CrypticClue)
		{
			description = ((net.runelite.client.plugins.cluescrolls.clues.CrypticClue) clue).getText();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CipherClue)
		{
			description = ((net.runelite.client.plugins.cluescrolls.clues.CipherClue) clue).getText();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue)
		{
			description = ((net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue) clue).getText();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue)
		{
			description = ((net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue) clue).getText();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.EmoteClue)
		{
			description = ((net.runelite.client.plugins.cluescrolls.clues.EmoteClue) clue).getText();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue || clue instanceof net.runelite.client.plugins.cluescrolls.clues.MapClue)
		{
			// For Map Clues, generate text similar to makeOverlayHint
			description = generateMapClueText(clue);
		}
		
		if (description != null && !description.isEmpty())
		{
			// Shorten description if too long
			String shortDesc = description.length() > 50 ? description.substring(0, 47) + "..." : description;
			return clueType + ": " + shortDesc;
		}
		
		return clueType;
	}
	
	private String getClueDifficulty(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		// Get difficulty from the current clue item
		if (clueScrollPlugin == null)
		{
			return "Unknown";
		}
		
		// Check if it's a beginner clue by type
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue)
		{
			return "Beginner";
		}
		
		// Must be on client thread to access inventory
		if (!client.isClientThread())
		{
			return "Unknown";
		}
		
		// Try to get the actual item ID from inventory first
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
				
				net.runelite.api.ItemComposition itemComp = itemManager.getItemComposition(item.getId());
				if (itemComp != null && (itemComp.getName().startsWith("Clue scroll")
					|| itemComp.getName().startsWith("Challenge scroll")
					|| itemComp.getName().startsWith("Treasure scroll")))
				{
					// Check if this item matches the current clue
					ClueScroll foundClue = findClueScroll(item.getId());
					if (foundClue == clue)
					{
						itemId = item.getId();
						break;
					}
					// For beginner clues (map or anagram), check by item ID
					else if (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == 23182)
					{
						// If it's a beginner item ID, check if clue is BeginnerMapClue or if item name contains "beginner"
						if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue)
						{
							itemId = item.getId();
							break;
						}
						// Also check item name for beginner anagram clues
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
		
		// Fallback to getCurrentClueItemId if we didn't find it in inventory
		if (itemId <= 0)
		{
			itemId = getCurrentClueItemId();
		}
		
		if (itemId > 0)
		{
			// Check for beginner clue item IDs first
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
					// Check for beginner first (case insensitive)
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
		
		// If we still don't have an item ID, check the item name from inventory directly
		if (inventory != null)
		{
			for (net.runelite.api.Item item : inventory.getItems())
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}
				
				net.runelite.api.ItemComposition itemComp = itemManager.getItemComposition(item.getId());
				if (itemComp != null)
				{
					String itemName = itemComp.getName();
					if (itemName != null && (itemName.startsWith("Clue scroll")
						|| itemName.startsWith("Challenge scroll")
						|| itemName.startsWith("Treasure scroll")))
					{
						// Check if this is a beginner clue by name
						if (itemName.toLowerCase().contains("beginner"))
						{
							return "Beginner";
						}
					}
				}
			}
		}
		
		// Fallback: check if it's a master clue by checking trackedCluesByText
		String currentClueText = generateClueTextFromClue(clue);
		if (currentClueText != null && trackedCluesByText.containsKey(currentClueText))
		{
			// Check if it's not in regular trackedClues (might be master)
			for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
			{
				ClueScroll trackedClue = entry.getValue();
				if (trackedClue == null)
				{
					continue;
				}
				String trackedClueText = generateClueTextFromClue(trackedClue);
				if (trackedClueText != null && trackedClueText.equals(currentClueText))
				{
					// Found in regular clues, not master
					return "Unknown";
				}
			}
			// Not found in regular clues, might be master
			return "Master";
		}
		
		return "Unknown";
	}
	
	private String generateFormattedClueText(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		StringBuilder sb = new StringBuilder();
		
		// First line: Clue type (Map Clue, Anagram, etc.)
		String clueType = getClueTypeName(clue);
		sb.append(clueType);
		
		// Second line: NPC name (if available)
		String npcName = getNpcName(clue);
		if (npcName != null && !npcName.isEmpty())
		{
			sb.append("\n").append("NPC: ").append(npcName);
		}
		
		// Third line: Location text
		String locationText = getLocationText(clue);
		if (locationText != null && !locationText.isEmpty())
		{
			sb.append("\n").append(locationText);
		}
		
		// Fourth line: Item requirements (if any)
		String itemRequirements = getItemRequirements(clue);
		if (itemRequirements != null && !itemRequirements.isEmpty())
		{
			sb.append("\n").append(itemRequirements);
		}
		
		return sb.toString();
	}
	
	private String getNpcName(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		// Get NPC name for clues that have NPCs
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.AnagramClue anagramClue = (net.runelite.client.plugins.cluescrolls.clues.AnagramClue) clue;
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
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CipherClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.CipherClue cipherClue = (net.runelite.client.plugins.cluescrolls.clues.CipherClue) clue;
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
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CrypticClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.CrypticClue crypticClue = (net.runelite.client.plugins.cluescrolls.clues.CrypticClue) clue;
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
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.EmoteClue)
		{
			// EmoteClue doesn't have getNpcs method, skip NPC extraction for emote clues
			// NPC info is typically in the text/description
		}
		
		return null;
	}
	
	private String getLocationText(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		// Try to get location text based on clue type
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.MapClue || 
			clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue)
		{
			return generateMapClueText(clue);
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CrypticClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.CrypticClue crypticClue = (net.runelite.client.plugins.cluescrolls.clues.CrypticClue) clue;
			String solution = crypticClue.getSolution(clueScrollPlugin);
			if (solution != null && !solution.isEmpty())
			{
				return solution;
			}
			return crypticClue.getText();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CipherClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.CipherClue cipherClue = (net.runelite.client.plugins.cluescrolls.clues.CipherClue) clue;
			// Try to get location
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
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.AnagramClue anagramClue = (net.runelite.client.plugins.cluescrolls.clues.AnagramClue) clue;
			// Try to get area/location (NPC is handled separately in getNpcName)
			try
			{
				// Try to get area using reflection (getArea() is a private field accessed via method)
				String area = null;
				try
				{
					// Try to access the private 'area' field
					java.lang.reflect.Field areaField = anagramClue.getClass().getDeclaredField("area");
					areaField.setAccessible(true);
					area = (String) areaField.get(anagramClue);
				}
				catch (Exception e)
				{
					// Field doesn't exist or not accessible, try method
					try
					{
						java.lang.reflect.Method getAreaMethod = anagramClue.getClass().getMethod("getArea");
						area = (String) getAreaMethod.invoke(anagramClue);
					}
					catch (Exception e2)
					{
						// Method doesn't exist either
					}
				}
				
				// Build location text
				if (area != null && !area.isEmpty())
				{
					return "Location: " + area;
				}
				else
				{
					// Fallback to coordinates if no area
					net.runelite.api.coords.WorldPoint location = anagramClue.getLocation(clueScrollPlugin);
					if (location != null)
					{
						return "Location: " + location.getX() + ", " + location.getY();
					}
				}
			}
			catch (Exception e)
			{
			}
			return "Anagram Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CoordinateClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.CoordinateClue coordClue = (net.runelite.client.plugins.cluescrolls.clues.CoordinateClue) clue;
			net.runelite.api.coords.WorldPoint location = coordClue.getLocation(clueScrollPlugin);
			if (location != null)
			{
				return location.getX() + ", " + location.getY();
			}
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.MusicClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.MusicClue musicClue = (net.runelite.client.plugins.cluescrolls.clues.MusicClue) clue;
			return musicClue.getSong();
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.EmoteClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.EmoteClue emoteClue = (net.runelite.client.plugins.cluescrolls.clues.EmoteClue) clue;
			return emoteClue.getText();
		}
		
		return null;
	}
	
	private String getItemRequirements(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		// Get item requirements if the clue has them
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.EmoteClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.EmoteClue emoteClue = (net.runelite.client.plugins.cluescrolls.clues.EmoteClue) clue;
			net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement[] requirements = emoteClue.getItemRequirements();
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
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue)
		{
			net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue skillClue = (net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue) clue;
			net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement[] requirements = skillClue.getItemRequirements();
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
		
		// Check for spade requirement
		if (clue.isRequiresSpade())
		{
			return "Requires: Spade";
		}
		
		return null;
	}
	
	private String getClueTypeName(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue)
		{
			return "Beginner Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.MapClue)
		{
			return "Map Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CrypticClue)
		{
			return "Cryptic Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CipherClue)
		{
			return "Cipher Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.EmoteClue)
		{
			return "Emote Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue)
		{
			return "Falo Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue)
		{
			return "Three Step";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue)
		{
			return "Anagram Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.CoordinateClue)
		{
			return "Coordinate Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.MusicClue)
		{
			return "Music Clue";
		}
		else if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.HotColdClue)
		{
			return "Hot/Cold Clue";
		}
		else
		{
			return "Clue";
		}
	}
	
	private String generateMapClueText(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		// Use reflection to get the description field from MapClue
		try
		{
			java.lang.reflect.Field descriptionField = clue.getClass().getSuperclass().getDeclaredField("description");
			descriptionField.setAccessible(true);
			String description = (String) descriptionField.get(clue);
			
			if (description != null && !description.isEmpty())
			{
				return description;
			}
		}
		catch (Exception e)
		{
		}
		
		// Fallback: try to get object ID and construct text
		try
		{
			java.lang.reflect.Method getObjectIdsMethod = clue.getClass().getMethod("getObjectIds");
			int[] objectIds = (int[]) getObjectIdsMethod.invoke(clue);
			
			if (objectIds != null && objectIds.length > 0 && objectIds[0] != -1)
			{
				net.runelite.api.ObjectComposition objectComp = client.getObjectDefinition(objectIds[0]);
				if (objectComp != null)
				{
					return "Travel to the destination and click the " + objectComp.getName() + ".";
				}
			}
			else
			{
				return "Travel to the destination and dig on the marked tile.";
			}
		}
		catch (Exception e)
		{
		}
		
		return "Map Clue";
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.deprioritizeBadClues())
		{
			return;
		}

		if (!event.getOption().equals("Take"))
		{
			return;
		}

		int itemId = event.getIdentifier();
		if (itemId <= 0)
		{
			return;
		}

		ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		if (itemComposition == null)
		{
			return;
		}

		String itemName = itemComposition.getName();
		if (itemName == null || (!itemName.startsWith("Clue scroll")
			&& !itemName.startsWith("Challenge scroll")
			&& !itemName.startsWith("Treasure scroll")))
		{
			return;
		}

		ClueScroll clue = findClueScroll(itemId);
		if (clue == null)
		{
			return;
		}

		if (shouldTrackOnPickup())
		{
			trackedClues.put(itemId, clue);
			logToFile("TRACKING", "onMenuEntryAdded - Tracked clue! itemId: " + itemId + " | clue: " + clue.getClass().getSimpleName());
		}
		else
		{
			logToFile("TRACKING", "onMenuEntryAdded - Not tracking (shouldTrackOnPickup is false)");
		}

		String clueText = generateClueTextFromClue(clue);
		if (clueText == null || clueText.isEmpty())
		{
			return;
		}

		boolean isBad = panel.isBadClue(clueText);
		if (isBad)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			int takeIndex = -1;
			int walkHereIndex = -1;

			for (int i = 0; i < menuEntries.length; i++)
			{
				MenuEntry entry = menuEntries[i];
				if (entry.getIdentifier() == itemId && entry.getOption().equals("Take"))
				{
					takeIndex = i;
				}
				else if (entry.getOption().equals("Walk here"))
				{
					walkHereIndex = i;
				}
			}

			if (takeIndex >= 0 && walkHereIndex >= 0 && takeIndex < walkHereIndex)
			{
				MenuEntry takeEntry = menuEntries[takeIndex];
				MenuEntry walkEntry = menuEntries[walkHereIndex];
				menuEntries[takeIndex] = walkEntry;
				menuEntries[walkHereIndex] = takeEntry;
				client.setMenuEntries(menuEntries);
			}
			else if (takeIndex >= 0)
			{
				menuEntries[takeIndex].setDeprioritized(true);
			}
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		// When a beginner/master clue is dropped by the player, mark the position
		TileItem item = event.getItem();
		
		// Early exits
		if (item == null)
		{
			return;
		}
		
		int itemId = item.getId();
		
		// Only process beginner/master clues (itemId check ensures easy/medium/hard/elite are ignored)
		boolean isBeginnerOrMaster = itemId == ItemID.TRAIL_CLUE_BEGINNER || 
			itemId == ItemID.TRAIL_CLUE_MASTER || 
			itemId == 23182;
		
		if (!isBeginnerOrMaster)
		{
			return;
		}
		
		// Only apply if we have a pending status (clue was just changed via buttons)
		if (pendingBeginnerMasterStatus == null)
		{
			return;
		}
		
		// Mark the position where the item spawned and the player position
		// This is only for beginner/master clues that were just dropped by the player
		if (client.isClientThread())
		{
			net.runelite.api.coords.WorldPoint tilePosition = event.getTile().getWorldLocation();
			beginnerMasterCluePositions.put(tilePosition, pendingBeginnerMasterStatus);
			log.debug("Marked beginner/master clue at tile position: {} with status: {}", tilePosition, pendingBeginnerMasterStatus);
			
			if (client.getLocalPlayer() != null)
			{
				net.runelite.api.coords.WorldPoint playerPosition = client.getLocalPlayer().getWorldLocation();
				beginnerMasterCluePositions.put(playerPosition, pendingBeginnerMasterStatus);
				log.debug("Marked beginner/master clue at player position: {} with status: {}", playerPosition, pendingBeginnerMasterStatus);
			}
			
			// Clear the pending status immediately after use
			pendingBeginnerMasterStatus = null;
		}
		else
		{
			clientThread.invoke(() ->
			{
				net.runelite.api.coords.WorldPoint tilePosition = event.getTile().getWorldLocation();
				beginnerMasterCluePositions.put(tilePosition, pendingBeginnerMasterStatus);
				log.debug("Marked beginner/master clue at tile position: {} with status: {}", tilePosition, pendingBeginnerMasterStatus);
				
				if (client.getLocalPlayer() != null)
				{
					net.runelite.api.coords.WorldPoint playerPosition = client.getLocalPlayer().getWorldLocation();
					beginnerMasterCluePositions.put(playerPosition, pendingBeginnerMasterStatus);
					log.debug("Marked beginner/master clue at player position: {} with status: {}", playerPosition, pendingBeginnerMasterStatus);
				}
				
				// Clear the pending status immediately after use
				pendingBeginnerMasterStatus = null;
			});
		}
	}
	
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuOption() == null)
		{
			return;
		}

		String menuOption = event.getMenuOption();
		boolean shouldTrack = false;

		if (menuOption.equals("Take") && shouldTrackOnPickup())
		{
			shouldTrack = true;
		}
		else if (menuOption.equals("Read") && !shouldTrackOnPickup())
		{
			shouldTrack = true;
			// Reset tracking flag so we re-process the clue
			lastClueWasTracked = false;
			clientThread.invokeLater(() -> updateClueTextFromWidget());
		}

		if (shouldTrack)
		{
			int itemId = event.getItemId();
			logToFile("TRACKING", "onMenuOptionClicked - menuOption: " + menuOption + " | itemId: " + itemId + " | shouldTrackOnPickup: " + shouldTrackOnPickup());
			if (itemId <= 0)
			{
				logToFile("TRACKING", "itemId <= 0, returning");
				return;
			}

			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			if (itemComposition != null && (itemComposition.getName().startsWith("Clue scroll")
				|| itemComposition.getName().startsWith("Challenge scroll")
				|| itemComposition.getName().startsWith("Treasure scroll")))
			{
				ClueScroll clue = null;
				
				// For "Read" operations, tracking will happen in updateClueTextFromWidget when the clue loads
				// For "Take" operations, use findClueScroll
				if (menuOption.equals("Take"))
				{
					clue = findClueScroll(itemId);
					if (clue != null)
					{
						trackedClues.put(itemId, clue);
						logToFile("TRACKING", "Tracked clue! itemId: " + itemId + " | clue: " + clue.getClass().getSimpleName());
					}
					else
					{
						logToFile("TRACKING", "findClueScroll returned null for itemId: " + itemId);
					}
				}
				else if (menuOption.equals("Read"))
				{
					logToFile("TRACKING", "Read operation - tracking will happen in updateClueTextFromWidget when clue loads");
				}
			}
			else
			{
				logToFile("TRACKING", "Not a clue scroll - itemName: " + (itemComposition != null ? itemComposition.getName() : "null"));
			}
		}
		else
		{
			logToFile("TRACKING", "shouldTrack is false - menuOption: " + menuOption + " | shouldTrackOnPickup: " + shouldTrackOnPickup());
		}
	}

	private boolean shouldTrackOnPickup()
	{
		ClueScrollConfig clueScrollConfig = configManager.getConfig(ClueScrollConfig.class);
		return clueScrollConfig.identify() == ClueScrollConfig.IdentificationMode.ON_PICKUP;
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

	public String getCurrentClueText()
	{
		return currentClueText;
	}
	
	public String getClueIdentifier(net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue)
	{
		if (clue == null)
		{
			return null;
		}
		
		// For regular clues, use itemId as identifier
		int itemId = getCurrentClueItemId();
		if (itemId > 0 && itemId != ItemID.TRAIL_CLUE_BEGINNER && itemId != ItemID.TRAIL_CLUE_MASTER)
		{
			return "item:" + itemId;
		}
		
		// For beginner/master clues, use the generated clue text as identifier
		// This is consistent and doesn't require reflection
		if (clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue)
		{
			// Generate the clue text using public methods (no reflection needed)
			String clueText = generateClueTextFromClue(clue);
			if (clueText != null && !clueText.isEmpty())
			{
				// Use the clue text hash as identifier - this is consistent across reads
				return "beginner:" + clueText.hashCode();
			}
			// Fallback to hash of clue object (shouldn't happen, but just in case)
			return "beginner:" + Integer.toHexString(clue.hashCode());
		}
		
		// For master clues or other special cases, use clue text hash
		String clueText = generateClueTextFromClue(clue);
		if (clueText != null && !clueText.isEmpty())
		{
			return "master:" + clueText.hashCode();
		}
		
		// Final fallback
		return "clue:" + Integer.toHexString(clue.hashCode());
	}

	public ClueJugglerPanel getPanel()
	{
		return panel;
	}

	public ClueScrollPlugin getClueScrollPlugin()
	{
		return clueScrollPlugin;
	}

	public ClueJugglerConfig getConfig()
	{
		return config;
	}

	public Client getClient()
	{
		return client;
	}
	
	public ClientThread getClientThread()
	{
		return clientThread;
	}

	@Provides
	ClueJugglerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClueJugglerConfig.class);
	}

	public void ensureClueTracked()
	{
		if (!client.isClientThread())
		{
			clientThread.invokeLater(() -> ensureClueTracked());
			return;
		}

		net.runelite.api.ItemContainer inventory = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
		if (inventory == null)
		{
			return;
		}

		for (net.runelite.api.Item item : inventory.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}

			net.runelite.api.ItemComposition itemComp = itemManager.getItemComposition(item.getId());
			if (itemComp == null)
			{
				continue;
			}

			String itemName = itemComp.getName();
			if (itemName != null && (itemName.startsWith("Clue scroll")
				|| itemName.startsWith("Challenge scroll")
				|| itemName.startsWith("Treasure scroll")))
			{
				ClueScroll foundClue = findClueScroll(item.getId());
				if (foundClue != null)
				{
					trackedClues.put(item.getId(), foundClue);
				}
			}
		}
	}

	public void setLastMarkedClue(int itemId, boolean isGood)
	{
		lastMarkedItemId = itemId;
		lastMarkedWasGood = isGood;
	}

	public Integer getLastMarkedItemId()
	{
		return lastMarkedItemId;
	}

	public boolean wasLastMarkedGood()
	{
		return lastMarkedWasGood;
	}
	
	public void setBeginnerMasterCluePosition(String clueText, String clueIdentifier, boolean isGood)
	{
		// Mark the position IMMEDIATELY when the button is clicked
		// The player is standing at the clue's location when they read and tag it
		if (client.getLocalPlayer() != null)
		{
			net.runelite.api.coords.WorldPoint playerPosition = client.getLocalPlayer().getWorldLocation();
			// Clear any old position at this location first
			beginnerMasterCluePositions.put(playerPosition, isGood);
			log.debug("Immediately marked beginner/master clue at player position: {} with good={}", playerPosition, isGood);
		}
		
		// Also store for ItemSpawned event (when dropped)
		pendingBeginnerMasterStatus = isGood;
	}
	
	public void setBeginnerMasterCluePosition(String clueText, String clueIdentifier, ClueList customList)
	{
		// Mark the position IMMEDIATELY when the button is clicked
		// The player is standing at the clue's location when they read and tag it
		if (client.getLocalPlayer() != null)
		{
			net.runelite.api.coords.WorldPoint playerPosition = client.getLocalPlayer().getWorldLocation();
			// Clear any old position at this location first
			beginnerMasterCluePositions.put(playerPosition, customList);
			log.debug("Immediately marked beginner/master clue at player position: {} with customList={}", playerPosition, customList.getName());
		}
		
		// Also store for ItemSpawned event (when dropped)
		pendingBeginnerMasterStatus = customList;
		
		// Ensure the clue is in trackedCluesByText for panel display
		if (clueScrollPlugin != null && clueText != null && !clueText.isEmpty())
		{
			ClueScroll currentClue = clueScrollPlugin.getClue();
			if (currentClue != null)
			{
				boolean isBeginnerOrMaster = currentClue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
					currentClue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue ||
					itemManager.getItemComposition(ItemID.TRAIL_CLUE_MASTER) != null;
				
				if (isBeginnerOrMaster && !trackedCluesByText.containsKey(clueText))
				{
					trackedCluesByText.put(clueText, currentClue);
				}
			}
		}
	}
	
	public Boolean getBeginnerMasterClueStatus(net.runelite.api.coords.WorldPoint position)
	{
		// Check exact position match first
		Object status = beginnerMasterCluePositions.get(position);
		if (status instanceof Boolean)
		{
			return (Boolean) status;
		}
		// Also check nearby positions (within 1 tile) in case player moved slightly
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0) continue;
				net.runelite.api.coords.WorldPoint nearbyPos = new net.runelite.api.coords.WorldPoint(
					position.getX() + dx, position.getY() + dy, position.getPlane());
				status = beginnerMasterCluePositions.get(nearbyPos);
				if (status instanceof Boolean)
				{
					return (Boolean) status;
				}
			}
		}
		return null;
	}
	
	public ClueList getBeginnerMasterClueCustomList(net.runelite.api.coords.WorldPoint position)
	{
		// Check exact position match first
		Object status = beginnerMasterCluePositions.get(position);
		if (status instanceof ClueList)
		{
			return (ClueList) status;
		}
		// Also check nearby positions (within 1 tile) in case player moved slightly
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0) continue;
				net.runelite.api.coords.WorldPoint nearbyPos = new net.runelite.api.coords.WorldPoint(
					position.getX() + dx, position.getY() + dy, position.getPlane());
				status = beginnerMasterCluePositions.get(nearbyPos);
				if (status instanceof ClueList)
				{
					return (ClueList) status;
				}
			}
		}
		return null;
	}
	
	public Map<net.runelite.api.coords.WorldPoint, Object> getBeginnerMasterCluePositions()
	{
		return beginnerMasterCluePositions;
	}
	
	public void clearClueStatusCache()
	{
		clueStatusCache.clear();
	}
	
	public Integer getCachedClueStatus(Object key)
	{
		Object value = clueStatusCache.get(key);
		if (value instanceof Integer)
		{
			return (Integer) value;
		}
		return null;
	}
	
	public Object getCachedClueStatusObject(Object key)
	{
		return clueStatusCache.get(key);
	}
	
	public void cacheClueStatus(Object key, Object status)
	{
		clueStatusCache.put(key, status);
	}
	
	public void clearClueStatusCacheEntry(Object key)
	{
		clueStatusCache.remove(key);
	}
	
	public void cacheItemIdToIdentifier(int itemId, String identifier)
	{
		if (itemId > 0 && identifier != null && !identifier.isEmpty())
		{
			itemIdToIdentifier.put(itemId, identifier);
		}
	}
	
	public String getIdentifierForItemId(int itemId)
	{
		return itemIdToIdentifier.get(itemId);
	}
	
	public void clearBeginnerMasterCluePosition(net.runelite.api.coords.WorldPoint position)
	{
		beginnerMasterCluePositions.remove(position);
	}
	
	public void clearBeginnerMasterCluePositionAtCurrentLocation()
	{
		if (client.isClientThread())
		{
			if (client.getLocalPlayer() != null)
			{
				net.runelite.api.coords.WorldPoint position = client.getLocalPlayer().getWorldLocation();
				beginnerMasterCluePositions.remove(position);
			}
		}
		else
		{
			clientThread.invoke(() ->
			{
				if (client.getLocalPlayer() != null)
				{
					net.runelite.api.coords.WorldPoint position = client.getLocalPlayer().getWorldLocation();
					beginnerMasterCluePositions.remove(position);
				}
			});
		}
	}

	public int getCurrentClueItemId()
	{
		if (!client.isClientThread())
		{
			// Must be called on client thread
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

		// For beginner clues, try to find the actual item ID from inventory first
		if (currentClue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue)
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
					
					// Check if it's a beginner clue item
					if (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == 23182)
					{
						net.runelite.api.ItemComposition itemComp = itemManager.getItemComposition(item.getId());
						if (itemComp != null && (itemComp.getName().startsWith("Clue scroll")
							|| itemComp.getName().startsWith("Challenge scroll")
							|| itemComp.getName().startsWith("Treasure scroll")))
						{
							return item.getId();
						}
					}
				}
			}
			// Fallback to constant if not found in inventory
			return ItemID.TRAIL_CLUE_BEGINNER;
		}
		// For master clues, we can't easily detect them, but check trackedCluesByText
		String currentClueText = generateClueTextFromClue(currentClue);
		if (currentClueText != null && !currentClueText.isEmpty() && trackedCluesByText.containsKey(currentClueText))
		{
			// Check if it might be a master clue (not in regular trackedClues)
			// For now, we'll check trackedCluesByText and if it's there but not in trackedClues, assume master
			boolean foundInRegular = false;
			for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
			{
				ClueScroll trackedClue = entry.getValue();
				if (trackedClue == null)
				{
					continue;
				}
				String trackedClueText = generateClueTextFromClue(trackedClue);
				if (trackedClueText != null && trackedClueText.equals(currentClueText))
				{
					return entry.getKey();
				}
			}
			// If not found in regular clues, it might be a master clue
			// We can't determine the exact item ID for master clues, so return -1
			// But the text-based tracking should still work
		}

		// For regular clues, check trackedClues
		if (currentClueText == null || currentClueText.isEmpty())
		{
			return -1;
		}

		for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
		{
			ClueScroll trackedClue = entry.getValue();
			if (trackedClue == null)
			{
				continue;
			}

			String trackedClueText = generateClueTextFromClue(trackedClue);
			if (trackedClueText != null && trackedClueText.equals(currentClueText))
			{
				return entry.getKey();
			}
		}

		return -1;
	}

	private void logToFile(String action, String message)
	{
		// Remove file logging to prevent client thread blocking
		// Use slf4j logger instead if needed
	}
}

