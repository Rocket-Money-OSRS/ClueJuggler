package com.cluejuggler.ui.panel;

import com.cluejuggler.model.ClueList;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

public class CurrentStepPanel extends JPanel
{
	private final JLabel clueTypeLabel;
	private final JTextArea currentStepArea;
	private final JButton goodButton;
	private final JButton badButton;
	private final JPanel buttonPanel;
	private final Map<String, JButton> customListButtons = new HashMap<>();
	
	private String currentClueText;
	private String currentClueIdentifier;
	private BiConsumer<String, String> onGoodClick;
	private BiConsumer<String, String> onBadClick;
	
	public CurrentStepPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(new Color(26, 26, 26));
		setBorder(new EmptyBorder(0, 0, 5, 0));
		setAlignmentX(0.0f);
		
		JLabel headerLabel = new JLabel("Current Step");
		headerLabel.setForeground(Color.WHITE);
		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 14));
		headerLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
		headerLabel.setAlignmentX(0.0f);
		add(headerLabel);
		
		clueTypeLabel = new JLabel("Clue Type: Unknown");
		clueTypeLabel.setForeground(new Color(180, 180, 180));
		clueTypeLabel.setFont(new Font(clueTypeLabel.getFont().getName(), Font.PLAIN, 12));
		clueTypeLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
		clueTypeLabel.setAlignmentX(0.0f);
		add(clueTypeLabel);
		
		currentStepArea = new JTextArea(4, 20);
		currentStepArea.setEditable(false);
		currentStepArea.setLineWrap(true);
		currentStepArea.setWrapStyleWord(true);
		currentStepArea.setBackground(new Color(20, 20, 20));
		currentStepArea.setForeground(Color.WHITE);
		currentStepArea.setBorder(new EmptyBorder(8, 8, 8, 8));
		currentStepArea.setText("Read a clue scroll to see the current step here.");
		currentStepArea.setAlignmentX(0.0f);
		currentStepArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		add(currentStepArea);
		
		buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
		buttonPanel.setBackground(new Color(26, 26, 26));
		buttonPanel.setAlignmentX(0.0f);
		buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		
		goodButton = new JButton("Good");
		goodButton.setBackground(new Color(0, 100, 0));
		goodButton.setForeground(Color.WHITE);
		goodButton.setFocusPainted(false);
		goodButton.setPreferredSize(new Dimension(60, 24));
		goodButton.addActionListener(e -> {
			if (onGoodClick != null && currentClueText != null)
			{
				onGoodClick.accept(currentClueText, currentClueIdentifier);
			}
		});
		buttonPanel.add(goodButton);
		
		badButton = new JButton("Bad");
		badButton.setBackground(new Color(100, 0, 0));
		badButton.setForeground(Color.WHITE);
		badButton.setFocusPainted(false);
		badButton.setPreferredSize(new Dimension(60, 24));
		badButton.addActionListener(e -> {
			if (onBadClick != null && currentClueText != null)
			{
				onBadClick.accept(currentClueText, currentClueIdentifier);
			}
		});
		buttonPanel.add(badButton);
		
		add(buttonPanel);
	}
	
	public void setCurrentStep(String difficulty, String clueText)
	{
		this.currentClueText = clueText;
		clueTypeLabel.setText("Clue Type: " + difficulty);
		currentStepArea.setText(clueText);
	}
	
	public void setCurrentClueIdentifier(String identifier)
	{
		this.currentClueIdentifier = identifier;
	}
	
	public void setOnGoodClick(BiConsumer<String, String> onGoodClick)
	{
		this.onGoodClick = onGoodClick;
	}
	
	public void setOnBadClick(BiConsumer<String, String> onBadClick)
	{
		this.onBadClick = onBadClick;
	}
	
	public void updateButtonStates(boolean isGood, boolean isBad, ClueList customList)
	{
		if (isGood)
		{
			goodButton.setBackground(new Color(0, 150, 0));
			badButton.setBackground(new Color(100, 0, 0));
		}
		else if (isBad)
		{
			goodButton.setBackground(new Color(0, 100, 0));
			badButton.setBackground(new Color(150, 0, 0));
		}
		else
		{
			goodButton.setBackground(new Color(0, 100, 0));
			badButton.setBackground(new Color(100, 0, 0));
		}
		
		for (Map.Entry<String, JButton> entry : customListButtons.entrySet())
		{
			JButton button = entry.getValue();
			if (customList != null && customList.getName().equals(entry.getKey()))
			{
				button.setBackground(customList.getTextColor().brighter());
			}
			else
			{
				button.setBackground(new Color(60, 60, 60));
			}
		}
	}
	
	public void refreshCustomListButtons(java.util.List<ClueList> customLists, java.util.function.BiConsumer<ClueList, String> onCustomListClick)
	{
		for (JButton button : customListButtons.values())
		{
			buttonPanel.remove(button);
		}
		customListButtons.clear();
		
		for (ClueList list : customLists)
		{
			JButton listButton = new JButton(list.getName());
			listButton.setBackground(new Color(60, 60, 60));
			listButton.setForeground(list.getTextColor());
			listButton.setFocusPainted(false);
			listButton.setPreferredSize(new Dimension(60, 24));
			listButton.addActionListener(e -> {
				if (onCustomListClick != null && currentClueText != null)
				{
					onCustomListClick.accept(list, currentClueIdentifier);
				}
			});
			customListButtons.put(list.getName(), listButton);
			buttonPanel.add(listButton);
		}
		
		buttonPanel.revalidate();
		buttonPanel.repaint();
	}
	
	public String getCurrentClueText()
	{
		return currentClueText;
	}
	
	public String getCurrentClueIdentifier()
	{
		return currentClueIdentifier;
	}
}

