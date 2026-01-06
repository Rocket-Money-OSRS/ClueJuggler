package com.cluejuggler.service;

import com.cluejuggler.ClueJugglerConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.util.RSTimeUnit;

@Slf4j
@Singleton
public class ClueTimerService
{
	@Inject
	private Client client;
	
	@Inject
	private Notifier notifier;
	
	@Inject
	private ClueJugglerConfig config;
	
	private final List<TimedClue> timedClues = new ArrayList<>();
	
	@Data
	public static class TimedClue
	{
		private final Instant startTime;
		private final int totalSeconds;
		private final int itemId;
		private final WorldPoint location;
		private final String listName; // null for good list, or custom list name
		@Getter
		private boolean notified = false;
		
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
		
		public void markNotified()
		{
			this.notified = true;
		}
	}
	
	public List<TimedClue> getTimedClues()
	{
		return Collections.unmodifiableList(timedClues);
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
		
		log.debug("Added timer for clue at {} with {} seconds remaining (list: {})", 
			location, timedClue.totalSeconds, listName);
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
			timedClues.remove(toRemove);
			log.debug("Removed timer at {}, {} remaining", location, timedClues.size());
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
		
		timedClues.removeAll(toRemove);
		
		if (!toRemove.isEmpty())
		{
			log.debug("Removed {} timer(s) at {}, {} remaining", toRemove.size(), location, timedClues.size());
		}
	}
	
	/**
	 * Remove only ONE timer at the given location (for stacked clues).
	 * Prefers matching itemId if provided.
	 */
	public void removeOneTimerAtLocation(WorldPoint location, int itemId)
	{
		TimedClue toRemove = null;
		
		// First try to find one matching the item ID
		for (TimedClue timedClue : timedClues)
		{
			if (timedClue.getLocation().equals(location))
			{
				if (timedClue.getItemId() == itemId)
				{
					toRemove = timedClue;
					break;
				}
				// Keep as fallback
				if (toRemove == null)
				{
					toRemove = timedClue;
				}
			}
		}
		
		if (toRemove != null)
		{
			timedClues.remove(toRemove);
			log.debug("Removed one timer at {} (itemId {}), {} remaining", location, itemId, timedClues.size());
		}
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
			if (config.timerNotificationSeconds() > 0 && !timedClue.isNotified())
			{
				if (timedClue.getTimeRemaining().getSeconds() <= config.timerNotificationSeconds())
				{
					String listInfo = timedClue.getListName() != null ? " (" + timedClue.getListName() + ")" : "";
					notifier.notify("Your clue scroll" + listInfo + " is about to disappear!");
					timedClue.markNotified();
				}
			}
		}
		
		timedClues.removeAll(toRemove);
		
		if (!toRemove.isEmpty())
		{
			log.debug("Removed {} expired timer(s), {} remaining", toRemove.size(), timedClues.size());
		}
	}
	
	public void clearAllTimers()
	{
		timedClues.clear();
		log.debug("Cleared all timers");
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
