package com.cluejuggler;

import com.cluejuggler.model.ClueList;
import com.cluejuggler.overlay.ClueJugglerOverlay;
import com.cluejuggler.overlay.ClueJugglerWorldOverlay;
import com.cluejuggler.service.ClueListService;
import com.cluejuggler.service.ClueLookupService;
import com.cluejuggler.service.ClueTextService;
import com.cluejuggler.service.ClueTimerService;
import com.cluejuggler.service.ClueTrackingService;
import com.cluejuggler.ui.ClueJugglerPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.ClueScrollConfig;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
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
	@Getter
	private ClueJugglerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	@Getter
	private ClueTextService textService;

	@Inject
	@Getter
	private ClueTrackingService trackingService;

	@Inject
	@Getter
	private ClueListService listService;

	@Inject
	@Getter
	private ClueLookupService lookupService;

	@Inject
	@Getter
	private ClueTimerService timerService;

	@Getter
	private ClueJugglerPanel panel;
	
	// Pending timer info - set when dropping a good clue, consumed when item spawns
	private String pendingTimerListName = null;
	private boolean hasPendingTimer = false;
	private NavigationButton navButton;
	private String currentClueText = null;

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(ClueJugglerPanel.class);
		panel.setPlugin(this);
		timerService.setPlugin(this);

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
		trackingService.clearTrackedClues();
		timerService.clearAllTimers();
		hasPendingTimer = false;
		pendingTimerListName = null;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() >= InterfaceID.TRAIL_MAP01 && event.getGroupId() <= InterfaceID.TRAIL_MAP11)
		{
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
		ClueScroll clue = clueScrollPlugin.getClue();
		if (clue != null && clue != trackingService.getLastProcessedClue())
		{
			trackingService.setLastProcessedClue(clue);
			trackingService.setLastClueWasTracked(false);
			clientThread.invokeLater(this::updateClueTextFromWidget);
		}
		else if (clue == null)
		{
			trackingService.setLastProcessedClue(null);
			trackingService.setLastClueWasTracked(false);
		}
		
		// Update timers
		timerService.onGameTick();
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (!hasPendingTimer || client.getLocalPlayer() == null)
		{
			return;
		}
		
		TileItem item = event.getItem();
		if (item == null)
		{
			return;
		}
		
		int itemId = item.getId();
		boolean isClueScroll = false;
		
		if (itemId == ItemID.TRAIL_CLUE_BEGINNER || itemId == ItemID.TRAIL_CLUE_MASTER || itemId == 23182)
		{
			isClueScroll = true;
		}
		else
		{
			ItemComposition itemComp = itemManager.getItemComposition(itemId);
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
		
		if (!isClueScroll)
		{
			return;
		}
		
		WorldPoint itemLocation = event.getTile().getWorldLocation();
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		
		// Only create timer if item spawned near player (within 2 tiles - they just dropped it)
		if (itemLocation.distanceTo(playerLocation) <= 2)
		{
			timerService.addTimer(item, itemLocation, pendingTimerListName);
			log.debug("Timer added for clue at {} (list: {})", itemLocation, pendingTimerListName);
			
			// Reset pending timer
			hasPendingTimer = false;
			pendingTimerListName = null;
		}
	}

	private void updateClueTextFromWidget()
	{
		ClueScroll clue = clueScrollPlugin.getClue();
		if (clue == null)
		{
			return;
		}

		if (!shouldTrackOnPickup() && !trackingService.wasLastClueTracked())
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

					ItemComposition itemComp = itemManager.getItemComposition(item.getId());
					if (itemComp != null && (itemComp.getName().startsWith("Clue scroll")
						|| itemComp.getName().startsWith("Challenge scroll")
						|| itemComp.getName().startsWith("Treasure scroll")))
					{
						ClueScroll foundClue = lookupService.findClueScroll(item.getId());
						boolean shouldTrack = false;

						if (foundClue == clue)
						{
							shouldTrack = true;
						}
						else if ((item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == 23182) &&
							(clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
							 clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue))
						{
							shouldTrack = true;
						}
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
						}

						if (shouldTrack)
						{
							if (item.getId() == ItemID.TRAIL_CLUE_BEGINNER || item.getId() == ItemID.TRAIL_CLUE_MASTER)
							{
								String clueText = textService.generateClueTextFromClue(clue);
								if (clueText != null && !clueText.isEmpty())
								{
									trackingService.trackClueByText(clueText, clue);
								}
							}
							else
							{
								trackingService.trackClue(item.getId(), clue);
							}
							trackingService.setLastClueWasTracked(true);
							break;
						}
					}
				}
			}
		}

		clientThread.invokeLater(() ->
		{
			String difficulty = textService.getClueDifficulty(clue, lookupService, trackingService);
			String formattedText = textService.generateFormattedClueText(clue);

			if (formattedText != null && !formattedText.isEmpty())
			{
				currentClueText = formattedText;
				panel.setCurrentStep(difficulty, formattedText);
				
				String identifier = lookupService.getClueIdentifier(clue, trackingService);
				if (identifier == null || identifier.isEmpty())
				{
					identifier = "text:" + formattedText.hashCode();
				}
				
				trackingService.setCurrentlyReadClueText(formattedText);
				trackingService.setCurrentlyReadClueIdentifier(identifier);
			}
		});
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

		boolean isClueScroll = false;
		if (itemId == ItemID.TRAIL_CLUE_BEGINNER || itemId == ItemID.TRAIL_CLUE_MASTER || itemId == 23182)
		{
			isClueScroll = true;
		}
		else
		{
			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			if (itemComposition != null)
			{
				String itemName = itemComposition.getName();
				if (itemName != null && (itemName.startsWith("Clue scroll")
					|| itemName.startsWith("Challenge scroll")
					|| itemName.startsWith("Treasure scroll")))
				{
					isClueScroll = true;
				}
			}
		}

		if (!isClueScroll)
		{
			return;
		}

		boolean shouldDeprioritize = false;
		
		WorldPoint tilePosition = WorldPoint.fromScene(client, event.getActionParam0(), event.getActionParam1(), client.getPlane());
		ClueTrackingService.DroppedClueInfo info = trackingService.getDroppedClueInfo(tilePosition);
		
		if (info != null)
		{
			if (info.isGood != null && !info.isGood)
			{
				shouldDeprioritize = true;
			}
			else if (info.customList != null && info.customList.isDeprioritize())
			{
				shouldDeprioritize = true;
			}
		}
		else
		{
			ClueScroll clue = lookupService.findClueScroll(itemId);
			if (clue != null)
			{
				String clueIdentifier = lookupService.getClueIdentifier(clue, trackingService);
				if (clueIdentifier != null && !clueIdentifier.isEmpty())
				{
					if (listService.isBadClueByIdentifier(clueIdentifier))
					{
						shouldDeprioritize = true;
					}
					else
					{
						ClueList customList = listService.getCustomListForClue(clueIdentifier);
						if (customList != null && customList.isDeprioritize())
						{
							shouldDeprioritize = true;
						}
					}
				}
			}
		}

		if (shouldDeprioritize)
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
	public void onItemDespawned(ItemDespawned event)
	{
		TileItem item = event.getItem();
		if (item == null)
		{
			return;
		}

		int itemId = item.getId();
		
		boolean isClueScroll = false;
		if (itemId == ItemID.TRAIL_CLUE_BEGINNER || itemId == ItemID.TRAIL_CLUE_MASTER || itemId == 23182)
		{
			isClueScroll = true;
		}
		else
		{
			ItemComposition itemComp = itemManager.getItemComposition(itemId);
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
		
		if (!isClueScroll)
		{
			return;
		}

		java.util.List<TileItem> remainingItems = event.getTile().getGroundItems();
		boolean hasRemainingClues = false;
		
		if (remainingItems != null)
		{
			for (TileItem remaining : remainingItems)
			{
				if (remaining == null || remaining == item)
				{
					continue;
				}
				
				int remainingId = remaining.getId();
				if (remainingId == ItemID.TRAIL_CLUE_BEGINNER || remainingId == ItemID.TRAIL_CLUE_MASTER || remainingId == 23182)
				{
					hasRemainingClues = true;
					break;
				}
				
				ItemComposition remainingComp = itemManager.getItemComposition(remainingId);
				if (remainingComp != null)
				{
					String remainingName = remainingComp.getName();
					if (remainingName != null && (remainingName.startsWith("Clue scroll")
						|| remainingName.startsWith("Challenge scroll")
						|| remainingName.startsWith("Treasure scroll")))
					{
						hasRemainingClues = true;
						break;
					}
				}
			}
		}
		
		if (!hasRemainingClues)
		{
			WorldPoint tilePosition = event.getTile().getWorldLocation();
			trackingService.clearDroppedClue(tilePosition);
			timerService.removeTimerAtLocation(tilePosition);
			log.debug("Clue despawned at {}, no more clues on tile", tilePosition);
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
		
		if (menuOption.equals("Drop"))
		{
			int itemId = event.getItemId();
			if (itemId <= 0)
			{
				return;
			}
			
			boolean isClueScroll = false;
			if (itemId == ItemID.TRAIL_CLUE_BEGINNER || itemId == ItemID.TRAIL_CLUE_MASTER || itemId == 23182)
			{
				isClueScroll = true;
			}
			else
			{
				ItemComposition itemComp = itemManager.getItemComposition(itemId);
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
			
			if (isClueScroll && client.getLocalPlayer() != null)
			{
				String clueText = trackingService.getCurrentlyReadClueText();
				String clueIdentifier = trackingService.getCurrentlyReadClueIdentifier();
				
				if (clueText != null && !clueText.isEmpty() && clueIdentifier != null && !clueIdentifier.isEmpty())
				{
					WorldPoint playerPosition = client.getLocalPlayer().getWorldLocation();
					
					listService.ensureListsLoaded();
					boolean isGood = listService.isGoodClueByIdentifier(clueIdentifier);
					boolean isBad = listService.isBadClueByIdentifier(clueIdentifier);
					ClueList customList = null;
					if (!isGood && !isBad)
					{
						customList = listService.getCustomListForClue(clueIdentifier);
					}
					
					Boolean goodBadStatus = null;
					if (isGood)
					{
						goodBadStatus = true;
					}
					else if (isBad)
					{
						goodBadStatus = false;
					}
					
					trackingService.markDroppedClue(playerPosition, clueText, goodBadStatus, customList);
					log.debug("Clue dropped via menu at {}: identifier={}, isGood={}, isBad={}, customList={}", 
						playerPosition, clueIdentifier, isGood, isBad, customList != null ? customList.getName() : "null");
					
					// Set up timer for good clues or non-deprioritized custom lists
					if (isGood)
					{
						hasPendingTimer = true;
						pendingTimerListName = null;
					}
					else if (customList != null && !customList.isDeprioritize())
					{
						hasPendingTimer = true;
						pendingTimerListName = customList.getName();
					}
					else
					{
						hasPendingTimer = false;
						pendingTimerListName = null;
					}
				}
			}
			return;
		}
		
		boolean shouldTrack = false;

		if (menuOption.equals("Take") && shouldTrackOnPickup())
		{
			shouldTrack = true;
		}
		else if (menuOption.equals("Read") && !shouldTrackOnPickup())
		{
			shouldTrack = true;
			trackingService.setLastClueWasTracked(false);
			clientThread.invokeLater(this::updateClueTextFromWidget);
		}

		if (shouldTrack)
		{
			int itemId = event.getItemId();
			if (itemId <= 0)
			{
				return;
			}

			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			if (itemComposition != null && (itemComposition.getName().startsWith("Clue scroll")
				|| itemComposition.getName().startsWith("Challenge scroll")
				|| itemComposition.getName().startsWith("Treasure scroll")))
			{
				if (menuOption.equals("Take"))
				{
					ClueScroll clue = lookupService.findClueScroll(itemId);
					if (clue != null)
					{
						trackingService.trackClue(itemId, clue);
					}
				}
			}
		}
	}

	private boolean shouldTrackOnPickup()
	{
		ClueScrollConfig clueScrollConfig = configManager.getConfig(ClueScrollConfig.class);
		return clueScrollConfig.identify() == ClueScrollConfig.IdentificationMode.ON_PICKUP;
	}

	public String getCurrentClueText()
	{
		return currentClueText;
	}

	public ClueScrollPlugin getClueScrollPlugin()
	{
		return clueScrollPlugin;
	}

	public Client getClient()
	{
		return client;
	}

	public ClientThread getClientThread()
	{
		return clientThread;
	}

	public ItemManager getItemManager()
	{
		return itemManager;
	}

	@Provides
	ClueJugglerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClueJugglerConfig.class);
	}

	// Legacy methods for backward compatibility with panel and overlays
	public String generateClueTextFromClue(ClueScroll clue)
	{
		return textService.generateClueTextFromClue(clue);
	}

	public String getClueIdentifier(ClueScroll clue)
	{
		return lookupService.getClueIdentifier(clue, trackingService);
	}

	public Map<Integer, ClueScroll> getTrackedClues()
	{
		return trackingService.getTrackedClues();
	}

	public Map<String, ClueScroll> getTrackedCluesByText()
	{
		return trackingService.getTrackedCluesByText();
	}

	public void ensureClueTracked()
	{
		if (!client.isClientThread())
		{
			clientThread.invokeLater(this::ensureClueTracked);
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

			ItemComposition itemComp = itemManager.getItemComposition(item.getId());
			if (itemComp == null)
			{
				continue;
			}

			String itemName = itemComp.getName();
			if (itemName != null && (itemName.startsWith("Clue scroll")
				|| itemName.startsWith("Challenge scroll")
				|| itemName.startsWith("Treasure scroll")))
			{
				ClueScroll foundClue = lookupService.findClueScroll(item.getId());
				if (foundClue != null)
				{
					trackingService.trackClue(item.getId(), foundClue);
				}
			}
		}
	}
}
