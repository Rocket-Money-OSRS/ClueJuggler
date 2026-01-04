package com.cluejuggler.ui.view;

import com.cluejuggler.service.ClueListService;
import com.cluejuggler.ui.component.ClueTableComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.util.ImageUtil;

public class BadListView extends JPanel
{
	private final ClueTableComponent tableComponent;
	private final ClueListService listService;
	private Runnable onBackClick;
	
	public BadListView(ClueListService listService)
	{
		this.listService = listService;
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
		
		JLabel titleLabel = new JLabel("Bad Steps");
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 18));
		titleLabel.setForeground(new Color(150, 0, 0));
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
		
		tableComponent = new ClueTableComponent();
		tableComponent.setOnDeleteClue((text, identifier) -> {
			listService.removeFromBad(identifier);
			listService.saveLists();
		});
		add(tableComponent, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttonPanel.setBackground(new Color(26, 26, 26));
		
		JButton exportButton = new JButton("Export");
		exportButton.setBackground(new Color(60, 60, 60));
		exportButton.setForeground(Color.WHITE);
		exportButton.setFocusPainted(false);
		exportButton.addActionListener(e -> exportList());
		buttonPanel.add(exportButton);
		
		JButton importButton = new JButton("Import");
		importButton.setBackground(new Color(60, 60, 60));
		importButton.setForeground(Color.WHITE);
		importButton.setFocusPainted(false);
		importButton.addActionListener(e -> importList());
		buttonPanel.add(importButton);
		
		JButton clearButton = new JButton("Clear All");
		clearButton.setBackground(new Color(100, 0, 0));
		clearButton.setForeground(Color.WHITE);
		clearButton.setFocusPainted(false);
		clearButton.addActionListener(e -> clearList());
		buttonPanel.add(clearButton);
		
		add(buttonPanel, BorderLayout.SOUTH);
	}
	
	public void setOnBackClick(Runnable onBackClick)
	{
		this.onBackClick = onBackClick;
	}
	
	public void refresh()
	{
		tableComponent.clear();
		for (java.util.Map.Entry<String, String> entry : listService.getBadClueIdentifiers().entrySet())
		{
			tableComponent.addRow(entry.getValue(), entry.getKey());
		}
	}
	
	private void exportList()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export Bad Steps");
		fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
		
		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			if (!file.getName().endsWith(".json"))
			{
				file = new File(file.getAbsolutePath() + ".json");
			}
			listService.exportToFile(file);
			JOptionPane.showMessageDialog(this, "Export successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	private void importList()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Import Bad Steps");
		fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
		
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			listService.importFromFile(file);
			refresh();
			JOptionPane.showMessageDialog(this, "Import successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	private void clearList()
	{
		int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all bad steps?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION)
		{
			listService.clearBadSteps();
			tableComponent.clear();
		}
	}
}

