package com.cluejuggler.ui.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.ImageUtil;

public class ListCardComponent extends JPanel
{
	private final JLabel titleLabel;
	private Runnable onClick;
	private Runnable onDelete;
	private boolean isCustomList;
	
	public ListCardComponent(String title, Color titleColor, boolean isCustomList)
	{
		this.isCustomList = isCustomList;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(new Color(40, 40, 40));
		setCursor(new Cursor(Cursor.HAND_CURSOR));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		setPreferredSize(new Dimension(190, 40));
		
		titleLabel = new JLabel(title);
		titleLabel.setForeground(titleColor);
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 13));
		add(titleLabel, BorderLayout.WEST);
		
		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		rightPanel.setOpaque(false);
		
		if (isCustomList)
		{
			JButton deleteButton = new JButton();
			try
			{
				BufferedImage deleteIcon = ImageUtil.loadImageResource(getClass(), "/delete.png");
				if (deleteIcon != null)
				{
					deleteButton.setIcon(new ImageIcon(deleteIcon));
				}
				else
				{
					deleteButton.setText("✕");
					deleteButton.setFont(new Font(deleteButton.getFont().getName(), Font.BOLD, 10));
				}
			}
			catch (Exception ex)
			{
				deleteButton.setText("✕");
				deleteButton.setFont(new Font(deleteButton.getFont().getName(), Font.BOLD, 10));
			}
			deleteButton.setForeground(new Color(180, 80, 80));
			deleteButton.setBackground(new Color(60, 60, 60));
			deleteButton.setBorderPainted(false);
			deleteButton.setFocusPainted(false);
			deleteButton.setContentAreaFilled(false);
			deleteButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
			deleteButton.addActionListener(e -> {
				if (onDelete != null)
				{
					onDelete.run();
				}
			});
			rightPanel.add(deleteButton);
		}
		
		JLabel arrow = new JLabel();
		try
		{
			BufferedImage arrowIcon = ImageUtil.loadImageResource(getClass(), "/open_arrow.png");
			if (arrowIcon != null)
			{
				arrow.setIcon(new ImageIcon(arrowIcon));
			}
			else
			{
				arrow.setText("▶");
				arrow.setForeground(new Color(120, 120, 120));
			}
		}
		catch (Exception ex)
		{
			arrow.setText("▶");
			arrow.setForeground(new Color(120, 120, 120));
		}
		rightPanel.add(arrow);
		
		add(rightPanel, BorderLayout.EAST);
		
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onClick != null)
				{
					onClick.run();
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(new Color(50, 50, 50));
			}
			
			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(new Color(40, 40, 40));
			}
		});
	}
	
	public void setOnClick(Runnable onClick)
	{
		this.onClick = onClick;
	}
	
	public void setOnDelete(Runnable onDelete)
	{
		this.onDelete = onDelete;
	}
	
	public void setTitle(String title)
	{
		titleLabel.setText(title);
	}
	
	public void setTitleColor(Color color)
	{
		titleLabel.setForeground(color);
	}
	
	public String getTitle()
	{
		return titleLabel.getText();
	}
	
	public boolean isCustomList()
	{
		return isCustomList;
	}
}

