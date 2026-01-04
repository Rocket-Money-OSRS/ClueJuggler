package com.cluejuggler;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClueList
{
	private String name;
	private int textColorRGB;
	private int tileColorRGB;
	private int overlayTextColorRGB;
	private final List<String> clues = new ArrayList<>();
	private final Map<String, String> clueIdentifiers = new HashMap<>();
	
	public ClueList()
	{
		// Default constructor for JSON deserialization
	}
	
	public ClueList(String name, Color textColor, Color tileColor, Color overlayTextColor)
	{
		this.name = name;
		this.textColorRGB = textColor != null ? textColor.getRGB() : Color.WHITE.getRGB();
		this.tileColorRGB = tileColor != null ? tileColor.getRGB() : new Color(255, 255, 255, 100).getRGB();
		this.overlayTextColorRGB = overlayTextColor != null ? overlayTextColor.getRGB() : Color.WHITE.getRGB();
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public Color getTextColor()
	{
		return new Color(textColorRGB);
	}
	
	public void setTextColor(Color textColor)
	{
		this.textColorRGB = textColor != null ? textColor.getRGB() : Color.WHITE.getRGB();
	}
	
	public Color getTileColor()
	{
		return new Color(tileColorRGB, true);
	}
	
	public void setTileColor(Color tileColor)
	{
		if (tileColor != null)
		{
			// Preserve alpha channel
			int r = tileColor.getRed();
			int g = tileColor.getGreen();
			int b = tileColor.getBlue();
			int a = tileColor.getAlpha();
			this.tileColorRGB = (a << 24) | (r << 16) | (g << 8) | b;
		}
		else
		{
			this.tileColorRGB = new Color(255, 255, 255, 100).getRGB();
		}
	}
	
	public Color getOverlayTextColor()
	{
		return new Color(overlayTextColorRGB);
	}
	
	public void setOverlayTextColor(Color overlayTextColor)
	{
		this.overlayTextColorRGB = overlayTextColor != null ? overlayTextColor.getRGB() : Color.WHITE.getRGB();
	}
	
	public List<String> getClues()
	{
		return clues;
	}
	
	public Map<String, String> getClueIdentifiers()
	{
		return clueIdentifiers;
	}
	
	// Getters/setters for JSON serialization
	public int getTextColorRGB()
	{
		return textColorRGB;
	}
	
	public void setTextColorRGB(int rgb)
	{
		this.textColorRGB = rgb;
	}
	
	public int getTileColorRGB()
	{
		return tileColorRGB;
	}
	
	public void setTileColorRGB(int rgb)
	{
		this.tileColorRGB = rgb;
	}
	
	public int getOverlayTextColorRGB()
	{
		return overlayTextColorRGB;
	}
	
	public void setOverlayTextColorRGB(int rgb)
	{
		this.overlayTextColorRGB = rgb;
	}
}

