package com.cluejuggler.ui.panel;

import com.cluejuggler.model.ClueList;
import com.cluejuggler.ui.component.ListCardComponent;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class ListsSectionPanel extends JPanel
{
	private final List<ListCardComponent> customListCards = new ArrayList<>();
	private final List<java.awt.Component> customListSpacers = new ArrayList<>();
	private JButton addListButton;
	private java.awt.Component buttonSpacer;
	private Runnable onGoodListClick;
	private Runnable onBadListClick;
	private Runnable onAddListClick;
	private java.util.function.Consumer<ClueList> onCustomListClick;
	private java.util.function.Consumer<ClueList> onDeleteCustomList;
	
	public ListsSectionPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(new Color(26, 26, 26));
		setAlignmentX(0.0f);
		
		JLabel headerLabel = new JLabel("Lists");
		headerLabel.setForeground(Color.WHITE);
		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 14));
		headerLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
		headerLabel.setAlignmentX(0.0f);
		add(headerLabel);
		
		ListCardComponent goodCard = new ListCardComponent("Good Steps", new Color(0, 150, 0), false);
		goodCard.setAlignmentX(0.0f);
		goodCard.setOnClick(() -> {
			if (onGoodListClick != null)
			{
				onGoodListClick.run();
			}
		});
		add(goodCard);
		
		add(Box.createVerticalStrut(5));
		
		ListCardComponent badCard = new ListCardComponent("Bad Steps", new Color(150, 0, 0), false);
		badCard.setAlignmentX(0.0f);
		badCard.setOnClick(() -> {
			if (onBadListClick != null)
			{
				onBadListClick.run();
			}
		});
		add(badCard);
		
		buttonSpacer = Box.createVerticalStrut(10);
		add(buttonSpacer);
		
		addListButton = new JButton("+ Add List");
		addListButton.setBackground(new Color(60, 60, 60));
		addListButton.setForeground(Color.WHITE);
		addListButton.setFocusPainted(false);
		addListButton.setAlignmentX(0.0f);
		addListButton.addActionListener(e -> {
			if (onAddListClick != null)
			{
				onAddListClick.run();
			}
		});
		add(addListButton);
	}
	
	public void setOnGoodListClick(Runnable onGoodListClick)
	{
		this.onGoodListClick = onGoodListClick;
	}
	
	public void setOnBadListClick(Runnable onBadListClick)
	{
		this.onBadListClick = onBadListClick;
	}
	
	public void setOnAddListClick(Runnable onAddListClick)
	{
		this.onAddListClick = onAddListClick;
	}
	
	public void setOnCustomListClick(java.util.function.Consumer<ClueList> onCustomListClick)
	{
		this.onCustomListClick = onCustomListClick;
	}
	
	public void setOnDeleteCustomList(java.util.function.Consumer<ClueList> onDeleteCustomList)
	{
		this.onDeleteCustomList = onDeleteCustomList;
	}
	
	public void refreshCustomLists(List<ClueList> customLists)
	{
		for (ListCardComponent card : customListCards)
		{
			remove(card);
		}
		customListCards.clear();
		
		for (java.awt.Component spacer : customListSpacers)
		{
			remove(spacer);
		}
		customListSpacers.clear();
		
		remove(buttonSpacer);
		remove(addListButton);
		
		for (ClueList list : customLists)
		{
			java.awt.Component spacer = Box.createVerticalStrut(5);
			customListSpacers.add(spacer);
			add(spacer);
			
			ListCardComponent card = new ListCardComponent(list.getName(), list.getTextColor(), true);
			card.setAlignmentX(0.0f);
			card.setOnClick(() -> {
				if (onCustomListClick != null)
				{
					onCustomListClick.accept(list);
				}
			});
			card.setOnDelete(() -> {
				if (onDeleteCustomList != null)
				{
					onDeleteCustomList.accept(list);
				}
			});
			customListCards.add(card);
			add(card);
		}
		
		buttonSpacer = Box.createVerticalStrut(10);
		add(buttonSpacer);
		add(addListButton);
		
		revalidate();
		repaint();
	}
}

