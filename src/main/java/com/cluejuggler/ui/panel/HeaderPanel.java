package com.cluejuggler.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.ImageUtil;

public class HeaderPanel extends JPanel
{
	private final JLabel headerLabel;
	private final JButton settingsButton;
	private Runnable onSettingsClick;
	
	public HeaderPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 8, 0));
		setBackground(new Color(26, 26, 26));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		setPreferredSize(new Dimension(190, 48));
		
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
		
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(new Color(26, 26, 26));
		
		headerLabel = new JLabel("Clue Juggler");
		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 18));
		headerLabel.setForeground(Color.WHITE);
		titlePanel.add(headerLabel, BorderLayout.NORTH);
		
		JLabel creditLabel = new JLabel("by: [RSN] Rocket Money");
		creditLabel.setFont(new Font(creditLabel.getFont().getName(), Font.PLAIN, 11));
		creditLabel.setForeground(new Color(150, 150, 150));
		creditLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
		titlePanel.add(creditLabel, BorderLayout.CENTER);
		
		leftPanel.add(titlePanel, BorderLayout.CENTER);
		add(leftPanel, BorderLayout.CENTER);
		
		settingsButton = new JButton();
		try
		{
			BufferedImage settingsIcon = ImageUtil.loadImageResource(getClass(), "/settings.png");
			if (settingsIcon != null)
			{
				settingsButton.setIcon(new ImageIcon(settingsIcon.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH)));
			}
			else
			{
				settingsButton.setText("⚙");
			}
		}
		catch (Exception e)
		{
			settingsButton.setText("⚙");
		}
		settingsButton.setBackground(new Color(60, 60, 60));
		settingsButton.setForeground(Color.WHITE);
		settingsButton.setFocusPainted(false);
		settingsButton.setBorderPainted(false);
		settingsButton.setContentAreaFilled(false);
		settingsButton.setPreferredSize(new Dimension(24, 24));
		settingsButton.addActionListener(e -> {
			if (onSettingsClick != null)
			{
				onSettingsClick.run();
			}
		});
		
		JPanel settingsPanel = new JPanel(new BorderLayout());
		settingsPanel.setBackground(new Color(26, 26, 26));
		settingsPanel.setBorder(new EmptyBorder(0, 0, 0, 5));
		settingsPanel.add(settingsButton, BorderLayout.CENTER);
		add(settingsPanel, BorderLayout.EAST);
	}
	
	public void setOnSettingsClick(Runnable onSettingsClick)
	{
		this.onSettingsClick = onSettingsClick;
	}
	
	public void setHeaderText(String text)
	{
		headerLabel.setText(text);
	}
}

