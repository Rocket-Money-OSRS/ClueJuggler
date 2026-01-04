package com.cluejuggler.service;

import com.cluejuggler.model.ClueList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
public class ClueListService
{
	private final ConfigManager configManager;
	private final Gson gson;
	
	@Getter
	private final Map<String, String> goodClueIdentifiers = new ConcurrentHashMap<>();
	@Getter
	private final Map<String, String> badClueIdentifiers = new ConcurrentHashMap<>();
	@Getter
	private final List<ClueList> customLists = new ArrayList<>();
	
	private boolean listsLoaded = false;
	private boolean customListsLoaded = false;
	
	@Inject
	public ClueListService(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}
	
	public void loadLists()
	{
		if (configManager == null || listsLoaded)
		{
			return;
		}
		
		String goodStepsData = configManager.getConfiguration("cluejuggler", "goodSteps");
		String badStepsData = configManager.getConfiguration("cluejuggler", "badSteps");
		
		goodClueIdentifiers.clear();
		badClueIdentifiers.clear();
		
		if (goodStepsData != null && !goodStepsData.isEmpty())
		{
			parseListData(goodStepsData, goodClueIdentifiers);
		}
		
		if (badStepsData != null && !badStepsData.isEmpty())
		{
			parseListData(badStepsData, badClueIdentifiers);
		}
		
		listsLoaded = true;
	}
	
	private void parseListData(String data, Map<String, String> targetMap)
	{
		String[] items = data.split("\\|");
		for (String item : items)
		{
			item = item.trim();
			if (!item.isEmpty())
			{
				String[] parts = item.split("::", 2);
				String text;
				String identifier;
				if (parts.length == 2)
				{
					text = parts[0].trim();
					identifier = parts[1].trim();
				}
				else
				{
					text = item;
					identifier = "text:" + text.hashCode();
				}
				if (!text.isEmpty() && !identifier.isEmpty())
				{
					targetMap.put(identifier, text);
				}
			}
		}
	}
	
	public void saveLists()
	{
		if (configManager == null)
		{
			return;
		}
		
		String goodData = buildListData(goodClueIdentifiers);
		String badData = buildListData(badClueIdentifiers);
		
		configManager.setConfiguration("cluejuggler", "goodSteps", goodData);
		configManager.setConfiguration("cluejuggler", "badSteps", badData);
	}
	
	private String buildListData(Map<String, String> sourceMap)
	{
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, String> entry : sourceMap.entrySet())
		{
			if (!first)
			{
				builder.append("|");
			}
			first = false;
			builder.append(entry.getValue()).append("::").append(entry.getKey());
		}
		return builder.toString();
	}
	
	public void loadCustomLists()
	{
		if (configManager == null)
		{
			return;
		}
		
		if (customListsLoaded && !customLists.isEmpty())
		{
			return;
		}
		
		if (customListsLoaded && customLists.isEmpty())
		{
			String customListsJson = configManager.getConfiguration("cluejuggler", "customLists");
			if (customListsJson != null && !customListsJson.isEmpty() && !customListsJson.equals("[]"))
			{
				customListsLoaded = false;
			}
			else
			{
				return;
			}
		}
		
		if (customListsLoaded)
		{
			return;
		}
		
		String customListsJson = configManager.getConfiguration("cluejuggler", "customLists");
		if (customListsJson == null || customListsJson.isEmpty())
		{
			customListsJson = "[]";
		}
		
		try
		{
			ClueList[] loaded = gson.fromJson(customListsJson, ClueList[].class);
			if (loaded != null)
			{
				customLists.clear();
				customLists.addAll(Arrays.asList(loaded));
			}
			customListsLoaded = true;
		}
		catch (Exception e)
		{
			log.warn("Failed to load custom lists", e);
			customLists.clear();
			customListsLoaded = true;
		}
	}
	
	public void saveCustomLists()
	{
		if (configManager == null)
		{
			return;
		}
		
		try
		{
			String json = gson.toJson(customLists);
			configManager.setConfiguration("cluejuggler", "customLists", json);
			customListsLoaded = true;
		}
		catch (Exception e)
		{
			log.warn("Failed to save custom lists", e);
		}
	}
	
	public void ensureListsLoaded()
	{
		if (!listsLoaded)
		{
			loadLists();
		}
		if (!customListsLoaded)
		{
			loadCustomLists();
		}
	}
	
	public void addGoodClue(String identifier, String text)
	{
		ensureListsLoaded();
		removeFromBad(identifier);
		removeFromCustomLists(identifier);
		goodClueIdentifiers.put(identifier, text);
		saveLists();
	}
	
	public void addBadClue(String identifier, String text)
	{
		ensureListsLoaded();
		removeFromGood(identifier);
		removeFromCustomLists(identifier);
		badClueIdentifiers.put(identifier, text);
		saveLists();
	}
	
	public void addToCustomList(ClueList list, String identifier, String text)
	{
		ensureListsLoaded();
		removeFromGood(identifier);
		removeFromBad(identifier);
		removeFromCustomLists(identifier);
		list.getClueIdentifiers().put(identifier, text);
		list.getClues().add(text);
		saveCustomLists();
	}
	
	public void removeFromGood(String identifier)
	{
		goodClueIdentifiers.remove(identifier);
	}
	
	public void removeFromBad(String identifier)
	{
		badClueIdentifiers.remove(identifier);
	}
	
	public void removeFromCustomLists(String identifier)
	{
		for (ClueList list : customLists)
		{
			String text = list.getClueIdentifiers().remove(identifier);
			if (text != null)
			{
				list.getClues().remove(text);
			}
		}
	}
	
	public boolean isGoodClue(String clueText)
	{
		ensureListsLoaded();
		return goodClueIdentifiers.containsValue(clueText);
	}
	
	public boolean isBadClue(String clueText)
	{
		ensureListsLoaded();
		return badClueIdentifiers.containsValue(clueText);
	}
	
	public boolean isGoodClueByIdentifier(String identifier)
	{
		ensureListsLoaded();
		return goodClueIdentifiers.containsKey(identifier);
	}
	
	public boolean isBadClueByIdentifier(String identifier)
	{
		ensureListsLoaded();
		return badClueIdentifiers.containsKey(identifier);
	}
	
	public ClueList getCustomListForClue(String identifier)
	{
		ensureListsLoaded();
		for (ClueList list : customLists)
		{
			if (list.getClueIdentifiers().containsKey(identifier))
			{
				return list;
			}
		}
		return null;
	}
	
	public ClueList getCustomListForClueByText(String clueText)
	{
		ensureListsLoaded();
		for (ClueList list : customLists)
		{
			if (list.getClues().contains(clueText))
			{
				return list;
			}
		}
		return null;
	}
	
	public ClueList findCustomListByName(String name)
	{
		ensureListsLoaded();
		for (ClueList list : customLists)
		{
			if (list.getName().equals(name))
			{
				return list;
			}
		}
		return null;
	}
	
	public void createCustomList(String name, java.awt.Color textColor, java.awt.Color tileColor, java.awt.Color overlayTextColor)
	{
		ensureListsLoaded();
		ClueList newList = new ClueList(name, textColor, tileColor, overlayTextColor);
		customLists.add(newList);
		saveCustomLists();
	}
	
	public void deleteCustomList(ClueList list)
	{
		customLists.remove(list);
		saveCustomLists();
	}
	
	public boolean hasAnyCluesInLists()
	{
		ensureListsLoaded();
		if (!goodClueIdentifiers.isEmpty() || !badClueIdentifiers.isEmpty())
		{
			return true;
		}
		for (ClueList list : customLists)
		{
			if (!list.getClueIdentifiers().isEmpty())
			{
				return true;
			}
		}
		return false;
	}
	
	public String exportListsToJson()
	{
		ensureListsLoaded();
		
		java.util.Map<String, Object> exportData = new java.util.HashMap<>();
		exportData.put("goodClues", goodClueIdentifiers);
		exportData.put("badClues", badClueIdentifiers);
		exportData.put("customLists", customLists);
		
		return gson.toJson(exportData);
	}
	
	public void importListsFromJson(String json)
	{
		try
		{
			JsonObject importData = gson.fromJson(json, JsonObject.class);
			
			if (importData.has("goodClues"))
			{
				JsonObject goodData = importData.getAsJsonObject("goodClues");
				goodClueIdentifiers.clear();
				for (String key : goodData.keySet())
				{
					goodClueIdentifiers.put(key, goodData.get(key).getAsString());
				}
			}
			
			if (importData.has("badClues"))
			{
				JsonObject badData = importData.getAsJsonObject("badClues");
				badClueIdentifiers.clear();
				for (String key : badData.keySet())
				{
					badClueIdentifiers.put(key, badData.get(key).getAsString());
				}
			}
			
			if (importData.has("customLists"))
			{
				String customListsJson = importData.get("customLists").toString();
				ClueList[] imported = gson.fromJson(customListsJson, ClueList[].class);
				if (imported != null)
				{
					customLists.clear();
					customLists.addAll(Arrays.asList(imported));
				}
			}
			
			saveLists();
			saveCustomLists();
		}
		catch (Exception e)
		{
			log.warn("Failed to import lists from JSON", e);
		}
	}
	
	public void exportToFile(File file)
	{
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file)))
		{
			writer.write(exportListsToJson());
		}
		catch (Exception e)
		{
			log.warn("Failed to export lists to file", e);
		}
	}
	
	public void importFromFile(File file)
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null)
			{
				sb.append(line);
			}
			importListsFromJson(sb.toString());
		}
		catch (Exception e)
		{
			log.warn("Failed to import lists from file", e);
		}
	}
	
	public void clearGoodSteps()
	{
		goodClueIdentifiers.clear();
		saveLists();
	}
	
	public void clearBadSteps()
	{
		badClueIdentifiers.clear();
		saveLists();
	}
	
	public void forceReload()
	{
		listsLoaded = false;
		customListsLoaded = false;
		loadLists();
		loadCustomLists();
	}
}

