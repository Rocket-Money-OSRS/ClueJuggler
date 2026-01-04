package com.cluejuggler.ui.view;

import com.cluejuggler.model.ClueList;
import com.cluejuggler.service.ClueListService;
import com.cluejuggler.ui.component.ClueTableComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.ImageUtil;

public class CustomListView extends JPanel
{
	private final ClueTableComponent tableComponent;
	private final ClueListService listService;
	private final JLabel titleLabel;
	private final JCheckBox deprioritizeCheckbox;
	private ClueList currentList;
	private Runnable onBackClick;
	
	public CustomListView(ClueListService listService)
	{
		this.listService = listService;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 5, 0));
		setBackground(new Color(26, 26, 26));
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.setBackground(new Color(26, 26, 26));
		
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
		
		titleLabel = new JLabel("Custom List");
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
		
		topPanel.add(headerPanel);
		
		JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		optionsPanel.setBackground(new Color(26, 26, 26));
		optionsPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		
		deprioritizeCheckbox = new JCheckBox("Deprioritize (treat as bad)");
		deprioritizeCheckbox.setBackground(new Color(26, 26, 26));
		deprioritizeCheckbox.setForeground(Color.WHITE);
		deprioritizeCheckbox.setFocusPainted(false);
		deprioritizeCheckbox.addActionListener(e -> {
			if (currentList != null)
			{
				currentList.setDeprioritize(deprioritizeCheckbox.isSelected());
				listService.saveCustomLists();
			}
		});
		optionsPanel.add(deprioritizeCheckbox);
		
		topPanel.add(optionsPanel);
		
		add(topPanel, BorderLayout.NORTH);
		
		tableComponent = new ClueTableComponent();
		tableComponent.setOnDeleteClue((text, identifier) -> {
			if (currentList != null)
			{
				currentList.getClueIdentifiers().remove(identifier);
				currentList.getClues().remove(text);
				listService.saveCustomLists();
			}
		});
		add(tableComponent, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttonPanel.setBackground(new Color(26, 26, 26));
		
		JButton clearButton = new JButton("Clear All");
		clearButton.setBackground(new Color(100, 0, 0));
		clearButton.setForeground(Color.WHITE);
		clearButton.setFocusPainted(false);
		clearButton.addActionListener(e -> clearList());
		buttonPanel.add(clearButton);
		
		JButton deleteListButton = new JButton("Delete List");
		deleteListButton.setBackground(new Color(100, 0, 0));
		deleteListButton.setForeground(Color.WHITE);
		deleteListButton.setFocusPainted(false);
		deleteListButton.addActionListener(e -> deleteList());
		buttonPanel.add(deleteListButton);
		
		add(buttonPanel, BorderLayout.SOUTH);
	}
	
	public void setOnBackClick(Runnable onBackClick)
	{
		this.onBackClick = onBackClick;
	}
	
	public void setList(ClueList list)
	{
		this.currentList = list;
		if (list != null)
		{
			titleLabel.setText(list.getName());
			titleLabel.setForeground(list.getTextColor());
			deprioritizeCheckbox.setSelected(list.isDeprioritize());
			refresh();
		}
	}
	
	public void refresh()
	{
		tableComponent.clear();
		if (currentList != null)
		{
			for (java.util.Map.Entry<String, String> entry : currentList.getClueIdentifiers().entrySet())
			{
				tableComponent.addRow(entry.getValue(), entry.getKey());
			}
		}
	}
	
	private void clearList()
	{
		if (currentList == null)
		{
			return;
		}
		
		int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all clues from " + currentList.getName() + "?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION)
		{
			currentList.getClueIdentifiers().clear();
			currentList.getClues().clear();
			listService.saveCustomLists();
			tableComponent.clear();
		}
	}
	
	private void deleteList()
	{
		if (currentList == null)
		{
			return;
		}
		
		int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete the list '" + currentList.getName() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION)
		{
			listService.deleteCustomList(currentList);
			if (onBackClick != null)
			{
				onBackClick.run();
			}
		}
	}
	
	public ClueList getCurrentList()
	{
		return currentList;
	}
}

