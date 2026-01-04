package com.cluejuggler.service;

import com.cluejuggler.model.ClueList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;

@Slf4j
@Singleton
public class ClueTrackingService
{
	private final Client client;
	private final ClientThread clientThread;
	
	@Getter
	private final Map<Integer, ClueScroll> trackedClues = new ConcurrentHashMap<>();
	
	@Getter
	private final Map<String, ClueScroll> trackedCluesByText = new ConcurrentHashMap<>();
	
	private final Map<Object, Object> clueStatusCache = new ConcurrentHashMap<>();
	private final Map<Integer, String> itemIdToIdentifier = new ConcurrentHashMap<>();
	private final Map<WorldPoint, DroppedClueInfo> droppedClues = new ConcurrentHashMap<>();
	
	@Getter @Setter
	private String currentlyReadClueText = null;
	@Getter @Setter
	private String currentlyReadClueIdentifier = null;
	
	public static class DroppedClueInfo
	{
		public final String clueText;
		public final Boolean isGood; // true=good, false=bad, null=unsure or custom
		public final ClueList customList; // non-null if in custom list
		
		public DroppedClueInfo(String clueText, Boolean isGood, ClueList customList)
		{
			this.clueText = clueText;
			this.isGood = isGood;
			this.customList = customList;
		}
	}
	
	private ClueScroll lastProcessedClue = null;
	private boolean lastClueWasTracked = false;
	
	@Inject
	public ClueTrackingService(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}
	
	public void trackClue(int itemId, ClueScroll clue)
	{
		if (itemId > 0 && clue != null)
		{
			trackedClues.put(itemId, clue);
		}
	}
	
	public void trackClueByText(String clueText, ClueScroll clue)
	{
		if (clueText != null && !clueText.isEmpty() && clue != null)
		{
			trackedCluesByText.put(clueText, clue);
		}
	}
	
	public boolean isTrackedByText(String clueText)
	{
		return clueText != null && trackedCluesByText.containsKey(clueText);
	}
	
	public void clearTrackedClues()
	{
		trackedClues.clear();
		trackedCluesByText.clear();
		droppedClues.clear();
	}
	
	public ClueScroll getLastProcessedClue()
	{
		return lastProcessedClue;
	}
	
	public void setLastProcessedClue(ClueScroll clue)
	{
		this.lastProcessedClue = clue;
	}
	
	public boolean wasLastClueTracked()
	{
		return lastClueWasTracked;
	}
	
	public void setLastClueWasTracked(boolean tracked)
	{
		this.lastClueWasTracked = tracked;
	}
	
	public void markDroppedClue(WorldPoint position, String clueText, Boolean isGood, ClueList customList)
	{
		if (position != null && clueText != null)
		{
			droppedClues.put(position, new DroppedClueInfo(clueText, isGood, customList));
			log.debug("Marked dropped clue at {}: text={}, isGood={}, customList={}", 
				position, clueText, isGood, customList != null ? customList.getName() : "null");
		}
	}
	
	public DroppedClueInfo getDroppedClueInfo(WorldPoint position)
	{
		if (position == null)
		{
			return null;
		}
		return droppedClues.get(position);
	}
	
	public void clearDroppedClue(WorldPoint position)
	{
		if (position != null)
		{
			droppedClues.remove(position);
		}
	}
	
	public Map<WorldPoint, DroppedClueInfo> getDroppedClues()
	{
		return droppedClues;
	}
	
	public void clearAllDroppedClues()
	{
		droppedClues.clear();
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
	
}

