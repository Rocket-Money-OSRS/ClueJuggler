package com.cluejuggler.ui.view;

import com.cluejuggler.model.ClueList;
import com.cluejuggler.service.ClueListService;
import com.cluejuggler.ui.component.ColorPickerRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ImageUtil;

public class SettingsView extends JPanel
{
	private final ClueListService listService;
	private final ConfigManager configManager;
	private final JPanel contentPanel;
	private Runnable onBackClick;
	
	public SettingsView(ClueListService listService, ConfigManager configManager)
	{
		this.listService = listService;
		this.configManager = configManager;
		
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 5, 0));
		setBackground(new Color(26, 26, 26));
		
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		headerPanel.setBackground(new Color(26, 26, 26));
		
		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.setBackground(new Color(26, 26, 26));
		
		try
		{
			BufferedImage iconImage = ImageUtil.loadImageResource(getClass(), "/cluejuggler.png");
			if (iconImage != null)
			{
				ImageIcon icon = new ImageIcon(iconImage.getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH));
				JLabel iconLabel = new JLabel(icon);
				iconLabel.setBorder(new EmptyBorder(0, 0, 0, 10));
				leftPanel.add(iconLabel, BorderLayout.WEST);
			}
		}
		catch (Exception e)
		{
		}
		
		JLabel titleLabel = new JLabel("Settings");
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 18));
		titleLabel.setForeground(Color.WHITE);
		leftPanel.add(titleLabel, BorderLayout.CENTER);
		
		headerPanel.add(leftPanel, BorderLayout.CENTER);
		
		JButton backButton = new JButton("← Back");
		backButton.setBackground(new Color(60, 60, 60));
		backButton.setForeground(Color.WHITE);
		backButton.setFocusPainted(false);
		backButton.addActionListener(e -> {
			if (onBackClick != null)
			{
				onBackClick.run();
			}
		});
		headerPanel.add(backButton, BorderLayout.EAST);
		
		add(headerPanel, BorderLayout.NORTH);
		
		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(new Color(26, 26, 26));
		
		JScrollPane scrollPane = new JScrollPane(contentPanel);
		scrollPane.setBackground(new Color(26, 26, 26));
		scrollPane.getViewport().setBackground(new Color(26, 26, 26));
		scrollPane.setBorder(null);
		add(scrollPane, BorderLayout.CENTER);
	}
	
	public void setOnBackClick(Runnable onBackClick)
	{
		this.onBackClick = onBackClick;
	}
	
	public void refresh()
	{
		contentPanel.removeAll();
		
		JLabel generalLabel = new JLabel("General");
		generalLabel.setFont(new Font(generalLabel.getFont().getName(), Font.BOLD, 14));
		generalLabel.setForeground(Color.WHITE);
		generalLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
		contentPanel.add(generalLabel);
		
		JCheckBox showIndicatorCheck = new JCheckBox("Show Clue Indicator");
		showIndicatorCheck.setBackground(new Color(26, 26, 26));
		showIndicatorCheck.setForeground(Color.WHITE);
		showIndicatorCheck.setSelected(getBooleanConfig("showIndicator", true));
		showIndicatorCheck.addActionListener(e -> 
			configManager.setConfiguration("cluejuggler", "showIndicator", showIndicatorCheck.isSelected()));
		contentPanel.add(showIndicatorCheck);
		
		JCheckBox deprioritizeCheck = new JCheckBox("Deprioritize Bad Clues");
		deprioritizeCheck.setBackground(new Color(26, 26, 26));
		deprioritizeCheck.setForeground(Color.WHITE);
		deprioritizeCheck.setSelected(getBooleanConfig("deprioritizeBadClues", true));
		deprioritizeCheck.addActionListener(e -> 
			configManager.setConfiguration("cluejuggler", "deprioritizeBadClues", deprioritizeCheck.isSelected()));
		contentPanel.add(deprioritizeCheck);
		
		contentPanel.add(Box.createVerticalStrut(20));
		
		JLabel highlighterLabel = new JLabel("Clue Highlighter");
		highlighterLabel.setFont(new Font(highlighterLabel.getFont().getName(), Font.BOLD, 14));
		highlighterLabel.setForeground(Color.WHITE);
		highlighterLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
		contentPanel.add(highlighterLabel);
		
		JCheckBox enableHighlightsCheck = new JCheckBox("Enable Clue Highlights");
		enableHighlightsCheck.setBackground(new Color(26, 26, 26));
		enableHighlightsCheck.setForeground(Color.WHITE);
		enableHighlightsCheck.setSelected(getBooleanConfig("clueHighlights", true));
		enableHighlightsCheck.addActionListener(e -> 
			configManager.setConfiguration("cluejuggler", "clueHighlights", enableHighlightsCheck.isSelected()));
		contentPanel.add(enableHighlightsCheck);
		
		contentPanel.add(Box.createVerticalStrut(10));
		
		ColorPickerRow goodTileColor = new ColorPickerRow("Good Tile Color", "Color for good clue tiles", 
			getColorConfig("goodClueTileColor", new Color(0, 255, 0, 100)),
			color -> setColorConfig("goodClueTileColor", color));
		contentPanel.add(goodTileColor);
		
		ColorPickerRow badTileColor = new ColorPickerRow("Bad Tile Color", "Color for bad clue tiles", 
			getColorConfig("badClueTileColor", new Color(255, 0, 0, 100)),
			color -> setColorConfig("badClueTileColor", color));
		contentPanel.add(badTileColor);
		
		ColorPickerRow unsureTileColor = new ColorPickerRow("Unsure Tile Color", "Color for unsure clue tiles", 
			getColorConfig("unsureClueTileColor", new Color(255, 255, 0, 100)),
			color -> setColorConfig("unsureClueTileColor", color));
		contentPanel.add(unsureTileColor);
		
		contentPanel.add(Box.createVerticalStrut(10));
		
		ColorPickerRow goodTextColor = new ColorPickerRow("Good Text Color", "Color for good clue text", 
			getColorConfig("goodClueTextColor", Color.GREEN),
			color -> setColorConfig("goodClueTextColor", color));
		contentPanel.add(goodTextColor);
		
		ColorPickerRow badTextColor = new ColorPickerRow("Bad Text Color", "Color for bad clue text", 
			getColorConfig("badClueTextColor", Color.RED),
			color -> setColorConfig("badClueTextColor", color));
		contentPanel.add(badTextColor);
		
		ColorPickerRow unsureTextColor = new ColorPickerRow("Unsure Text Color", "Color for unsure clue text", 
			getColorConfig("unsureClueTextColor", Color.YELLOW),
			color -> setColorConfig("unsureClueTextColor", color));
		contentPanel.add(unsureTextColor);
		
		List<ClueList> customLists = listService.getCustomLists();
		if (!customLists.isEmpty())
		{
			contentPanel.add(Box.createVerticalStrut(20));
			
			JLabel customListsLabel = new JLabel("Custom Lists");
			customListsLabel.setFont(new Font(customListsLabel.getFont().getName(), Font.BOLD, 14));
			customListsLabel.setForeground(Color.WHITE);
			customListsLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
			contentPanel.add(customListsLabel);
			
			for (ClueList list : customLists)
			{
				JLabel listNameLabel = new JLabel(list.getName());
				listNameLabel.setForeground(list.getTextColor());
				listNameLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
				contentPanel.add(listNameLabel);
				
				ColorPickerRow listTileColor = new ColorPickerRow("Tile Color", "Tile color for " + list.getName(), 
					list.getTileColor(),
					color -> {
						list.setTileColor(color);
						listService.saveCustomLists();
					});
				contentPanel.add(listTileColor);
				
				ColorPickerRow listTextColor = new ColorPickerRow("Text Color", "Text color for " + list.getName(), 
					list.getTextColor(),
					color -> {
						list.setTextColor(color);
						list.setOverlayTextColor(color);
						listService.saveCustomLists();
					});
				contentPanel.add(listTextColor);
			}
		}
		
		contentPanel.revalidate();
		contentPanel.repaint();
	}
	
	private boolean getBooleanConfig(String key, boolean defaultValue)
	{
		String value = configManager.getConfiguration("cluejuggler", key);
		if (value == null)
		{
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}
	
	private Color getColorConfig(String key, Color defaultValue)
	{
		String value = configManager.getConfiguration("cluejuggler", key);
		if (value == null)
		{
			return defaultValue;
		}
		try
		{
			return Color.decode(value);
		}
		catch (Exception e)
		{
			return defaultValue;
		}
	}
	
	private void setColorConfig(String key, Color color)
	{
		String value = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
		configManager.setConfiguration("cluejuggler", key, value);
	}
}

