package com.cluejuggler.ui;

import com.cluejuggler.ClueJugglerPlugin;
import com.cluejuggler.model.ClueList;
import com.cluejuggler.service.ClueListService;
import com.cluejuggler.ui.panel.CurrentStepPanel;
import com.cluejuggler.ui.panel.HeaderPanel;
import com.cluejuggler.ui.panel.ListsSectionPanel;
import com.cluejuggler.ui.view.BadListView;
import com.cluejuggler.ui.view.CustomListView;
import com.cluejuggler.ui.view.GoodListView;
import com.cluejuggler.ui.view.SettingsView;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public class ClueJugglerPanel extends PluginPanel
{
	private static final String MAIN_VIEW = "MAIN";
	private static final String SETTINGS_VIEW = "SETTINGS";
	private static final String GOOD_LIST_VIEW = "GOOD_LIST";
	private static final String BAD_LIST_VIEW = "BAD_LIST";
	private static final String CUSTOM_LIST_VIEW = "CUSTOM_LIST";
	
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cardPanel = new JPanel(cardLayout);
	
	private ClueJugglerPlugin plugin;
	
	@Inject
	private ConfigManager configManager;
	
	private HeaderPanel headerPanel;
	private CurrentStepPanel currentStepPanel;
	private ListsSectionPanel listsSectionPanel;
	private GoodListView goodListView;
	private BadListView badListView;
	private CustomListView customListView;
	private SettingsView settingsView;
	
	private boolean initialized = false;

	public ClueJugglerPanel()
	{
		super();
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 5, 0));
		setBackground(new Color(26, 26, 26));
	}
	
	public void setPlugin(ClueJugglerPlugin plugin)
	{
		this.plugin = plugin;
		initializeUI();
	}
	
	private void initializeUI()
	{
		if (initialized || plugin == null)
		{
			return;
		}
		
		ClueListService listService = plugin.getListService();
		
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBackground(new Color(26, 26, 26));
		
		headerPanel = new HeaderPanel();
		headerPanel.setAlignmentX(0.0f);
		headerPanel.setOnSettingsClick(() -> {
			settingsView.refresh();
			cardLayout.show(cardPanel, SETTINGS_VIEW);
		});
		mainPanel.add(headerPanel);
		
		currentStepPanel = new CurrentStepPanel();
		currentStepPanel.setOnGoodClick((text, identifier) -> addToGoodList(text, identifier));
		currentStepPanel.setOnBadClick((text, identifier) -> addToBadList(text, identifier));
		mainPanel.add(currentStepPanel);
		
		listsSectionPanel = new ListsSectionPanel();
		listsSectionPanel.setOnGoodListClick(() -> {
			goodListView.refresh();
			cardLayout.show(cardPanel, GOOD_LIST_VIEW);
		});
		listsSectionPanel.setOnBadListClick(() -> {
			badListView.refresh();
			cardLayout.show(cardPanel, BAD_LIST_VIEW);
		});
		listsSectionPanel.setOnAddListClick(this::showCreateListDialog);
		listsSectionPanel.setOnCustomListClick(list -> {
			customListView.setList(list);
			cardLayout.show(cardPanel, CUSTOM_LIST_VIEW);
		});
		listsSectionPanel.setOnDeleteCustomList(list -> {
			int result = javax.swing.JOptionPane.showConfirmDialog(
						this,
				"Delete list '" + list.getName() + "'?",
				"Confirm Delete",
				javax.swing.JOptionPane.YES_NO_OPTION
			);
			if (result == javax.swing.JOptionPane.YES_OPTION)
			{
				plugin.getListService().deleteCustomList(list);
				listsSectionPanel.refreshCustomLists(plugin.getListService().getCustomLists());
				refreshCustomListButtons();
			}
		});
		mainPanel.add(listsSectionPanel);
		
		mainPanel.add(Box.createVerticalStrut(5));
		
		JPanel importExportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		importExportPanel.setBackground(new Color(26, 26, 26));
		importExportPanel.setAlignmentX(0.0f);
		importExportPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
		
		JButton exportButton = new JButton("Export");
		exportButton.setBackground(new Color(60, 60, 60));
		exportButton.setForeground(Color.WHITE);
		exportButton.setFocusPainted(false);
		exportButton.addActionListener(e -> exportLists());
		importExportPanel.add(exportButton);
		
		JButton importButton = new JButton("Import");
		importButton.setBackground(new Color(60, 60, 60));
		importButton.setForeground(Color.WHITE);
		importButton.setFocusPainted(false);
		importButton.addActionListener(e -> importLists());
		importExportPanel.add(importButton);
		
		mainPanel.add(importExportPanel);
		
		JScrollPane mainScrollPane = new JScrollPane(mainPanel);
		mainScrollPane.setBackground(new Color(26, 26, 26));
		mainScrollPane.getViewport().setBackground(new Color(26, 26, 26));
		mainScrollPane.setBorder(null);
		mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		
		goodListView = new GoodListView(listService);
		goodListView.setOnBackClick(() -> cardLayout.show(cardPanel, MAIN_VIEW));
		
		badListView = new BadListView(listService);
		badListView.setOnBackClick(() -> cardLayout.show(cardPanel, MAIN_VIEW));
		
		customListView = new CustomListView(listService);
		customListView.setOnBackClick(() -> {
			listsSectionPanel.refreshCustomLists(listService.getCustomLists());
			cardLayout.show(cardPanel, MAIN_VIEW);
		});
		
		settingsView = new SettingsView(listService, configManager);
		settingsView.setOnBackClick(() -> cardLayout.show(cardPanel, MAIN_VIEW));
		
		cardPanel.add(mainScrollPane, MAIN_VIEW);
		cardPanel.add(goodListView, GOOD_LIST_VIEW);
		cardPanel.add(badListView, BAD_LIST_VIEW);
		cardPanel.add(customListView, CUSTOM_LIST_VIEW);
		cardPanel.add(settingsView, SETTINGS_VIEW);
		cardPanel.setBackground(new Color(26, 26, 26));
		
		add(cardPanel, BorderLayout.CENTER);
		
		listService.ensureListsLoaded();
		listsSectionPanel.refreshCustomLists(listService.getCustomLists());
		refreshCustomListButtons();
		
		initialized = true;
	}
	
	private void addToGoodList(String text, String identifier)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		
		ClueListService listService = plugin.getListService();
		if (identifier == null || identifier.isEmpty())
		{
			identifier = "text:" + text.hashCode();
		}
		
		listService.addGoodClue(identifier, text);
		plugin.getTrackingService().clearClueStatusCacheEntry(identifier);
		plugin.getTrackingService().clearClueStatusCache();
		updateButtonStates(text);
	}
	
	private void addToBadList(String text, String identifier)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		
		ClueListService listService = plugin.getListService();
			if (identifier == null || identifier.isEmpty())
			{
				identifier = "text:" + text.hashCode();
			}
			
		listService.addBadClue(identifier, text);
		plugin.getTrackingService().clearClueStatusCacheEntry(identifier);
		plugin.getTrackingService().clearClueStatusCache();
		updateButtonStates(text);
	}
	
	private void updateButtonStates(String text)
	{
		if (text == null)
		{
			return;
		}
		
		ClueListService listService = plugin.getListService();
		boolean isGood = listService.isGoodClue(text);
		boolean isBad = listService.isBadClue(text);
		ClueList customList = listService.getCustomListForClueByText(text);
		
		currentStepPanel.updateButtonStates(isGood, isBad, customList);
	}
	
	private void refreshCustomListButtons()
	{
		if (currentStepPanel != null && plugin != null)
		{
			ClueListService listService = plugin.getListService();
			currentStepPanel.refreshCustomListButtons(listService.getCustomLists(), (list, identifier) -> {
				String text = currentStepPanel.getCurrentClueText();
		if (text != null && !text.isEmpty())
		{
			if (identifier == null || identifier.isEmpty())
			{
				identifier = "text:" + text.hashCode();
			}
					listService.addToCustomList(list, identifier, text);
					plugin.getTrackingService().clearClueStatusCacheEntry(identifier);
					plugin.getTrackingService().clearClueStatusCache();
			updateButtonStates(text);
				}
			});
		}
	}

	private void showCreateListDialog()
	{
		JPanel dialogPanel = new JPanel();
		dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
		dialogPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		dialogPanel.setBackground(new Color(26, 26, 26));

		JLabel nameLabel = new JLabel("List Name:");
		nameLabel.setForeground(Color.WHITE);
		dialogPanel.add(nameLabel);

		JTextField nameField = new JTextField(20);
		nameField.setBackground(new Color(20, 20, 20));
		nameField.setForeground(Color.WHITE);
		nameField.setBorder(new EmptyBorder(5, 5, 5, 5));
		dialogPanel.add(nameField);
		dialogPanel.add(Box.createVerticalStrut(10));

		JLabel textColorLabel = new JLabel("Text Color:");
		textColorLabel.setForeground(Color.WHITE);
		dialogPanel.add(textColorLabel);

		final Color[] selectedTextColor = {Color.WHITE};
		javax.swing.JButton textColorButton = new javax.swing.JButton();
		textColorButton.setPreferredSize(new Dimension(60, 30));
		textColorButton.setMaximumSize(new Dimension(60, 30));
		textColorButton.setBackground(selectedTextColor[0]);
		textColorButton.setBorder(new LineBorder(Color.WHITE, 1));
		textColorButton.setFocusPainted(false);
		textColorButton.addActionListener(e -> {
			Color newColor = JColorChooser.showDialog(dialogPanel, "Choose Text Color", selectedTextColor[0]);
			if (newColor != null)
			{
				textColorButton.setBackground(newColor);
				selectedTextColor[0] = newColor;
			}
		});
		dialogPanel.add(textColorButton);
		dialogPanel.add(Box.createVerticalStrut(10));

		JLabel tileColorLabel = new JLabel("Tile Color:");
		tileColorLabel.setForeground(Color.WHITE);
		dialogPanel.add(tileColorLabel);

		final Color[] selectedTileColor = {new Color(255, 255, 255, 100)};
		javax.swing.JButton tileColorButton = new javax.swing.JButton();
		tileColorButton.setPreferredSize(new Dimension(60, 30));
		tileColorButton.setMaximumSize(new Dimension(60, 30));
		tileColorButton.setBackground(selectedTileColor[0]);
		tileColorButton.setBorder(new LineBorder(Color.WHITE, 1));
		tileColorButton.setFocusPainted(false);
		tileColorButton.addActionListener(e -> {
			Color newColor = JColorChooser.showDialog(dialogPanel, "Choose Tile Color", selectedTileColor[0]);
			if (newColor != null)
			{
				int alpha = selectedTileColor[0].getAlpha();
				if (alpha == 255)
				{
					alpha = 100;
				}
				Color colorWithAlpha = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), alpha);
				tileColorButton.setBackground(colorWithAlpha);
				selectedTileColor[0] = colorWithAlpha;
			}
		});
		dialogPanel.add(tileColorButton);

		int result = JOptionPane.showConfirmDialog(
			this,
			dialogPanel,
			"Create New List",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);

		if (result == JOptionPane.OK_OPTION)
		{
			String listName = nameField.getText().trim();
			if (listName.isEmpty())
			{
				JOptionPane.showMessageDialog(this, "List name cannot be empty!", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			ClueListService listService = plugin.getListService();
			if (listService.findCustomListByName(listName) != null)
			{
				JOptionPane.showMessageDialog(this, "A list with that name already exists!", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			listService.createCustomList(listName, selectedTextColor[0], selectedTileColor[0], selectedTextColor[0]);
			listsSectionPanel.refreshCustomLists(listService.getCustomLists());
			refreshCustomListButtons();
		}
	}

	private void exportLists()
	{
		if (plugin == null)
		{
			return;
		}
		
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export All Lists");
		fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
		fileChooser.setSelectedFile(new File("cluejuggler-lists.json"));
		
		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			if (!file.getName().endsWith(".json"))
			{
				file = new File(file.getAbsolutePath() + ".json");
			}
			plugin.getListService().exportToFile(file);
			JOptionPane.showMessageDialog(this, "Export successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	private void importLists()
	{
		if (plugin == null)
		{
			return;
		}

		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Import Lists");
		fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
		
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			plugin.getListService().importFromFile(file);
			listsSectionPanel.refreshCustomLists(plugin.getListService().getCustomLists());
			refreshCustomListButtons();
			JOptionPane.showMessageDialog(this, "Import successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	public void setCurrentStep(String difficulty, String clueText)
	{
		if (currentStepPanel != null)
		{
			currentStepPanel.setCurrentStep(difficulty, clueText);
			
			if (plugin != null)
			{
				net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = plugin.getClueScrollPlugin().getClue();
							if (clue != null)
							{
					String identifier = plugin.getClueIdentifier(clue);
					currentStepPanel.setCurrentClueIdentifier(identifier);
				}
			}
			
			updateButtonStates(clueText);
		}
	}
	
	public void setCurrentStep(String clueText)
	{
		setCurrentStep("Unknown", clueText);
	}
	
	public boolean isGoodClue(String clueText)
	{
		return plugin != null && plugin.getListService().isGoodClue(clueText);
	}
	
	public boolean isBadClue(String clueText)
	{
		return plugin != null && plugin.getListService().isBadClue(clueText);
	}
	
	public boolean isGoodClueByIdentifier(String identifier)
	{
		return plugin != null && plugin.getListService().isGoodClueByIdentifier(identifier);
	}
	
	public boolean isBadClueByIdentifier(String identifier)
	{
		return plugin != null && plugin.getListService().isBadClueByIdentifier(identifier);
	}
	
	public ClueList getCustomListForClue(String identifier)
	{
		return plugin != null ? plugin.getListService().getCustomListForClue(identifier) : null;
	}
	
	public ClueList getCustomListForClueByText(String clueText)
	{
		return plugin != null ? plugin.getListService().getCustomListForClueByText(clueText) : null;
	}
	
	public boolean hasAnyCluesInLists()
	{
		return plugin != null && plugin.getListService().hasAnyCluesInLists();
	}
	
	public void ensureListsLoaded()
	{
		if (plugin != null)
		{
			plugin.getListService().ensureListsLoaded();
		}
	}
	
	public void ensureCustomListsLoaded()
	{
			if (plugin != null)
			{
			plugin.getListService().loadCustomLists();
		}
	}
}

