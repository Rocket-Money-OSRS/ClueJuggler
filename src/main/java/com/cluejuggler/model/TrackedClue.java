package com.cluejuggler.model;

import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;

public class TrackedClue
{
	private final int itemId;
	private final String clueText;
	private final String identifier;
	private final ClueScroll clueScroll;
	private ClueStatus status;
	private ClueList customList;
	
	public TrackedClue(int itemId, String clueText, String identifier, ClueScroll clueScroll)
	{
		this.itemId = itemId;
		this.clueText = clueText;
		this.identifier = identifier;
		this.clueScroll = clueScroll;
		this.status = ClueStatus.UNSURE;
	}
	
	public int getItemId()
	{
		return itemId;
	}
	
	public String getClueText()
	{
		return clueText;
	}
	
	public String getIdentifier()
	{
		return identifier;
	}
	
	public ClueScroll getClueScroll()
	{
		return clueScroll;
	}
	
	public ClueStatus getStatus()
	{
		return status;
	}
	
	public void setStatus(ClueStatus status)
	{
		this.status = status;
	}
	
	public ClueList getCustomList()
	{
		return customList;
	}
	
	public void setCustomList(ClueList customList)
	{
		this.customList = customList;
		if (customList != null)
		{
			this.status = ClueStatus.CUSTOM;
		}
	}
	
	public boolean isBeginnerOrMaster()
	{
		return identifier != null && (identifier.startsWith("beginner:") || identifier.startsWith("master:"));
	}
}

