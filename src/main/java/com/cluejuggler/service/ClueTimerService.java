package com.cluejuggler.service;

import com.cluejuggler.ClueJugglerConfig;
import com.cluejuggler.ClueJugglerPlugin;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.RSTimeUnit;

@Slf4j
@Singleton
public class ClueTimerService
{
	@Inject
	private Client client;
	
	@Inject
	private ItemManager itemManager;
	
	@Inject
	private InfoBoxManager infoBoxManager;
	
	@Inject
	private Notifier notifier;
	
	@Inject
	private ClueJugglerConfig config;
	
	private ClueJugglerPlugin plugin;
	
	private final List<TimedClue> timedClues = new ArrayList<>();
	private InfoBox combinedTimer = null;
	
	public void setPlugin(ClueJugglerPlugin plugin)
	{
		this.plugin = plugin;
	}
	
	@Data
	public static class TimedClue
	{
		private final Instant startTime;
		private final int totalSeconds;
		private final int itemId;
		private final WorldPoint location;
		private final String listName; // null for good list, or custom list name
		private boolean notified = false;
		private InfoBox infoBox = null;
		
		public TimedClue(int itemId, WorldPoint location, int despawnTicks, String listName)
		{
			this.startTime = Instant.now();
			this.totalSeconds = (int) Duration.of(despawnTicks, RSTimeUnit.GAME_TICKS).getSeconds();
			this.itemId = itemId;
			this.location = location;
			this.listName = listName;
		}
		
		public Duration getTimeRemaining()
		{
			return Duration.between(Instant.now(), startTime.plusSeconds(totalSeconds));
		}
		
		public boolean isExpired()
		{
			return getTimeRemaining().isNegative();
		}
	}
	
	public void addTimer(TileItem item, WorldPoint location, String listName)
	{
		if (!config.showTimers())
		{
			return;
		}
		
		// Check if we already have a timer for this location
		for (TimedClue existing : timedClues)
		{
			if (existing.getLocation().equals(location) && existing.getItemId() == item.getId())
			{
				return; // Already tracking
			}
		}
		
		int despawnTicks = item.getDespawnTime() - client.getTickCount();
		if (despawnTicks <= 0)
		{
			return;
		}
		
		TimedClue timedClue = new TimedClue(item.getId(), location, despawnTicks, listName);
		timedClues.add(timedClue);
		
		log.debug("Added timer for clue at {} with {} seconds remaining", location, timedClue.totalSeconds);
		
		addInfoBox(timedClue);
	}
	
	public void removeTimer(WorldPoint location, int itemId)
	{
		TimedClue toRemove = null;
		for (TimedClue timedClue : timedClues)
		{
			if (timedClue.getLocation().equals(location) && timedClue.getItemId() == itemId)
			{
				toRemove = timedClue;
				break;
			}
		}
		
		if (toRemove != null)
		{
			removeClue(toRemove);
		}
	}
	
	public void removeTimerAtLocation(WorldPoint location)
	{
		List<TimedClue> toRemove = new ArrayList<>();
		for (TimedClue timedClue : timedClues)
		{
			if (timedClue.getLocation().equals(location))
			{
				toRemove.add(timedClue);
			}
		}
		
		for (TimedClue clue : toRemove)
		{
			removeClue(clue);
		}
	}
	
	private void removeClue(TimedClue timedClue)
	{
		timedClues.remove(timedClue);
		
		if (timedClue.infoBox != null)
		{
			infoBoxManager.removeInfoBox(timedClue.infoBox);
		}
		
		if (combinedTimer != null && timedClues.size() <= 1)
		{
			infoBoxManager.removeInfoBox(combinedTimer);
			combinedTimer = null;
			
			if (timedClues.size() == 1)
			{
				addInfoBox(timedClues.get(0));
			}
		}
		
		log.debug("Removed timer, {} remaining", timedClues.size());
	}
	
	private void addInfoBox(TimedClue timedClue)
	{
		if (config.combineTimers() && timedClues.size() > 1)
		{
			// Combined timer mode
			if (combinedTimer == null)
			{
				for (TimedClue clue : timedClues)
				{
					if (clue.infoBox != null)
					{
						infoBoxManager.removeInfoBox(clue.infoBox);
						clue.infoBox = null;
					}
				}
				
				BufferedImage image = itemManager.getImage(ItemID.CLUE_SCROLL_MASTER);
				combinedTimer = new InfoBox(image, plugin)
				{
					@Override
					public Color getTextColor()
					{
						for (TimedClue clue : timedClues)
						{
							if (clue.notified)
							{
								return Color.RED;
							}
						}
						return Color.WHITE;
					}
					
					@Override
					public String getText()
					{
						if (timedClues.isEmpty())
						{
							return "";
						}
						
						TimedClue lowestTime = null;
						for (TimedClue clue : timedClues)
						{
							if (lowestTime == null || clue.getTimeRemaining().compareTo(lowestTime.getTimeRemaining()) < 0)
							{
								lowestTime = clue;
							}
						}
						
						return formatDuration(lowestTime.getTimeRemaining());
					}
					
					@Override
					public String getTooltip()
					{
						return timedClues.size() + " clue timer" + (timedClues.size() > 1 ? "s" : "");
					}
				};
				
				infoBoxManager.addInfoBox(combinedTimer);
			}
		}
		else
		{
			// Individual timer mode
			BufferedImage image = itemManager.getImage(timedClue.getItemId());
			InfoBox timer = new InfoBox(image, plugin)
			{
				@Override
				public Color getTextColor()
				{
					return timedClue.notified ? Color.RED : Color.WHITE;
				}
				
				@Override
				public String getText()
				{
					return formatDuration(timedClue.getTimeRemaining());
				}
				
				@Override
				public String getTooltip()
				{
					String tooltip = "Clue despawn timer";
					if (timedClue.listName != null)
					{
						tooltip += " (" + timedClue.listName + ")";
					}
					return tooltip;
				}
			};
			
			timedClue.infoBox = timer;
			infoBoxManager.addInfoBox(timer);
		}
	}
	
	private String formatDuration(Duration duration)
	{
		long totalSeconds = duration.getSeconds();
		if (totalSeconds < 0)
		{
			return "0:00";
		}
		
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		
		if (minutes >= 10)
		{
			return String.format("%dm", minutes);
		}
		
		return String.format("%d:%02d", minutes, seconds);
	}
	
	public void onGameTick()
	{
		if (timedClues.isEmpty())
		{
			return;
		}
		
		List<TimedClue> toRemove = new ArrayList<>();
		
		for (TimedClue timedClue : timedClues)
		{
			// Check for expiry
			if (timedClue.isExpired())
			{
				toRemove.add(timedClue);
				continue;
			}
			
			// Check for notification
			if (config.timerNotificationSeconds() > 0 && !timedClue.notified)
			{
				if (timedClue.getTimeRemaining().getSeconds() <= config.timerNotificationSeconds())
				{
					notifier.notify("Your clue scroll is about to disappear!");
					timedClue.notified = true;
				}
			}
		}
		
		for (TimedClue clue : toRemove)
		{
			removeClue(clue);
		}
	}
	
	public void clearAllTimers()
	{
		for (TimedClue timedClue : new ArrayList<>(timedClues))
		{
			removeClue(timedClue);
		}
		
		if (combinedTimer != null)
		{
			infoBoxManager.removeInfoBox(combinedTimer);
			combinedTimer = null;
		}
	}
	
	public boolean hasTimerAt(WorldPoint location)
	{
		for (TimedClue timedClue : timedClues)
		{
			if (timedClue.getLocation().equals(location))
			{
				return true;
			}
		}
		return false;
	}
	
	public int getTimerCount()
	{
		return timedClues.size();
	}
}

