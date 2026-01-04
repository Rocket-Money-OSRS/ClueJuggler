package com.cluejuggler.ui.component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class ColorPickerRow extends JPanel
{
	private final JButton colorButton;
	private Color currentColor;
	private final Consumer<Color> onColorChange;
	
	public ColorPickerRow(String name, String description, Color initialColor, Consumer<Color> onColorChange)
	{
		this.currentColor = initialColor;
		this.onColorChange = onColorChange;
		
		setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		setBackground(new Color(26, 26, 26));
		setBorder(new EmptyBorder(5, 0, 5, 0));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		setAlignmentX(0.0f);
		
		JLabel nameLabel = new JLabel(name + ": ");
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setToolTipText(description);
		add(nameLabel);
		
		colorButton = new JButton();
		colorButton.setPreferredSize(new Dimension(30, 20));
		colorButton.setBackground(initialColor);
		colorButton.setBorder(new LineBorder(Color.WHITE, 1));
		colorButton.setFocusPainted(false);
		colorButton.addActionListener(e -> showColorChooser());
		add(colorButton);
	}
	
	private void showColorChooser()
	{
		Color newColor = JColorChooser.showDialog(this, "Choose Color", currentColor);
		if (newColor != null)
		{
			if (currentColor.getAlpha() != 255)
			{
				newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), currentColor.getAlpha());
			}
			currentColor = newColor;
			colorButton.setBackground(currentColor);
			if (onColorChange != null)
			{
				onColorChange.accept(currentColor);
			}
		}
	}
	
	public Color getCurrentColor()
	{
		return currentColor;
	}
	
	public void setCurrentColor(Color color)
	{
		this.currentColor = color;
		colorButton.setBackground(color);
	}
}

