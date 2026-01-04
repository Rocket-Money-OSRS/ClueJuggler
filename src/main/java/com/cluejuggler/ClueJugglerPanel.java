package com.cluejuggler;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JMenu;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import javax.inject.Inject;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;

public class ClueJugglerPanel extends PluginPanel
{
	private final DefaultTableModel goodStepsModel = new DefaultTableModel(new String[]{"Clue", "Identifier"}, 0);
	private final DefaultTableModel badStepsModel = new DefaultTableModel(new String[]{"Clue", "Identifier"}, 0);
	private final JTable goodStepsTable = new JTable(goodStepsModel);
	private final JTable badStepsTable = new JTable(badStepsModel);
	
	// Map to store identifier -> text mapping for quick lookup
	private final java.util.Map<String, String> goodClueIdentifiers = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Map<String, String> badClueIdentifiers = new java.util.concurrent.ConcurrentHashMap<>();
	private final JTextArea currentStepArea = new JTextArea();
	private final JLabel headerLabel = new JLabel("Clue Juggler");
	private final JLabel clueTypeLabel = new JLabel("Clue Type: Type of Clue Scroll");
	private JButton goodButton;
	private JButton badButton;
	private String currentDisplayedClueText = null;
	private String currentClueIdentifier = null;
	private JPanel currentStepButtonPanel;
	private final java.util.Map<String, JButton> customListButtons = new java.util.HashMap<>();

	@Inject
	private ConfigManager configManager;

	private ClueJugglerPlugin plugin;
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cardPanel = new JPanel(cardLayout);
	private static final String MAIN_VIEW = "MAIN";
	private static final String SETTINGS_VIEW = "SETTINGS";
	private static final String GOOD_LIST_VIEW = "GOOD_LIST";
	private static final String BAD_LIST_VIEW = "BAD_LIST";
	
	private final List<ClueList> customLists = new ArrayList<>();
	private final Gson gson = new Gson();
	private JPanel listsSectionPanel;

	public void setPlugin(ClueJugglerPlugin plugin)
	{
		this.plugin = plugin;
	}

	public ClueJugglerPanel()
	{
		super();
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(new Color(26, 26, 26));
		
		// Add component listener early to catch when panel becomes visible
		ComponentAdapter initListener = new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent e) {
				initializeCustomListsDisplay();
			}
		};
		this.addComponentListener(initListener);
		
		// Also try to initialize after panel is fully constructed
		// This catches cases where component events don't fire or configManager isn't ready yet
		java.awt.EventQueue.invokeLater(() -> {
			java.awt.EventQueue.invokeLater(() -> {
				initializeCustomListsDisplay();
			});
		});

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		mainPanel.setBackground(new Color(26, 26, 26));

		JPanel headerPanel = createHeader();
		mainPanel.add(headerPanel, BorderLayout.NORTH);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBorder(new EmptyBorder(3, 0, 0, 0));
		contentPanel.setBackground(new Color(26, 26, 26));

		JPanel currentStepPanel = createCurrentStepPanel();
		currentStepPanel.setAlignmentX(0.0f);
		contentPanel.add(currentStepPanel);

		contentPanel.add(Box.createVerticalStrut(5));

		JPanel listsSection = createListsSection();
		listsSection.setAlignmentX(0.0f);
		contentPanel.add(listsSection);

		contentPanel.add(Box.createVerticalStrut(5));

		JPanel buttonPanel = createButtonPanel();
		buttonPanel.setAlignmentX(0.0f);
		contentPanel.add(buttonPanel);

		mainPanel.add(contentPanel, BorderLayout.CENTER);

		// Create placeholder panels for non-visible views - create actual panels lazily
		JPanel settingsPlaceholder = new JPanel();
		settingsPlaceholder.setName("SETTINGS_PLACEHOLDER");
		JPanel goodListPlaceholder = new JPanel();
		goodListPlaceholder.setName("GOOD_LIST_PLACEHOLDER");
		JPanel badListPlaceholder = new JPanel();
		badListPlaceholder.setName("BAD_LIST_PLACEHOLDER");

		cardPanel.add(mainPanel, MAIN_VIEW);
		cardPanel.add(settingsPlaceholder, SETTINGS_VIEW);
		cardPanel.add(goodListPlaceholder, GOOD_LIST_VIEW);
		cardPanel.add(badListPlaceholder, BAD_LIST_VIEW);
		cardPanel.setBackground(new Color(26, 26, 26));

		add(cardPanel, BorderLayout.CENTER);
		
		// Lists will be loaded asynchronously during startup or lazily when first accessed
		// Don't initialize custom list buttons here - wait until panel is viewed and configManager is ready
	}

	private JPanel createHeader()
	{
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

		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(new Color(26, 26, 26));

		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 18));
		headerLabel.setForeground(Color.WHITE);
		titlePanel.add(headerLabel, BorderLayout.NORTH);

		JLabel creditLabel = new JLabel("by: [RSN] Rocket Money");
		creditLabel.setFont(new Font(creditLabel.getFont().getName(), Font.PLAIN, 11));
		creditLabel.setForeground(new Color(150, 150, 150));
		creditLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
		titlePanel.add(creditLabel, BorderLayout.CENTER);

		leftPanel.add(titlePanel, BorderLayout.CENTER);
		headerPanel.add(leftPanel, BorderLayout.CENTER);

		JButton settingsButton = new JButton();
		try
		{
			BufferedImage settingsImage = ImageUtil.loadImageResource(getClass(), "/settings.png");
			if (settingsImage != null)
			{
				ImageIcon settingsIcon = new ImageIcon(settingsImage.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH));
				settingsButton.setIcon(settingsIcon);
			}
			else
			{
				settingsButton.setText("⚙");
				settingsButton.setFont(new Font(settingsButton.getFont().getName(), Font.PLAIN, 16));
			}
		}
		catch (Exception e)
		{
			settingsButton.setText("⚙");
			settingsButton.setFont(new Font(settingsButton.getFont().getName(), Font.PLAIN, 16));
		}
		settingsButton.setBackground(new Color(26, 26, 26));
		settingsButton.setForeground(Color.WHITE);
		settingsButton.setFocusPainted(false);
		settingsButton.setContentAreaFilled(false);
		settingsButton.setToolTipText("Settings");
		settingsButton.setPreferredSize(new Dimension(30, 30));
		settingsButton.setBorder(new EmptyBorder(5, 5, 5, 5));
		settingsButton.addActionListener(e -> {
			ensureSettingsPanelCreated();
			cardLayout.show(cardPanel, SETTINGS_VIEW);
		});
		headerPanel.add(settingsButton, BorderLayout.EAST);

		return headerPanel;
	}

	private JPanel createCurrentStepPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(new Color(60, 60, 60), 1),
			new EmptyBorder(6, 8, 6, 8)
		));
		panel.setBackground(new Color(26, 26, 26));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
		panel.setAlignmentX(0.0f);

		JPanel labelsPanel = new JPanel(new BorderLayout());
		labelsPanel.setBackground(new Color(26, 26, 26));
		labelsPanel.setAlignmentX(0.0f);

		clueTypeLabel.setForeground(Color.WHITE);
		clueTypeLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
		labelsPanel.add(clueTypeLabel, BorderLayout.NORTH);

		JLabel label = new JLabel("Current Step:");
		label.setForeground(Color.WHITE);
		label.setBorder(new EmptyBorder(0, 0, 2, 0));
		labelsPanel.add(label, BorderLayout.CENTER);

		panel.add(labelsPanel);

		panel.add(Box.createVerticalStrut(3));

		currentStepArea.setEditable(false);
		currentStepArea.setLineWrap(true);
		currentStepArea.setWrapStyleWord(true);
		currentStepArea.setBackground(new Color(20, 20, 20));
		currentStepArea.setForeground(Color.WHITE);
		currentStepArea.setBorder(new EmptyBorder(5, 5, 5, 5));
		currentStepArea.setRows(2);
		currentStepArea.setText("No clue step currently open");
		JScrollPane scrollPane = new JScrollPane(currentStepArea);
		scrollPane.setBorder(null);
		scrollPane.setPreferredSize(new Dimension(0, 50));
		scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		scrollPane.setMinimumSize(new Dimension(0, 35));
		scrollPane.setAlignmentX(0.0f);
		panel.add(scrollPane);

		JPanel mainButtonPanel = new JPanel();
		mainButtonPanel.setLayout(new BoxLayout(mainButtonPanel, BoxLayout.Y_AXIS));
		mainButtonPanel.setBackground(new Color(26, 26, 26));
		mainButtonPanel.setAlignmentX(0.0f);

		JPanel goodBadButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		goodBadButtonPanel.setBackground(new Color(26, 26, 26));
		goodBadButtonPanel.setAlignmentX(0.0f);

		goodButton = new JButton("Good");
		goodButton.setBackground(new Color(0, 150, 0));
		goodButton.setForeground(Color.WHITE);
		goodButton.setFocusPainted(false);
		goodButton.addActionListener(e -> addCurrentStepToGood());
		goodBadButtonPanel.add(goodButton);

		badButton = new JButton("Bad");
		badButton.setBackground(new Color(150, 0, 0));
		badButton.setForeground(Color.WHITE);
		badButton.setFocusPainted(false);
		badButton.addActionListener(e -> addCurrentStepToBad());
		goodBadButtonPanel.add(badButton);

		mainButtonPanel.add(goodBadButtonPanel);

		currentStepButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		currentStepButtonPanel.setBackground(new Color(26, 26, 26));
		currentStepButtonPanel.setAlignmentX(0.0f);
		mainButtonPanel.add(currentStepButtonPanel);

		refreshCustomListButtons();

		panel.add(mainButtonPanel);

		return panel;
	}

	private JPanel createListsSection()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(new Color(26, 26, 26));
		panel.setAlignmentX(0.0f);
		listsSectionPanel = panel;

		JLabel headerLabel = new JLabel("Lists");
		headerLabel.setForeground(Color.WHITE);
		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 14));
		headerLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
		headerLabel.setAlignmentX(0.0f);
		panel.add(headerLabel);

		JPanel goodCard = createListCard("Good Steps", new Color(0, 150, 0), false, () -> {
			ensureGoodListPanelCreated();
			ensureListsLoaded();
			ensureModelsPopulated();
			cardLayout.show(cardPanel, GOOD_LIST_VIEW);
		});
		goodCard.setAlignmentX(0.0f);
		panel.add(goodCard);

		panel.add(Box.createVerticalStrut(5));

		JPanel badCard = createListCard("Bad Steps", new Color(150, 0, 0), false, () -> {
			initializeCustomListsDisplay();
			ensureBadListPanelCreated();
			ensureListsLoaded();
			ensureModelsPopulated();
			cardLayout.show(cardPanel, BAD_LIST_VIEW);
		});
		badCard.setAlignmentX(0.0f);
		panel.add(badCard);

		// Custom lists will be loaded lazily when panel is first shown
		// Use a lightweight check to initialize on first access
		ComponentAdapter initListener = new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent e) {
				if (!customListsDisplayInitialized && configManager != null) {
					customListsDisplayInitialized = true;
					// Load and display custom lists on first show
					java.awt.EventQueue.invokeLater(() -> {
						ensureListsLoaded();
						refreshCustomListsDisplay();
					});
				}
			}
		};
		this.addComponentListener(initListener);

		JButton addListButton = new JButton("+ Add List");
		addListButton.setBackground(new Color(60, 60, 60));
		addListButton.setForeground(Color.WHITE);
		addListButton.setFocusPainted(false);
		addListButton.setAlignmentX(0.0f);
		addListButton.addActionListener(e -> {
			ensureListsLoaded();
			// Ensure display is initialized before showing dialog
			initializeCustomListsDisplay();
			showCreateListDialog();
		});
		panel.add(Box.createVerticalStrut(5));
		panel.add(addListButton);

		return panel;
	}

	private JPanel createListCard(String title, Color titleColor, boolean isCustom, Runnable onClick)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(new Color(60, 60, 60), 1),
			new EmptyBorder(6, 10, 6, 10)
		));
		card.setBackground(new Color(26, 26, 26));
		card.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		card.setPreferredSize(new Dimension(0, 32));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.setBackground(new Color(26, 26, 26));
		leftPanel.setOpaque(false);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(titleColor);
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 13));
		leftPanel.add(titleLabel, BorderLayout.CENTER);
		card.add(leftPanel, BorderLayout.CENTER);

		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		rightPanel.setBackground(new Color(26, 26, 26));
		rightPanel.setOpaque(false);

		JButton deleteButton = null;
		if (isCustom)
		{
			deleteButton = new JButton();
			try
			{
				BufferedImage deleteImage = ImageUtil.loadImageResource(getClass(), "/delete.png");
				if (deleteImage != null)
				{
					ImageIcon deleteIcon = new ImageIcon(deleteImage.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH));
					deleteButton.setIcon(deleteIcon);
				}
				else
				{
					deleteButton.setText("🗑");
					deleteButton.setFont(new Font(deleteButton.getFont().getName(), Font.PLAIN, 14));
				}
			}
			catch (Exception e)
			{
				deleteButton.setText("🗑");
				deleteButton.setFont(new Font(deleteButton.getFont().getName(), Font.PLAIN, 14));
			}
			deleteButton.setBackground(new Color(26, 26, 26));
			deleteButton.setForeground(new Color(200, 50, 50));
			deleteButton.setFocusPainted(false);
			deleteButton.setContentAreaFilled(false);
			deleteButton.setBorder(new EmptyBorder(0, 0, 0, 0));
			deleteButton.setPreferredSize(new Dimension(20, 20));
			final String listTitle = title;
			deleteButton.addActionListener(e -> {
				ClueList listToDelete = findCustomListByName(listTitle);
				if (listToDelete != null)
				{
					int confirm = JOptionPane.showConfirmDialog(
						this,
						"Are you sure you want to delete the list \"" + listTitle + "\"?",
						"Delete List",
						JOptionPane.YES_NO_OPTION
					);
					if (confirm == JOptionPane.YES_OPTION)
					{
						String viewKey = "CUSTOM_LIST_" + listTitle;
						
						// Switch to main view if we're viewing the deleted list
						CardLayout layout = (CardLayout) cardPanel.getLayout();
						Component currentCard = null;
						for (Component comp : cardPanel.getComponents())
						{
							if (comp.isVisible())
							{
								currentCard = comp;
								break;
							}
						}
						if (currentCard != null && viewKey.equals(currentCard.getName()))
						{
							layout.show(cardPanel, MAIN_VIEW);
						}
						
						// Remove from data structure FIRST
						customLists.remove(listToDelete);
						customListsLoaded = true; // Mark as loaded so refresh doesn't reload from config
						customListModels.remove(listTitle);
						
						// DIRECTLY remove the card and spacer from listsSectionPanel
						if (listsSectionPanel != null)
						{
							Component[] components = listsSectionPanel.getComponents();
							Component cardToRemove = null;
							Component spacerToRemove = null;
							
							// Find the card by looking for the JLabel with matching text
							// The label is nested: card -> leftPanel -> titleLabel
							for (int i = 0; i < components.length; i++)
							{
								Component comp = components[i];
								if (comp instanceof JPanel)
								{
									JPanel panel = (JPanel) comp;
									// Check if this panel is a card (has leftPanel with label)
									Component[] panelComps = panel.getComponents();
									for (Component panelComp : panelComps)
									{
										// Check if it's the leftPanel (BorderLayout.CENTER)
										if (panelComp instanceof JPanel)
										{
											JPanel innerPanel = (JPanel) panelComp;
											Component[] innerPanelComps = innerPanel.getComponents();
											for (Component innerPanelComp : innerPanelComps)
											{
												if (innerPanelComp instanceof JLabel)
												{
													JLabel label = (JLabel) innerPanelComp;
													if (listTitle.equals(label.getText()))
													{
														cardToRemove = comp;
														// Check if next component is a spacer
														if (i + 1 < components.length && components[i + 1] instanceof Box.Filler)
														{
															spacerToRemove = components[i + 1];
														}
														break;
													}
												}
											}
											if (cardToRemove != null)
											{
												break;
											}
										}
									}
									if (cardToRemove != null)
									{
										break;
									}
								}
							}
							
							// Remove the components
							if (cardToRemove != null)
							{
								listsSectionPanel.remove(cardToRemove);
							}
							if (spacerToRemove != null)
							{
								listsSectionPanel.remove(spacerToRemove);
							}
						}
						
						// Remove the card panel from cardPanel
						for (Component comp : cardPanel.getComponents())
						{
							if (comp instanceof JPanel)
							{
								JPanel panel = (JPanel) comp;
								if (viewKey.equals(panel.getName()))
								{
									cardPanel.remove(comp);
									break;
								}
							}
						}
						
						// Remove button
						JButton buttonToRemove = customListButtons.remove(listTitle);
						if (buttonToRemove != null && currentStepButtonPanel != null)
						{
							currentStepButtonPanel.remove(buttonToRemove);
							currentStepButtonPanel.revalidate();
							currentStepButtonPanel.repaint();
						}
						
						// Save
						saveCustomLists();
						
						// Refresh settings panel
						refreshSettingsPanel();
						
						// Force immediate UI update
						if (listsSectionPanel != null)
						{
							listsSectionPanel.revalidate();
							listsSectionPanel.repaint();
						}
						cardPanel.revalidate();
						cardPanel.repaint();
						revalidate();
						repaint();
					}
				}
			});
			rightPanel.add(deleteButton);
		}

		JLabel arrowLabel = new JLabel();
		try
		{
			BufferedImage arrowImage = ImageUtil.loadImageResource(getClass(), "/open_arrow.png");
			if (arrowImage != null)
			{
				ImageIcon arrowIcon = new ImageIcon(arrowImage.getScaledInstance(12, 12, java.awt.Image.SCALE_SMOOTH));
				arrowLabel.setIcon(arrowIcon);
			}
			else
			{
				arrowLabel.setText("→");
				arrowLabel.setForeground(new Color(150, 150, 150));
				arrowLabel.setFont(new Font(arrowLabel.getFont().getName(), Font.PLAIN, 16));
			}
		}
		catch (Exception e)
		{
			arrowLabel.setText("→");
			arrowLabel.setForeground(new Color(150, 150, 150));
			arrowLabel.setFont(new Font(arrowLabel.getFont().getName(), Font.PLAIN, 16));
		}
		rightPanel.add(arrowLabel);
		card.add(rightPanel, BorderLayout.EAST);

		final JButton finalDeleteButton = deleteButton;
		java.awt.event.MouseAdapter clickAdapter = new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (finalDeleteButton == null || e.getSource() != finalDeleteButton)
				{
					onClick.run();
				}
			}
		};
		card.addMouseListener(clickAdapter);
		titleLabel.addMouseListener(clickAdapter);
		arrowLabel.addMouseListener(clickAdapter);

		return card;
	}

	private JPanel createGoodListPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setBackground(new Color(26, 26, 26));

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		headerPanel.setBackground(new Color(26, 26, 26));

		JLabel titleLabel = new JLabel("Good Steps");
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 18));
		titleLabel.setForeground(new Color(0, 150, 0));
		headerPanel.add(titleLabel, BorderLayout.CENTER);

		JButton backButton = new JButton("← Back");
		backButton.setBackground(new Color(60, 60, 60));
		backButton.setForeground(Color.WHITE);
		backButton.setFocusPainted(false);
		backButton.addActionListener(e -> cardLayout.show(cardPanel, MAIN_VIEW));
		headerPanel.add(backButton, BorderLayout.EAST);

		panel.add(headerPanel, BorderLayout.NORTH);

		goodStepsTable.setBackground(new Color(20, 20, 20));
		goodStepsTable.setForeground(Color.WHITE);
		goodStepsTable.setSelectionBackground(new Color(255, 152, 0));
		goodStepsTable.setSelectionForeground(Color.WHITE);
		goodStepsTable.setGridColor(new Color(60, 60, 60));
		goodStepsTable.setShowGrid(true);
		goodStepsTable.setRowHeight(20);
		goodStepsTable.setTableHeader(null);
		goodStepsTable.setEnabled(true);
		setupTableContextMenu(goodStepsTable, true);
		
		TableColumn goodColumn = goodStepsTable.getColumnModel().getColumn(0);
		goodColumn.setResizable(true);
		goodColumn.setPreferredWidth(300);
		
		if (goodStepsTable.getColumnModel().getColumnCount() > 1)
		{
			TableColumn goodIdColumn = goodStepsTable.getColumnModel().getColumn(1);
			goodIdColumn.setMinWidth(0);
			goodIdColumn.setMaxWidth(0);
			goodIdColumn.setPreferredWidth(0);
			goodIdColumn.setResizable(false);
		}
		
		JScrollPane scrollPane = new JScrollPane(goodStepsTable);
		scrollPane.setBorder(null);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panel.add(scrollPane, BorderLayout.CENTER);

		return panel;
	}

	private JPanel createBadListPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setBackground(new Color(26, 26, 26));

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		headerPanel.setBackground(new Color(26, 26, 26));

		JLabel titleLabel = new JLabel("Bad Steps");
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 18));
		titleLabel.setForeground(new Color(150, 0, 0));
		headerPanel.add(titleLabel, BorderLayout.CENTER);

		JButton backButton = new JButton("← Back");
		backButton.setBackground(new Color(60, 60, 60));
		backButton.setForeground(Color.WHITE);
		backButton.setFocusPainted(false);
		backButton.addActionListener(e -> cardLayout.show(cardPanel, MAIN_VIEW));
		headerPanel.add(backButton, BorderLayout.EAST);

		panel.add(headerPanel, BorderLayout.NORTH);

		badStepsTable.setBackground(new Color(20, 20, 20));
		badStepsTable.setForeground(Color.WHITE);
		badStepsTable.setSelectionBackground(new Color(255, 152, 0));
		badStepsTable.setSelectionForeground(Color.WHITE);
		badStepsTable.setGridColor(new Color(60, 60, 60));
		badStepsTable.setShowGrid(true);
		badStepsTable.setRowHeight(20);
		badStepsTable.setTableHeader(null);
		badStepsTable.setEnabled(true);
		setupTableContextMenu(badStepsTable, false);
		
		TableColumn badColumn = badStepsTable.getColumnModel().getColumn(0);
		badColumn.setResizable(true);
		badColumn.setPreferredWidth(300);
		
		if (badStepsTable.getColumnModel().getColumnCount() > 1)
		{
			TableColumn badIdColumn = badStepsTable.getColumnModel().getColumn(1);
			badIdColumn.setMinWidth(0);
			badIdColumn.setMaxWidth(0);
			badIdColumn.setPreferredWidth(0);
			badIdColumn.setResizable(false);
		}
		
		JScrollPane scrollPane = new JScrollPane(badStepsTable);
		scrollPane.setBorder(null);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panel.add(scrollPane, BorderLayout.CENTER);

		return panel;
	}

	private JPanel createButtonPanel()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		panel.setBorder(new EmptyBorder(3, 0, 0, 0));
		panel.setBackground(new Color(26, 26, 26));

		JButton importButton = new JButton("Import Lists");
		importButton.setBackground(new Color(60, 60, 60));
		importButton.setForeground(Color.WHITE);
		importButton.setFocusPainted(false);
		importButton.addActionListener(e -> importLists());
		panel.add(importButton);

		JButton exportButton = new JButton("Export Lists");
		exportButton.setBackground(new Color(60, 60, 60));
		exportButton.setForeground(Color.WHITE);
		exportButton.setFocusPainted(false);
		exportButton.addActionListener(e -> exportLists());
		panel.add(exportButton);

		return panel;
	}

	private JPanel settingsContentPanel;

	private JPanel createSettingsPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setBackground(new Color(26, 26, 26));
		panel.setMaximumSize(new Dimension(220, Integer.MAX_VALUE));

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
			refreshSettingsPanel();
			cardLayout.show(cardPanel, MAIN_VIEW);
		});
		headerPanel.add(backButton, BorderLayout.EAST);

		panel.add(headerPanel, BorderLayout.NORTH);

		settingsContentPanel = new JPanel();
		settingsContentPanel.setLayout(new BoxLayout(settingsContentPanel, BoxLayout.Y_AXIS));
		settingsContentPanel.setBackground(new Color(26, 26, 26));

		refreshSettingsPanel();
		panel.add(settingsContentPanel, BorderLayout.CENTER);

		return panel;
	}

	private void refreshSettingsPanel()
	{
		if (settingsContentPanel == null)
		{
			return;
		}

		settingsContentPanel.removeAll();

		// Always add settings, even if plugin isn't set yet (will be updated when plugin is set)
		ClueJugglerConfig config = null;
		if (plugin != null && plugin.getConfig() != null)
		{
			config = plugin.getConfig();
		}

		JCheckBox showIndicatorCheck = new JCheckBox("Show Clue Indicator");
		showIndicatorCheck.setSelected(config != null ? config.showIndicator() : true);
		showIndicatorCheck.setForeground(Color.WHITE);
		showIndicatorCheck.setBackground(new Color(26, 26, 26));
		showIndicatorCheck.addActionListener(e -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "showIndicator", showIndicatorCheck.isSelected());
			}
		});
		settingsContentPanel.add(createSettingRow("Show Clue Indicator", "Display whether clues are good, bad, or unsure", showIndicatorCheck));

		JLabel highlighterLabel = new JLabel("Clue Highlighter");
		highlighterLabel.setFont(new Font(highlighterLabel.getFont().getName(), Font.BOLD, 14));
		highlighterLabel.setForeground(Color.WHITE);
		highlighterLabel.setBorder(new EmptyBorder(20, 0, 10, 0));
		settingsContentPanel.add(highlighterLabel);

		JCheckBox clueHighlightsCheck = new JCheckBox("Enable Clue Highlights");
		clueHighlightsCheck.setSelected(config != null ? config.clueHighlights() : true);
		clueHighlightsCheck.setForeground(Color.WHITE);
		clueHighlightsCheck.setBackground(new Color(26, 26, 26));
		clueHighlightsCheck.addActionListener(e -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "clueHighlights", clueHighlightsCheck.isSelected());
			}
		});
		settingsContentPanel.add(createSettingRow("Enable Clue Highlights", "Highlight the clue tile and show good/bad/unsure text on top of it", clueHighlightsCheck));

		settingsContentPanel.add(createColorSettingRow("Good Clue Tile Color", "Color for highlighting good clue tiles", 
			config != null ? config.goodClueTileColor() : new Color(0, 255, 0, 100), (color) -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "goodClueTileColor", color.getRGB());
			}
		}));

		settingsContentPanel.add(createColorSettingRow("Bad Clue Tile Color", "Color for highlighting bad clue tiles", 
			config != null ? config.badClueTileColor() : new Color(255, 0, 0, 100), (color) -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "badClueTileColor", color.getRGB());
			}
		}));

		settingsContentPanel.add(createColorSettingRow("Unsure Clue Tile Color", "Color for highlighting unsure clue tiles", 
			config != null ? config.unsureClueTileColor() : new Color(255, 255, 0, 100), (color) -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "unsureClueTileColor", color.getRGB());
			}
		}));

		settingsContentPanel.add(createColorSettingRow("Good Clue Text Color", "Color for good clue text", 
			config != null ? config.goodClueTextColor() : Color.GREEN, (color) -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "goodClueTextColor", color.getRGB());
			}
		}));

		settingsContentPanel.add(createColorSettingRow("Bad Clue Text Color", "Color for bad clue text", 
			config != null ? config.badClueTextColor() : Color.RED, (color) -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "badClueTextColor", color.getRGB());
			}
		}));

		settingsContentPanel.add(createColorSettingRow("Unsure Clue Text Color", "Color for unsure clue text", 
			config != null ? config.unsureClueTextColor() : Color.YELLOW, (color) -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "unsureClueTextColor", color.getRGB());
			}
		}));

		JCheckBox deprioritizeCheck = new JCheckBox("Deprioritize Bad Clues");
		deprioritizeCheck.setSelected(config != null ? config.deprioritizeBadClues() : true);
		deprioritizeCheck.setForeground(Color.WHITE);
		deprioritizeCheck.setBackground(new Color(26, 26, 26));
		deprioritizeCheck.addActionListener(e -> {
			if (configManager != null)
			{
				configManager.setConfiguration("cluejuggler", "deprioritizeBadClues", deprioritizeCheck.isSelected());
			}
		});
		settingsContentPanel.add(createSettingRow("Deprioritize Bad Clues", "Makes 'Walk here' the default action for bad clues to prevent accidental pickup", deprioritizeCheck));

		JLabel customListsLabel = new JLabel("Custom Lists");
		customListsLabel.setFont(new Font(customListsLabel.getFont().getName(), Font.BOLD, 14));
		customListsLabel.setForeground(Color.WHITE);
		customListsLabel.setBorder(new EmptyBorder(20, 0, 10, 0));
		settingsContentPanel.add(customListsLabel);

		for (ClueList list : customLists)
		{
			settingsContentPanel.add(createCustomListColorSettings(list, settingsContentPanel));
		}

		settingsContentPanel.revalidate();
		settingsContentPanel.repaint();
	}

	private JPanel createCustomListColorSettings(ClueList list, JPanel parentPanel)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(10, 0, 10, 0));
		panel.setBackground(new Color(26, 26, 26));

		JLabel nameLabel = new JLabel(list.getName());
		nameLabel.setForeground(list.getTextColor());
		nameLabel.setFont(new Font(nameLabel.getFont().getName(), Font.BOLD, 13));
		nameLabel.setAlignmentX(0.0f);
		panel.add(nameLabel);

		panel.add(createColorSettingRow(list.getName() + " - Tile Color", "Color for highlighting clue tiles in this list", 
			list.getTileColor(), (color) -> {
			list.setTileColor(color);
			saveCustomLists();
		}));

		panel.add(createColorSettingRow(list.getName() + " - Overlay Text Color", "Color for overlay text in this list", 
			list.getOverlayTextColor(), (color) -> {
			list.setOverlayTextColor(color);
			saveCustomLists();
		}));

		return panel;
	}

	private JPanel createSettingRow(String name, String description, JCheckBox checkBox)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(5, 0, 5, 0));
		panel.setBackground(new Color(26, 26, 26));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(0.0f);
		panel.add(nameLabel);

		JLabel descLabel = new JLabel("<html><body style='width: 200px'>" + description + "</body></html>");
		descLabel.setForeground(new Color(150, 150, 150));
		descLabel.setFont(new Font(descLabel.getFont().getName(), Font.PLAIN, 11));
		descLabel.setBorder(new EmptyBorder(2, 0, 5, 0));
		descLabel.setAlignmentX(0.0f);
		panel.add(descLabel);

		checkBox.setAlignmentX(0.0f);
		panel.add(checkBox);

		return panel;
	}

	private JPanel createColorSettingRow(String name, String description, Color currentColor, java.util.function.Consumer<Color> onColorChange)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(5, 0, 5, 0));
		panel.setBackground(new Color(26, 26, 26));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(0.0f);
		panel.add(nameLabel);

		JLabel descLabel = new JLabel("<html><body style='width: 200px'>" + description + "</body></html>");
		descLabel.setForeground(new Color(150, 150, 150));
		descLabel.setFont(new Font(descLabel.getFont().getName(), Font.PLAIN, 11));
		descLabel.setBorder(new EmptyBorder(2, 0, 5, 0));
		descLabel.setAlignmentX(0.0f);
		panel.add(descLabel);

		JButton colorButton = new JButton();
		colorButton.setPreferredSize(new Dimension(60, 30));
		colorButton.setMaximumSize(new Dimension(60, 30));
		colorButton.setBackground(currentColor);
		colorButton.setBorder(new LineBorder(Color.WHITE, 1));
		colorButton.setFocusPainted(false);
		colorButton.setAlignmentX(0.0f);
		colorButton.addActionListener(e -> {
			Color newColor = JColorChooser.showDialog(panel, "Choose Color", currentColor);
			if (newColor != null)
			{
				colorButton.setBackground(newColor);
				onColorChange.accept(newColor);
			}
		});
		panel.add(colorButton);

		return panel;
	}

	public void setCurrentStep(String clueText)
	{
		setCurrentStep("Unknown", clueText);
	}
	
	public void setCurrentStep(String difficulty, String clueText)
	{
		if (clueText != null && !clueText.isEmpty())
		{
			currentDisplayedClueText = clueText;
			// Always try to cache the identifier when clue is set
			// If on client thread, get it directly; otherwise, it will be retrieved when needed
			if (plugin != null)
			{
				net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
				if (clueScrollPlugin != null)
				{
					net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
					if (clue != null)
					{
						if (plugin.getClient().isClientThread())
						{
							// On client thread - get identifier directly
							currentClueIdentifier = plugin.getClueIdentifier(clue);
						}
						else
						{
							// Not on client thread - use text-based identifier as fallback for now
							// It will be updated properly when getClueIdentifier() is called on client thread
							currentClueIdentifier = "text:" + clueText.hashCode();
						}
					}
				}
			}
			currentStepArea.setText(clueText);
			// Set difficulty level in Clue Type area
			clueTypeLabel.setText("Clue Type: " + difficulty);
			updateButtonStates(clueText);
		}
		else
		{
			currentDisplayedClueText = null;
			currentClueIdentifier = null;
			currentStepArea.setText("No clue step currently open");
			clueTypeLabel.setText("Clue Type: Type of Clue Scroll");
			if (goodButton != null)
			{
				goodButton.setEnabled(true);
			}
			if (badButton != null)
			{
				badButton.setEnabled(true);
			}
		}
	}
	
	private void updateButtonStates(String clueText)
	{
		// Ensure lists are loaded before checking button states
		ensureListsLoaded();
		
		if (goodButton == null || badButton == null)
		{
			return;
		}
		
		// Use the storage format for checking, not the display format
		String storageText = getCurrentClueTextForStorage();
		if (storageText == null || storageText.isEmpty())
		{
			storageText = clueText;
		}
		
		String identifier = null;
		if (plugin != null)
		{
			identifier = getClueIdentifier();
		}
		if (identifier == null || identifier.isEmpty())
		{
			identifier = "text:" + storageText.hashCode();
		}
		
		boolean isGood = isGoodClueByIdentifier(identifier);
		if (!isGood && storageText != null && !storageText.isEmpty())
		{
			isGood = isGoodClue(storageText);
		}
		
		boolean isBad = isBadClueByIdentifier(identifier);
		if (!isBad && storageText != null && !storageText.isEmpty())
		{
			isBad = isBadClue(storageText);
		}
		
		ClueList customList = getCustomListForClue(identifier);
		if (customList == null && storageText != null && !storageText.isEmpty())
		{
			customList = getCustomListForClueByText(storageText);
		}
		
		// Good and bad buttons should only be disabled if the clue is already in good/bad
		// They should remain enabled even if the clue is in a custom list
		goodButton.setEnabled(!isGood);
		badButton.setEnabled(!isBad);
		
		// Custom list buttons should only be disabled if the clue is already in that specific list
		// They should remain enabled even if the clue is in good/bad (so you can move it)
		for (java.util.Map.Entry<String, JButton> entry : customListButtons.entrySet())
		{
			ClueList list = findCustomListByName(entry.getKey());
			if (list != null)
			{
				boolean isInList = list.getClueIdentifiers().containsKey(identifier);
				if (!isInList && storageText != null && !storageText.isEmpty())
				{
					isInList = list.getClues().contains(storageText);
				}
				// Only disable if it's already in this specific list
				entry.getValue().setEnabled(!isInList);
			}
		}
	}

	private void addCurrentStepToGood()
	{
		// getCurrentClueTextForStorage() now uses currentDisplayedClueText first, so no need for fallback
		String text = getCurrentClueTextForStorage();
		
		if (text != null && !text.isEmpty())
		{
			logToFile("MARKING AS GOOD", "Stored text: [" + text + "]");
			
			// Get clue identifier
			String identifier = getClueIdentifier();
			if (identifier == null || identifier.isEmpty())
			{
				// Fallback to text-based if no identifier
				identifier = "text:" + text.hashCode();
			}
			
			// Ensure models are populated before modifying
			ensureModelsPopulated();
			
			// Remove from bad list if present (by identifier and text as fallback)
			boolean wasInBad = removeFromListByIdentifierAndText(badStepsModel, identifier, text);
			if (wasInBad)
			{
				badClueIdentifiers.remove(identifier);
			}
			
			// Remove from all custom lists
			boolean customListModified = false;
			for (ClueList customList : customLists)
			{
				boolean wasInCustomList = customList.getClueIdentifiers().containsKey(identifier) || 
					(text != null && customList.getClues().contains(text));
				if (wasInCustomList)
				{
					customList.getClues().remove(text);
					customList.getClueIdentifiers().remove(identifier);
					customListModified = true;
					String listKey = customList.getName();
					DefaultTableModel model = customListModels.get(listKey);
					if (model != null)
					{
						for (int i = model.getRowCount() - 1; i >= 0; i--)
						{
							String rowIdentifier = (String) model.getValueAt(i, 1);
							String rowText = (String) model.getValueAt(i, 0);
							if ((identifier != null && identifier.equals(rowIdentifier)) || 
								(text != null && text.equals(rowText)))
							{
								model.removeRow(i);
								break;
							}
						}
					}
				}
			}
			if (customListModified)
			{
				customListsLoaded = true; // Mark as loaded since we modified it
				saveCustomLists();
			}
			
			// Remove from good list if already present (to avoid duplicates)
			boolean wasInGood = removeFromListByIdentifierAndText(goodStepsModel, identifier, text);
			if (wasInGood)
			{
				goodClueIdentifiers.remove(identifier);
			}
			
			// Add to good list (only if not already there)
			if (!wasInGood)
			{
				goodStepsModel.addRow(new Object[]{text, identifier});
				goodClueIdentifiers.put(identifier, text);
				saveLists();
			}
			
			// Ensure the clue is tracked by item ID so it persists when dropped
			if (plugin != null)
			{
				plugin.ensureClueTracked();
				// Clear old cache entries first to avoid conflicts
				if (identifier != null && !identifier.isEmpty())
				{
					plugin.clearClueStatusCacheEntry("customList:" + identifier);
				}
				// Cache by identifier first (always available)
				if (identifier != null && !identifier.isEmpty())
				{
					plugin.cacheClueStatus(identifier, 1); // 1 = good
				}
				
				// For beginner/master clues, set position immediately (doesn't require itemId)
				// Check if it's a beginner/master clue by checking the current clue and itemId if available
				net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
				boolean isBeginnerOrMaster = false;
				if (clueScrollPlugin != null)
				{
					net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
					if (clue != null)
					{
						isBeginnerOrMaster = clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
							clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
					}
				}
				// Also check by itemId if we're on client thread (more reliable)
				if (!isBeginnerOrMaster && plugin.getClient().isClientThread())
				{
					int itemId = plugin.getCurrentClueItemId();
					if (itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_BEGINNER ||
						itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_MASTER ||
						itemId == 23182)
					{
						isBeginnerOrMaster = true;
					}
				}
				
				if (isBeginnerOrMaster)
				{
					String finalIdentifier = identifier;
					if (finalIdentifier == null || finalIdentifier.isEmpty())
					{
						finalIdentifier = "text:" + text.hashCode();
					}
					// Set position immediately for beginner/master clues
					// The setBeginnerMasterCluePosition will overwrite any old position
					plugin.setBeginnerMasterCluePosition(text, finalIdentifier, true);
				}
				
				// Also try to cache by itemId if available (must be on client thread)
				if (plugin.getClient().isClientThread())
				{
					int itemId = plugin.getCurrentClueItemId();
					if (itemId > 0)
					{
						// Clear old cache entries for this itemId first
						plugin.clearClueStatusCacheEntry(itemId);
						plugin.clearClueStatusCacheEntry("customList:" + itemId);
						plugin.setLastMarkedClue(itemId, true);
						// Cache the good status by itemId so overlay updates immediately
						plugin.cacheClueStatus(itemId, 1); // 1 = good
						// Store mapping from itemId to identifier for dropped clues (CRITICAL for overlay)
						plugin.cacheItemIdToIdentifier(itemId, identifier);
						// Position already set above for beginner/master clues
						// Just cache itemId here if available
					}
				}
				else
				{
					// Not on client thread - invoke to get itemId and cache it
					final String finalIdentifier = identifier;
					plugin.getClientThread().invokeLater(() -> {
						int itemId = plugin.getCurrentClueItemId();
						if (itemId > 0)
						{
							// Clear old cache entries for this itemId
							plugin.clearClueStatusCacheEntry("customList:" + itemId);
							plugin.setLastMarkedClue(itemId, true);
							plugin.cacheClueStatus(itemId, 1); // 1 = good
							// Store mapping from itemId to identifier for dropped clues
							plugin.cacheItemIdToIdentifier(itemId, finalIdentifier);
							// Position already set above for beginner/master clues
							// Just cache itemId here if available
						}
					});
				}
			}
			
			updateButtonStates(text);
		}
	}

	private void addCurrentStepToBad()
	{
		// getCurrentClueTextForStorage() now uses currentDisplayedClueText first, so no need for fallback
		String text = getCurrentClueTextForStorage();
		
		if (text != null && !text.isEmpty())
		{
			logToFile("MARKING AS BAD", "Stored text: [" + text + "]");
			
			// Get clue identifier
			String identifier = getClueIdentifier();
			if (identifier == null || identifier.isEmpty())
			{
				// Fallback to text-based if no identifier
				identifier = "text:" + text.hashCode();
			}
			
			// Ensure models are populated before modifying
			ensureModelsPopulated();
			
			// Remove from good list if present (by identifier)
			boolean wasInGood = removeFromListByIdentifier(goodStepsModel, identifier);
			if (wasInGood)
			{
				goodClueIdentifiers.remove(identifier);
			}
			
			// Remove from all custom lists
			boolean customListModified = false;
			for (ClueList customList : customLists)
			{
				if (customList.getClueIdentifiers().containsKey(identifier))
				{
					customList.getClues().remove(text);
					customList.getClueIdentifiers().remove(identifier);
					customListModified = true;
					String listKey = customList.getName();
					DefaultTableModel model = customListModels.get(listKey);
					if (model != null)
					{
						for (int i = model.getRowCount() - 1; i >= 0; i--)
						{
							String rowIdentifier = (String) model.getValueAt(i, 1);
							if (identifier.equals(rowIdentifier))
							{
								model.removeRow(i);
								break;
							}
						}
					}
				}
			}
			if (customListModified)
			{
				customListsLoaded = true; // Mark as loaded since we modified it
				saveCustomLists();
			}
			
			// Remove from bad list if already present (to avoid duplicates)
			boolean wasInBad = removeFromListByIdentifierAndText(badStepsModel, identifier, text);
			if (wasInBad)
			{
				badClueIdentifiers.remove(identifier);
			}
			
			// Add to bad list (only if not already there)
			if (!wasInBad)
			{
				badStepsModel.addRow(new Object[]{text, identifier});
				badClueIdentifiers.put(identifier, text);
				saveLists();
			}
			
			// Ensure the clue is tracked by item ID so it persists when dropped
			if (plugin != null)
			{
				plugin.ensureClueTracked();
				// Clear old cache entries first to avoid conflicts
				if (identifier != null && !identifier.isEmpty())
				{
					plugin.clearClueStatusCacheEntry("customList:" + identifier);
				}
				// Cache by identifier first (always available)
				if (identifier != null && !identifier.isEmpty())
				{
					plugin.cacheClueStatus(identifier, -1); // -1 = bad
				}
				
				// For beginner/master clues, set position immediately (doesn't require itemId)
				net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
				boolean isBeginnerOrMaster = false;
				if (clueScrollPlugin != null)
				{
					net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
					if (clue != null)
					{
						isBeginnerOrMaster = clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
							clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
					}
				}
				// Also check by itemId if we're on client thread (more reliable)
				if (!isBeginnerOrMaster && plugin.getClient().isClientThread())
				{
					int itemId = plugin.getCurrentClueItemId();
					if (itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_BEGINNER ||
						itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_MASTER ||
						itemId == 23182)
					{
						isBeginnerOrMaster = true;
					}
				}
				
				if (isBeginnerOrMaster)
				{
					String finalIdentifier = identifier;
					if (finalIdentifier == null || finalIdentifier.isEmpty())
					{
						finalIdentifier = "text:" + text.hashCode();
					}
					// Set position immediately for beginner/master clues
					// The setBeginnerMasterCluePosition will overwrite any old position
					plugin.setBeginnerMasterCluePosition(text, finalIdentifier, false);
				}
				
				// Also try to cache by itemId if available (must be on client thread)
				if (plugin.getClient().isClientThread())
				{
					int itemId = plugin.getCurrentClueItemId();
					if (itemId > 0)
					{
						// Clear old cache entries for this itemId first
						plugin.clearClueStatusCacheEntry(itemId);
						plugin.clearClueStatusCacheEntry("customList:" + itemId);
						plugin.setLastMarkedClue(itemId, false);
						// Cache the bad status by itemId so overlay updates immediately
						plugin.cacheClueStatus(itemId, -1); // -1 = bad
						// Store mapping from itemId to identifier for dropped clues (CRITICAL for overlay)
						plugin.cacheItemIdToIdentifier(itemId, identifier);
						// Position already set above for beginner/master clues
						// Just cache itemId here if available
					}
				}
				else
				{
					// Not on client thread - invoke to get itemId and cache it
					final String finalIdentifier = identifier;
					plugin.getClientThread().invokeLater(() -> {
						int itemId = plugin.getCurrentClueItemId();
						if (itemId > 0)
						{
							// Clear old cache entries for this itemId first
							plugin.clearClueStatusCacheEntry(itemId);
							plugin.clearClueStatusCacheEntry("customList:" + itemId);
							plugin.setLastMarkedClue(itemId, false);
							plugin.cacheClueStatus(itemId, -1); // -1 = bad
							// Store mapping from itemId to identifier for dropped clues (CRITICAL for overlay)
							plugin.cacheItemIdToIdentifier(itemId, finalIdentifier);
							// Position already set above for beginner/master clues
							// Just cache itemId here if available
						}
					});
				}
			}
			
			updateButtonStates(text);
		}
	}


	private String getCurrentClueTextForStorage()
	{
		// Always use currentDisplayedClueText first - it's updated when setCurrentStep is called
		// This ensures we're using the clue that's actually displayed, not a stale one
		if (currentDisplayedClueText != null && !currentDisplayedClueText.isEmpty())
		{
			return currentDisplayedClueText;
		}
		
		// Fallback to getting from plugin if currentDisplayedClueText is not set
		if (plugin != null)
		{
			net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
			if (clueScrollPlugin != null)
			{
				net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
				if (clue != null)
				{
					String generatedText = plugin.generateClueTextFromClue(clue);
					if (generatedText != null && !generatedText.isEmpty())
					{
						return generatedText;
					}
				}
			}
		}
		
		return null;
	}
	
	private String getClueIdentifier()
	{
		// Always try to get the proper identifier from plugin if on client thread
		// This ensures we get the correct identifier (especially for beginner clues)
		// and that it matches what was stored when the clue was added to lists
		if (plugin != null && plugin.getClient().isClientThread())
		{
			net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
			if (clueScrollPlugin != null)
			{
				net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
				if (clue != null)
				{
					String identifier = plugin.getClueIdentifier(clue);
					if (identifier != null && !identifier.isEmpty())
					{
						// Cache it for future use
						currentClueIdentifier = identifier;
						return identifier;
					}
				}
			}
		}
		
		// Use cached identifier if available (from previous call on client thread)
		if (currentClueIdentifier != null && !currentClueIdentifier.isEmpty())
		{
			return currentClueIdentifier;
		}
		
		// Final fallback to text-based identifier
		String text = getCurrentClueTextForStorage();
		if (text != null && !text.isEmpty())
		{
			return "text:" + text.hashCode();
		}
		
		return null;
	}
	
	private boolean removeFromListByIdentifier(DefaultTableModel model, String identifier)
	{
		boolean removed = false;
		for (int i = model.getRowCount() - 1; i >= 0; i--)
		{
			if (model.getColumnCount() > 1)
			{
				String rowIdentifier = (String) model.getValueAt(i, 1);
				if (identifier != null && identifier.equals(rowIdentifier))
				{
					model.removeRow(i);
					removed = true;
				}
			}
		}
		return removed;
	}
	
	private boolean removeFromListByIdentifierAndText(DefaultTableModel model, String identifier, String text)
	{
		// First try by identifier
		boolean removed = removeFromListByIdentifier(model, identifier);
		
		// If not found by identifier, try by text as fallback
		if (!removed && text != null && !text.isEmpty())
		{
			// Check if it exists by text before removing
			for (int i = model.getRowCount() - 1; i >= 0; i--)
			{
				String rowText = (String) model.getValueAt(i, 0);
				if (text.equals(rowText))
				{
					model.removeRow(i);
					removed = true;
					break;
				}
			}
		}
		
		return removed;
	}
	
	private boolean isInList(DefaultTableModel model, String text)
	{
		for (int i = 0; i < model.getRowCount(); i++)
		{
			String rowText = (String) model.getValueAt(i, 0);
			if (text.equals(rowText))
			{
				return true;
			}
		}
		return false;
	}
	
	private void removeFromList(DefaultTableModel model, String text)
	{
		for (int i = model.getRowCount() - 1; i >= 0; i--)
		{
			String rowText = (String) model.getValueAt(i, 0);
			if (text.equals(rowText))
			{
				model.removeRow(i);
			}
		}
	}

	public void addGoodStep(String clueText)
	{
		if (clueText != null && !clueText.isEmpty())
		{
			// Remove from bad list if present
			removeFromList(badStepsModel, clueText);
			// Add to good list if not already present
			if (!isInList(goodStepsModel, clueText))
			{
				goodStepsModel.addRow(new Object[]{clueText});
				saveLists();
			}
		}
	}

	public void addBadStep(String clueText)
	{
		if (clueText != null && !clueText.isEmpty())
		{
			// Remove from good list if present
			removeFromList(goodStepsModel, clueText);
			// Add to bad list if not already present
			if (!isInList(badStepsModel, clueText))
			{
				badStepsModel.addRow(new Object[]{clueText});
				saveLists();
			}
		}
	}

	private void importLists()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
		fileChooser.setDialogTitle("Import Clue Lists");

		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			try (BufferedReader reader = new BufferedReader(new FileReader(file)))
			{
				goodStepsModel.setRowCount(0);
				badStepsModel.setRowCount(0);

				String line;
				boolean inGoodSection = false;
				boolean inBadSection = false;

				while ((line = reader.readLine()) != null)
				{
					line = line.trim();
					if (line.equals("[GOOD]"))
					{
						inGoodSection = true;
						inBadSection = false;
					}
					else if (line.equals("[BAD]"))
					{
						inGoodSection = false;
						inBadSection = true;
					}
					else if (!line.isEmpty())
					{
						if (inGoodSection)
						{
							if (!isInList(goodStepsModel, line))
							{
								goodStepsModel.addRow(new Object[]{line});
							}
						}
						else if (inBadSection)
						{
							if (!isInList(badStepsModel, line))
							{
								badStepsModel.addRow(new Object[]{line});
							}
						}
					}
				}

				JOptionPane.showMessageDialog(this, "Lists imported successfully!", "Import", JOptionPane.INFORMATION_MESSAGE);
				saveLists();
			}
			catch (IOException e)
			{
				JOptionPane.showMessageDialog(this, "Error importing file: " + e.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void exportLists()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
		fileChooser.setDialogTitle("Export Clue Lists");

		int result = fileChooser.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			if (!file.getName().endsWith(".txt"))
			{
				file = new File(file.getAbsolutePath() + ".txt");
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file)))
			{
				writer.write("[GOOD]");
				writer.newLine();
				for (int i = 0; i < goodStepsModel.getRowCount(); i++)
				{
					writer.write((String) goodStepsModel.getValueAt(i, 0));
					writer.newLine();
				}

				writer.newLine();
				writer.write("[BAD]");
				writer.newLine();
				for (int i = 0; i < badStepsModel.getRowCount(); i++)
				{
					writer.write((String) badStepsModel.getValueAt(i, 0));
					writer.newLine();
				}

				JOptionPane.showMessageDialog(this, "Lists exported successfully!", "Export", JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException e)
			{
				JOptionPane.showMessageDialog(this, "Error exporting file: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void clearGoodSteps()
	{
		goodStepsModel.setRowCount(0);
	}

	public void clearBadSteps()
	{
		badStepsModel.setRowCount(0);
	}

	private void setupTableContextMenu(JTable table, boolean isGoodTable)
	{
		JPopupMenu contextMenu = new JPopupMenu();
		
		JMenuItem moveToOtherList = new JMenuItem(isGoodTable ? "Move to Bad Steps" : "Move to Good Steps");
		moveToOtherList.addActionListener(e -> {
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0)
			{
				String clueText = (String) table.getValueAt(selectedRow, 0);
				String identifier = null;
				if (table.getColumnCount() > 1)
				{
					identifier = (String) table.getValueAt(selectedRow, 1);
				}
				if (identifier == null || identifier.isEmpty())
				{
					identifier = "text:" + clueText.hashCode();
				}
				
				if (isGoodTable)
				{
					removeFromListByIdentifier(goodStepsModel, identifier);
					goodClueIdentifiers.remove(identifier);
					if (!badClueIdentifiers.containsKey(identifier))
					{
						badStepsModel.addRow(new Object[]{clueText, identifier});
						badClueIdentifiers.put(identifier, clueText);
					}
				}
				else
				{
					removeFromListByIdentifier(badStepsModel, identifier);
					badClueIdentifiers.remove(identifier);
					// Clear cache so overlay recalculates status
					if (plugin != null)
					{
						plugin.clearClueStatusCache();
					}
					if (!goodClueIdentifiers.containsKey(identifier))
					{
						goodStepsModel.addRow(new Object[]{clueText, identifier});
						goodClueIdentifiers.put(identifier, clueText);
					}
				}
				saveLists();
				if (currentDisplayedClueText != null)
				{
					updateButtonStates(currentDisplayedClueText);
				}
			}
		});
		
		JMenuItem removeItem = new JMenuItem("Remove");
		removeItem.addActionListener(e -> {
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0)
			{
				String clueText = (String) (isGoodTable ? goodStepsModel : badStepsModel).getValueAt(selectedRow, 0);
				String identifier = null;
				if ((isGoodTable ? goodStepsModel : badStepsModel).getColumnCount() > 1)
				{
					identifier = (String) (isGoodTable ? goodStepsModel : badStepsModel).getValueAt(selectedRow, 1);
				}
				if (identifier == null || identifier.isEmpty())
				{
					identifier = "text:" + (clueText != null ? clueText.hashCode() : 0);
				}
				
				if (isGoodTable)
				{
					goodStepsModel.removeRow(selectedRow);
					goodClueIdentifiers.remove(identifier);
				}
				else
				{
					badStepsModel.removeRow(selectedRow);
					badClueIdentifiers.remove(identifier);
				}
				
				// Clear cache entries for this clue
				if (plugin != null && identifier != null)
				{
					plugin.clearClueStatusCacheEntry(identifier);
					plugin.clearClueStatusCacheEntry("customList:" + identifier);
					
					// Clear beginner/master position if applicable
					plugin.clearBeginnerMasterCluePositionAtCurrentLocation();
					
					// Try to get itemId and clear cache entries for it too
					final String finalIdentifier = identifier;
					if (plugin.getClient().isClientThread())
					{
						// Try to find itemId from tracked clues
						Map<Integer, ClueScroll> trackedClues = plugin.getTrackedClues();
						for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
						{
							ClueScroll clue = entry.getValue();
							if (clue != null)
							{
								String clueId = plugin.getClueIdentifier(clue);
								if (finalIdentifier.equals(clueId))
								{
									int itemId = entry.getKey();
									plugin.clearClueStatusCacheEntry(itemId);
									plugin.clearClueStatusCacheEntry("customList:" + itemId);
									break;
								}
							}
						}
					}
					else
					{
						plugin.getClientThread().invokeLater(() -> {
							Map<Integer, ClueScroll> trackedClues = plugin.getTrackedClues();
							for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
							{
								ClueScroll clue = entry.getValue();
								if (clue != null)
								{
									String clueId = plugin.getClueIdentifier(clue);
									if (finalIdentifier.equals(clueId))
									{
										int itemId = entry.getKey();
										plugin.clearClueStatusCacheEntry(itemId);
										plugin.clearClueStatusCacheEntry("customList:" + itemId);
										break;
									}
								}
							}
						});
					}
				}
				
				saveLists();
				if (currentDisplayedClueText != null)
				{
					updateButtonStates(currentDisplayedClueText);
				}
			}
		});
		
		contextMenu.add(moveToOtherList);
		
		if (!customLists.isEmpty())
		{
			contextMenu.addSeparator();
			JMenu moveToCustomMenu = new JMenu("Move to Custom List");
			for (ClueList customList : customLists)
			{
				JMenuItem customListItem = new JMenuItem(customList.getName());
				final ClueList targetList = customList;
				customListItem.addActionListener(e -> {
					int selectedRow = table.getSelectedRow();
					if (selectedRow >= 0)
					{
						String clueText = (String) table.getValueAt(selectedRow, 0);
						String identifier = null;
						if (table.getColumnCount() > 1)
						{
							identifier = (String) table.getValueAt(selectedRow, 1);
						}
						if (identifier == null || identifier.isEmpty())
						{
							identifier = "text:" + clueText.hashCode();
						}
						
						if (isGoodTable)
						{
							removeFromListByIdentifier(goodStepsModel, identifier);
							goodClueIdentifiers.remove(identifier);
						}
						else
						{
							removeFromListByIdentifier(badStepsModel, identifier);
							badClueIdentifiers.remove(identifier);
						}
						
						if (plugin != null)
						{
							plugin.clearClueStatusCache();
						}
						
						if (!targetList.getClues().contains(clueText))
						{
							targetList.getClues().add(clueText);
							targetList.getClueIdentifiers().put(identifier, clueText);
							
							String listKey = targetList.getName();
							DefaultTableModel model = customListModels.get(listKey);
							if (model != null)
							{
								model.addRow(new Object[]{clueText, identifier});
							}
						}
						
						saveLists();
						saveCustomLists();
						if (currentDisplayedClueText != null)
						{
							updateButtonStates(currentDisplayedClueText);
						}
					}
				});
				moveToCustomMenu.add(customListItem);
			}
			contextMenu.add(moveToCustomMenu);
		}
		
		contextMenu.add(removeItem);
		
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger())
				{
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount())
					{
						table.setRowSelectionInterval(row, row);
						contextMenu.show(table, e.getX(), e.getY());
					}
				}
			}
			
			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger())
				{
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount())
					{
						table.setRowSelectionInterval(row, row);
						contextMenu.show(table, e.getX(), e.getY());
					}
				}
			}
		});
	}

	public boolean isGoodClue(String clueText)
	{
		// Ensure lists are loaded before checking
		if (!listsLoaded)
		{
			loadLists();
		}
		
		// First try identifier-based matching (more reliable)
		// Try to get identifier from the plugin if available
		if (plugin != null)
		{
			net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
			if (clueScrollPlugin != null)
			{
				net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
				if (clue != null)
				{
					String identifier = plugin.getClueIdentifier(clue);
					if (identifier != null && !identifier.isEmpty())
					{
						if (goodClueIdentifiers.containsKey(identifier))
						{
							return true;
						}
					}
				}
			}
		}
		
		// Also try getting identifier from panel method
		String identifier = getClueIdentifier();
		if (identifier != null && !identifier.isEmpty())
		{
			if (goodClueIdentifiers.containsKey(identifier))
			{
				return true;
			}
		}
		
		// Fallback to text-based matching for backwards compatibility
		if (clueText == null || clueText.isEmpty())
		{
			return false;
		}
		
		return isInList(goodStepsModel, clueText);
	}
	
	public boolean isGoodClueByIdentifier(String identifier)
	{
		// Ensure lists are loaded before checking
		if (!listsLoaded)
		{
			loadLists();
		}
		return identifier != null && !identifier.isEmpty() && goodClueIdentifiers.containsKey(identifier);
	}

	public boolean isBadClue(String clueText)
	{
		// Ensure lists are loaded before checking
		if (!listsLoaded)
		{
			loadLists();
		}
		
		// First try identifier-based matching (more reliable)
		// Try to get identifier from the plugin if available
		if (plugin != null)
		{
			net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
			if (clueScrollPlugin != null)
			{
				net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
				if (clue != null)
				{
					String identifier = plugin.getClueIdentifier(clue);
					if (identifier != null && !identifier.isEmpty())
					{
						if (badClueIdentifiers.containsKey(identifier))
						{
							return true;
						}
					}
				}
			}
		}
		
		// Also try getting identifier from panel method
		String identifier = getClueIdentifier();
		if (identifier != null && !identifier.isEmpty())
		{
			if (badClueIdentifiers.containsKey(identifier))
			{
				return true;
			}
		}
		
		// Fallback to text-based matching for backwards compatibility
		if (clueText == null || clueText.isEmpty())
		{
			return false;
		}
		
		return isInList(badStepsModel, clueText);
	}
	
	public boolean isBadClueByIdentifier(String identifier)
	{
		// Ensure lists are loaded before checking
		if (!listsLoaded)
		{
			loadLists();
		}
		return identifier != null && !identifier.isEmpty() && badClueIdentifiers.containsKey(identifier);
	}

	public void ensureCustomListsLoaded()
	{
		// Ensure both regular lists and custom lists are loaded
		ensureListsLoaded();
	}
	
	public boolean hasAnyCluesInLists()
	{
		// Ensure lists are loaded before checking
		ensureListsLoaded();
		ensureCustomListsLoaded();
		
		// Check if good list has any clues
		if (!goodClueIdentifiers.isEmpty() || goodStepsModel.getRowCount() > 0)
		{
			return true;
		}
		
		// Check if bad list has any clues
		if (!badClueIdentifiers.isEmpty() || badStepsModel.getRowCount() > 0)
		{
			return true;
		}
		
		// Check if any custom lists have clues
		for (ClueList list : customLists)
		{
			if (!list.getClueIdentifiers().isEmpty() || !list.getClues().isEmpty())
			{
				return true;
			}
		}
		
		return false;
	}

	public ClueList getCustomListForClue(String identifier)
	{
		if (identifier == null || identifier.isEmpty())
		{
			return null;
		}
		
		// Always ensure lists are loaded - this is safe to call multiple times
		loadCustomLists();
		
		for (ClueList list : customLists)
		{
			if (list.getClueIdentifiers().containsKey(identifier))
			{
				return list;
			}
		}
		return null;
	}

	public ClueList getCustomListForClueByText(String clueText)
	{
		if (clueText == null || clueText.isEmpty())
		{
			return null;
		}
		
		// Always ensure lists are loaded - this is safe to call multiple times
		loadCustomLists();
		
		for (ClueList list : customLists)
		{
			if (list.getClues().contains(clueText))
			{
				return list;
			}
		}
		return null;
	}

	private String extractClueKey(String clueText)
	{
		if (clueText == null || clueText.isEmpty())
		{
			return null;
		}
		
		if (clueText.contains(": "))
		{
			String[] parts = clueText.split(": ", 2);
			if (parts.length == 2)
			{
				String clueType = parts[0].trim();
				String description = parts[1].trim();
				
				String normalizedDesc = normalizeClueText(description);
				if (normalizedDesc.length() > 50)
				{
					normalizedDesc = normalizedDesc.substring(0, 50);
				}
				
				return clueType + ": " + normalizedDesc;
			}
		}
		
		return normalizeClueText(clueText);
	}

	private boolean isInListByKey(DefaultTableModel model, String key)
	{
		String normalizedKey = normalizeClueText(key);
		
		for (int i = 0; i < model.getRowCount(); i++)
		{
			String rowText = (String) model.getValueAt(i, 0);
			if (rowText == null)
			{
				continue;
			}
			
			if (rowText.equals(key))
			{
				return true;
			}
			
			String normalizedRow = normalizeClueText(rowText);
			if (normalizedRow.equals(normalizedKey))
			{
				return true;
			}
			
			String rowKey = extractClueKey(rowText);
			String keyKey = extractClueKey(key);
			if (rowKey != null && keyKey != null && rowKey.equals(keyKey))
			{
				return true;
			}
			
			if (rowText.contains(": ") && key.contains(": "))
			{
				String[] rowParts = rowText.split(": ", 2);
				String[] keyParts = key.split(": ", 2);
				if (rowParts.length == 2 && keyParts.length == 2)
				{
					String rowType = rowParts[0].trim();
					String keyType = keyParts[0].trim();
					if (rowType.equals(keyType))
					{
						String rowDesc = normalizeClueText(rowParts[1].trim());
						String keyDesc = normalizeClueText(keyParts[1].trim());
						
						int minLen = Math.min(rowDesc.length(), keyDesc.length());
						if (minLen > 0)
						{
							String rowPrefix = rowDesc.substring(0, Math.min(minLen, 50));
							String keyPrefix = keyDesc.substring(0, Math.min(minLen, 50));
							if (rowPrefix.equals(keyPrefix))
							{
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	private String normalizeClueText(String clueText)
	{
		if (clueText == null || clueText.isEmpty())
		{
			return clueText;
		}
		
		String normalized = clueText.trim();
		if (normalized.endsWith("..."))
		{
			normalized = normalized.substring(0, normalized.length() - 3).trim();
		}
		
		return normalized;
	}

	private boolean listsLoaded = false;
	
	public void loadLists()
	{
		if (configManager == null || listsLoaded)
		{
			return;
		}

		String goodStepsData = configManager.getConfiguration("cluejuggler", "goodSteps");
		String badStepsData = configManager.getConfiguration("cluejuggler", "badSteps");

		// Clear data structures first (fast)
		goodClueIdentifiers.clear();
		badClueIdentifiers.clear();
		
		// Load data into maps only (fast, no UI operations)
		// UI model updates will happen lazily when panels are viewed
		// Format: "text::identifier|text::identifier|..." or legacy "text|text|..." format
		if (goodStepsData != null && !goodStepsData.isEmpty())
		{
			String[] items = goodStepsData.split("\\|");
			for (String item : items)
			{
				item = item.trim();
				if (!item.isEmpty())
				{
					// Check if it's new format with identifier (text::identifier)
					String[] parts = item.split("::", 2);
					String text;
					String identifier;
					if (parts.length == 2)
					{
						// New format: text::identifier
						text = parts[0].trim();
						identifier = parts[1].trim();
					}
					else
					{
						// Legacy format: just text
						text = item;
						identifier = "text:" + text.hashCode();
					}
					if (!text.isEmpty() && !identifier.isEmpty())
					{
						goodClueIdentifiers.put(identifier, text);
					}
				}
			}
		}

		if (badStepsData != null && !badStepsData.isEmpty())
		{
			String[] items = badStepsData.split("\\|");
			for (String item : items)
			{
				item = item.trim();
				if (!item.isEmpty())
				{
					// Check if it's new format with identifier (text::identifier)
					String[] parts = item.split("::", 2);
					String text;
					String identifier;
					if (parts.length == 2)
					{
						// New format: text::identifier
						text = parts[0].trim();
						identifier = parts[1].trim();
					}
					else
					{
						// Legacy format: just text
						text = item;
						identifier = "text:" + text.hashCode();
					}
					if (!text.isEmpty() && !identifier.isEmpty())
					{
						badClueIdentifiers.put(identifier, text);
					}
				}
			}
		}

		listsLoaded = true;
	}
	
	private boolean modelsPopulated = false;
	
	private void populateListModels()
	{
		if (modelsPopulated)
		{
			return;
		}
		
		// Populate table models from loaded data
		// This is called lazily when the panels are first viewed
		java.util.List<Object[]> goodRows = new java.util.ArrayList<>();
		java.util.List<Object[]> badRows = new java.util.ArrayList<>();
		
		for (java.util.Map.Entry<String, String> entry : goodClueIdentifiers.entrySet())
		{
			goodRows.add(new Object[]{entry.getValue(), entry.getKey()});
		}
		
		for (java.util.Map.Entry<String, String> entry : badClueIdentifiers.entrySet())
		{
			badRows.add(new Object[]{entry.getValue(), entry.getKey()});
		}
		
		if (!goodRows.isEmpty())
		{
			Object[][] goodData = goodRows.toArray(new Object[goodRows.size()][]);
			goodStepsModel.setDataVector(goodData, new String[]{"Clue", "Identifier"});
		}
		else
		{
			goodStepsModel.setRowCount(0);
		}
		
		if (!badRows.isEmpty())
		{
			Object[][] badData = badRows.toArray(new Object[badRows.size()][]);
			badStepsModel.setDataVector(badData, new String[]{"Clue", "Identifier"});
		}
		else
		{
			badStepsModel.setRowCount(0);
		}
		
		modelsPopulated = true;
	}

	public void ensureListsLoaded()
	{
		// Load both lists lazily - only when needed
		if (!listsLoaded)
		{
			loadLists();
		}
		if (!customListsLoaded)
		{
			loadCustomLists();
		}
	}
	
	private void ensureModelsPopulated()
	{
		// Ensure lists are loaded before populating models
		ensureListsLoaded();
		if (!modelsPopulated)
		{
			populateListModels();
		}
	}
	
	private boolean settingsPanelCreated = false;
	private boolean goodListPanelCreated = false;
	private boolean badListPanelCreated = false;
	
	private void ensureSettingsPanelCreated()
	{
		if (!settingsPanelCreated)
		{
			JPanel settingsPanel = createSettingsPanel();
			Component[] components = cardPanel.getComponents();
			for (Component comp : components)
			{
				if (comp.getName() != null && comp.getName().equals("SETTINGS_PLACEHOLDER"))
				{
					cardPanel.remove(comp);
					break;
				}
			}
			cardPanel.add(settingsPanel, SETTINGS_VIEW);
			settingsPanelCreated = true;
		}
	}
	
	private void ensureGoodListPanelCreated()
	{
		if (!goodListPanelCreated)
		{
			JPanel goodListPanel = createGoodListPanel();
			Component[] components = cardPanel.getComponents();
			for (Component comp : components)
			{
				if (comp.getName() != null && comp.getName().equals("GOOD_LIST_PLACEHOLDER"))
				{
					cardPanel.remove(comp);
					break;
				}
			}
			cardPanel.add(goodListPanel, GOOD_LIST_VIEW);
			goodListPanelCreated = true;
		}
	}
	
	private void ensureBadListPanelCreated()
	{
		if (!badListPanelCreated)
		{
			JPanel badListPanel = createBadListPanel();
			Component[] components = cardPanel.getComponents();
			for (Component comp : components)
			{
				if (comp.getName() != null && comp.getName().equals("BAD_LIST_PLACEHOLDER"))
				{
					cardPanel.remove(comp);
					break;
				}
			}
			cardPanel.add(badListPanel, BAD_LIST_VIEW);
			badListPanelCreated = true;
		}
	}
	
	private void saveLists()
	{
		if (configManager == null)
		{
			return;
		}

		// Ensure models are populated before saving
		ensureModelsPopulated();

		// Save both text and identifier for consistency with custom lists
		// Format: "text|identifier|text|identifier|..."
		StringBuilder goodBuilder = new StringBuilder();
		for (int i = 0; i < goodStepsModel.getRowCount(); i++)
		{
			if (i > 0)
			{
				goodBuilder.append("|");
			}
			String text = (String) goodStepsModel.getValueAt(i, 0);
			String identifier = goodStepsModel.getColumnCount() > 1 ? (String) goodStepsModel.getValueAt(i, 1) : null;
			if (identifier == null || identifier.isEmpty())
			{
				// Fallback to text-based identifier if not stored
				identifier = "text:" + (text != null ? text.hashCode() : 0);
			}
			goodBuilder.append(text).append("::").append(identifier);
		}

		StringBuilder badBuilder = new StringBuilder();
		for (int i = 0; i < badStepsModel.getRowCount(); i++)
		{
			if (i > 0)
			{
				badBuilder.append("|");
			}
			String text = (String) badStepsModel.getValueAt(i, 0);
			String identifier = badStepsModel.getColumnCount() > 1 ? (String) badStepsModel.getValueAt(i, 1) : null;
			if (identifier == null || identifier.isEmpty())
			{
				// Fallback to text-based identifier if not stored
				identifier = "text:" + (text != null ? text.hashCode() : 0);
			}
			badBuilder.append(text).append("::").append(identifier);
		}

		configManager.setConfiguration("cluejuggler", "goodSteps", goodBuilder.toString());
		configManager.setConfiguration("cluejuggler", "badSteps", badBuilder.toString());
	}
	

	private void logToFile(String action, String message)
	{
		// Removed file logging to prevent client thread blocking
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
		JButton textColorButton = new JButton();
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
		JButton tileColorButton = new JButton();
		tileColorButton.setPreferredSize(new Dimension(60, 30));
		tileColorButton.setMaximumSize(new Dimension(60, 30));
		tileColorButton.setBackground(selectedTileColor[0]);
		tileColorButton.setBorder(new LineBorder(Color.WHITE, 1));
		tileColorButton.setFocusPainted(false);
		tileColorButton.addActionListener(e -> {
			Color newColor = JColorChooser.showDialog(dialogPanel, "Choose Tile Color", selectedTileColor[0]);
			if (newColor != null)
			{
				// Preserve alpha if it was set, otherwise use 100
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

			if (findCustomListByName(listName) != null)
			{
				JOptionPane.showMessageDialog(this, "A list with that name already exists!", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			// Use the same text color for both list card and overlay text
			ClueList newList = new ClueList(listName, selectedTextColor[0], selectedTileColor[0], selectedTextColor[0]);
			customLists.add(newList);
			// Mark as loaded since we just modified it
			customListsLoaded = true;
			saveCustomLists();
			refreshCustomListsDisplay();
			refreshSettingsPanel();
			refreshCustomListButtons();
		}
	}

	private ClueList findCustomListByName(String name)
	{
		for (ClueList list : customLists)
		{
			if (list.getName().equals(name))
			{
				return list;
			}
		}
		return null;
	}

	private boolean customListsLoaded = false;
	private boolean customListsDisplayInitialized = false;
	
	private void initializeCustomListsDisplay()
	{
		if (customListsDisplayInitialized) {
			return;
		}
		
		// Wait for configManager to be available
		if (configManager == null) {
			// Retry later if configManager isn't ready yet
			java.awt.EventQueue.invokeLater(() -> initializeCustomListsDisplay());
			return;
		}
		
		if (listsSectionPanel == null) {
			return;
		}
		
		customListsDisplayInitialized = true;
		// Load and display custom lists
		ensureListsLoaded();
		refreshCustomListsDisplay();
		// Also refresh buttons to ensure they're created for loaded lists
		refreshCustomListButtons();
	}

	private void loadCustomLists()
	{
		if (configManager == null)
		{
			return;
		}

		// Only skip if already loaded AND we have data (not just the flag set)
		if (customListsLoaded && !customLists.isEmpty())
		{
			return;
		}

		// If flag is set but lists are empty, we might need to reload
		// This can happen if lists were cleared but flag wasn't reset
		if (customListsLoaded && customLists.isEmpty())
		{
			// Check if config actually has data
			String customListsJson = configManager.getConfiguration("cluejuggler", "customLists");
			if (customListsJson != null && !customListsJson.isEmpty() && !customListsJson.equals("[]"))
			{
				// Config has data but we don't - need to reload
				customListsLoaded = false;
			}
			else
			{
				// Config is empty, so our empty state is correct
				return;
			}
		}

		if (customListsLoaded)
		{
			return;
		}

		String customListsJson = configManager.getConfiguration("cluejuggler", "customLists");
		if (customListsJson == null || customListsJson.isEmpty())
		{
			customListsJson = "[]";
		}

		try
		{
			Type listType = new TypeToken<List<ClueList>>(){}.getType();
			List<ClueList> loaded = gson.fromJson(customListsJson, listType);
			if (loaded != null)
			{
				customLists.clear();
				customLists.addAll(loaded);
			}
			customListsLoaded = true;
		}
		catch (Exception e)
		{
			customLists.clear();
			customListsLoaded = true;
		}
	}

	private void forceLoadCustomLists()
	{
		customListsLoaded = false;
		loadCustomLists();
	}

	public void loadCustomListsOnStartup()
	{
		customListsLoaded = false;
		loadCustomLists();
		
		// Initialize custom list models lazily - only create them when needed
		// Don't populate them here to avoid blocking startup
		// Models will be populated when the list panel is first viewed
		
		// Defer UI updates to avoid blocking startup
		// UI will refresh when the panel is first accessed
	}

	private void saveCustomLists()
	{
		if (configManager == null)
		{
			return;
		}

		try
		{
			String json = gson.toJson(customLists);
			configManager.setConfiguration("cluejuggler", "customLists", json);
			// After saving, mark as loaded to prevent immediate reload
			customListsLoaded = true;
		}
		catch (Exception e)
		{
		}
	}

	private void refreshCustomListsDisplay()
	{
		if (listsSectionPanel == null)
		{
			return;
		}
		
		// Only load if not already loaded - don't reload if we just modified data
		if (!customListsLoaded)
		{
			loadCustomLists();
		}

		// Build set of current list names (fast)
		java.util.Set<String> currentListNames = new java.util.HashSet<>(customLists.size());
		for (ClueList list : customLists)
		{
			currentListNames.add(list.getName());
		}

		// Single pass: collect existing custom list cards and their spacers
		java.util.Map<String, Component> existingCards = new java.util.HashMap<>();
		java.util.Map<String, Component> existingSpacers = new java.util.HashMap<>();
		Component[] components = listsSectionPanel.getComponents();
		
		for (int i = 0; i < components.length; i++)
		{
			Component comp = components[i];
			if (comp instanceof JPanel)
			{
				JPanel panel = (JPanel) comp;
				// Quick check for label in first few components
				Component[] panelComps = panel.getComponents();
				if (panelComps.length > 0 && panelComps[0] instanceof JLabel)
				{
					String text = ((JLabel) panelComps[0]).getText();
					if (text != null && !text.equals("Good Steps") && !text.equals("Bad Steps"))
					{
						existingCards.put(text, comp);
						// Check if next component is a spacer
						if (i + 1 < components.length && components[i + 1] instanceof Box.Filler)
						{
							existingSpacers.put(text, components[i + 1]);
						}
					}
				}
			}
		}

		// Remove cards that no longer exist in customLists
		for (java.util.Map.Entry<String, Component> entry : existingCards.entrySet())
		{
			if (!currentListNames.contains(entry.getKey()))
			{
				listsSectionPanel.remove(entry.getValue());
				Component spacer = existingSpacers.get(entry.getKey());
				if (spacer != null)
				{
					listsSectionPanel.remove(spacer);
				}
			}
		}

		// Remove card panels that no longer exist
		Component[] cardComponents = cardPanel.getComponents();
		for (Component comp : cardComponents)
		{
			if (comp instanceof JPanel)
			{
				JPanel panel = (JPanel) comp;
				String name = panel.getName();
				if (name != null && name.startsWith("CUSTOM_LIST_"))
				{
					String listName = name.substring("CUSTOM_LIST_".length());
					if (!currentListNames.contains(listName))
					{
						cardPanel.remove(comp);
						customListModels.remove(listName);
					}
				}
			}
		}

		// Add missing cards (only for lists that don't have cards yet)
		for (ClueList list : customLists)
		{
			String listName = list.getName();
			if (!existingCards.containsKey(listName))
			{
				String viewKey = "CUSTOM_LIST_" + listName;
				
				// Create and add card panel if it doesn't exist
				boolean cardPanelExists = false;
				for (Component comp : cardPanel.getComponents())
				{
					if (comp instanceof JPanel && viewKey.equals(((JPanel) comp).getName()))
					{
						cardPanelExists = true;
						break;
					}
				}
				
				if (!cardPanelExists)
				{
					JPanel listPanel = createCustomListPanel(list);
					listPanel.setName(viewKey);
					cardPanel.add(listPanel, viewKey);
				}

				// Create and add list card
				JPanel card = createListCard(listName, list.getTextColor(), true, () -> cardLayout.show(cardPanel, viewKey));
				card.setAlignmentX(0.0f);
				
				int insertIndex = listsSectionPanel.getComponentCount() - 1;
				listsSectionPanel.add(card, insertIndex);
				listsSectionPanel.add(Box.createVerticalStrut(5), insertIndex + 1);
			}
		}

		// Batch UI updates
		listsSectionPanel.revalidate();
		listsSectionPanel.repaint();
		cardPanel.revalidate();
		cardPanel.repaint();
	}

	private final java.util.Map<String, DefaultTableModel> customListModels = new java.util.HashMap<>();

	private JPanel createCustomListPanel(ClueList list)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setBackground(new Color(26, 26, 26));

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		headerPanel.setBackground(new Color(26, 26, 26));

		JLabel titleLabel = new JLabel(list.getName());
		titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 18));
		titleLabel.setForeground(list.getTextColor());
		headerPanel.add(titleLabel, BorderLayout.CENTER);

		JButton backButton = new JButton("← Back");
		backButton.setBackground(new Color(60, 60, 60));
		backButton.setForeground(Color.WHITE);
		backButton.setFocusPainted(false);
		backButton.addActionListener(e -> cardLayout.show(cardPanel, MAIN_VIEW));
		headerPanel.add(backButton, BorderLayout.EAST);

		panel.add(headerPanel, BorderLayout.NORTH);

		String listKey = list.getName();
		DefaultTableModel model = customListModels.get(listKey);
		if (model == null)
		{
			model = new DefaultTableModel(new String[]{"Clue", "Identifier"}, 0);
			customListModels.put(listKey, model);
		}
		
		// Create reverse map for efficient lookup (clue -> identifier)
		// This avoids O(n*m) nested loop complexity
		java.util.Map<String, String> reverseMap = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, String> entry : list.getClueIdentifiers().entrySet())
		{
			reverseMap.put(entry.getValue(), entry.getKey());
		}
		
		// Check if we need to update the model
		boolean needsUpdate = model.getRowCount() != list.getClues().size();
		if (!needsUpdate)
		{
			// Verify all clues are in the model
			for (int i = 0; i < model.getRowCount(); i++)
			{
				String modelClue = (String) model.getValueAt(i, 0);
				if (!list.getClues().contains(modelClue))
				{
					needsUpdate = true;
					break;
				}
			}
		}
		
		if (needsUpdate)
		{
			model.setRowCount(0);
			for (String clue : list.getClues())
			{
				String identifier = reverseMap.get(clue);
				if (identifier == null)
				{
					identifier = "text:" + clue.hashCode();
				}
				model.addRow(new Object[]{clue, identifier});
			}
		}

		JTable table = new JTable(model);
		table.setBackground(new Color(20, 20, 20));
		table.setForeground(Color.WHITE);
		table.setSelectionBackground(new Color(255, 152, 0));
		table.setSelectionForeground(Color.WHITE);
		table.setGridColor(new Color(60, 60, 60));
		table.setShowGrid(true);
		table.setRowHeight(20);
		table.setTableHeader(null);
		table.setEnabled(true);
		setupTableContextMenu(table, list);

		TableColumn column = table.getColumnModel().getColumn(0);
		column.setResizable(true);
		column.setPreferredWidth(300);

		if (table.getColumnModel().getColumnCount() > 1)
		{
			TableColumn idColumn = table.getColumnModel().getColumn(1);
			idColumn.setMinWidth(0);
			idColumn.setMaxWidth(0);
			idColumn.setPreferredWidth(0);
			idColumn.setResizable(false);
		}

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(null);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panel.add(scrollPane, BorderLayout.CENTER);

		return panel;
	}

	private void setupTableContextMenu(JTable table, ClueList list)
	{
		JPopupMenu contextMenu = new JPopupMenu();

		JMenuItem removeItem = new JMenuItem("Remove");
		removeItem.addActionListener(e -> {
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0)
			{
				DefaultTableModel model = (DefaultTableModel) table.getModel();
				String clueText = (String) model.getValueAt(selectedRow, 0);
				String identifier = null;
				if (model.getColumnCount() > 1)
				{
					identifier = (String) model.getValueAt(selectedRow, 1);
				}
				model.removeRow(selectedRow);
				list.getClues().remove(clueText);
				if (identifier == null || identifier.isEmpty())
				{
					identifier = "text:" + (clueText != null ? clueText.hashCode() : 0);
				}
				if (identifier != null)
				{
					list.getClueIdentifiers().remove(identifier);
				}
				
				// Clear cache entries for this clue
				if (plugin != null && identifier != null)
				{
					plugin.clearClueStatusCacheEntry("customList:" + identifier);
					
					// Clear beginner/master position if applicable
					plugin.clearBeginnerMasterCluePositionAtCurrentLocation();
					
					// Try to get itemId and clear cache entries for it too
					final String finalIdentifier = identifier;
					if (plugin.getClient().isClientThread())
					{
						// Try to find itemId from tracked clues
						Map<Integer, ClueScroll> trackedClues = plugin.getTrackedClues();
						for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
						{
							ClueScroll clue = entry.getValue();
							if (clue != null)
							{
								String clueId = plugin.getClueIdentifier(clue);
								if (finalIdentifier.equals(clueId))
								{
									int itemId = entry.getKey();
									plugin.clearClueStatusCacheEntry("customList:" + itemId);
									break;
								}
							}
						}
					}
					else
					{
						plugin.getClientThread().invokeLater(() -> {
							Map<Integer, ClueScroll> trackedClues = plugin.getTrackedClues();
							for (Map.Entry<Integer, ClueScroll> entry : trackedClues.entrySet())
							{
								ClueScroll clue = entry.getValue();
								if (clue != null)
								{
									String clueId = plugin.getClueIdentifier(clue);
									if (finalIdentifier.equals(clueId))
									{
										int itemId = entry.getKey();
										plugin.clearClueStatusCacheEntry("customList:" + itemId);
										break;
									}
								}
							}
						});
					}
				}
				
				customListsLoaded = true; // Mark as loaded since we modified it
				saveCustomLists();
				
				String listKey = list.getName();
				DefaultTableModel cachedModel = customListModels.get(listKey);
				if (cachedModel == model)
				{
					cachedModel.removeRow(selectedRow);
				}
			}
		});

		contextMenu.add(removeItem);

		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger())
				{
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount())
					{
						table.setRowSelectionInterval(row, row);
						contextMenu.show(table, e.getX(), e.getY());
					}
				}
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger())
				{
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount())
					{
						table.setRowSelectionInterval(row, row);
						contextMenu.show(table, e.getX(), e.getY());
					}
				}
			}
		});
	}

	private void refreshCustomListButtons()
	{
		if (currentStepButtonPanel == null)
		{
			return;
		}

		// Ensure custom lists are loaded before refreshing
		ensureListsLoaded();

		Component[] components = currentStepButtonPanel.getComponents();
		List<Component> toRemove = new ArrayList<>();
		
		// Collect all custom list buttons to remove
		for (Component comp : components)
		{
			if (comp instanceof JButton)
			{
				JButton button = (JButton) comp;
				String text = button.getText();
				// Remove if it's a custom list button (exists in customListButtons map or can be found by name)
				if (text != null && (customListButtons.containsKey(text) || findCustomListByName(text) != null))
				{
					toRemove.add(comp);
				}
			}
		}

		// Remove old buttons
		for (Component comp : toRemove)
		{
			currentStepButtonPanel.remove(comp);
		}

		// Clear the map and rebuild it
		customListButtons.clear();
		
		// Create buttons for all current custom lists
		for (ClueList list : customLists)
		{
			JButton listButton = new JButton(list.getName());
			listButton.setBackground(list.getTextColor());
			listButton.setForeground(Color.WHITE);
			listButton.setFocusPainted(false);
			final ClueList finalList = list;
			listButton.addActionListener(e -> addCurrentStepToCustomList(finalList));
			customListButtons.put(list.getName(), listButton);
			currentStepButtonPanel.add(listButton);
		}
		
		if (currentDisplayedClueText != null)
		{
			updateButtonStates(currentDisplayedClueText);
		}

		currentStepButtonPanel.revalidate();
		currentStepButtonPanel.repaint();
	}

	private void addCurrentStepToCustomList(ClueList list)
	{
		// getCurrentClueTextForStorage() now uses currentDisplayedClueText first, so no need for fallback
		String text = getCurrentClueTextForStorage();
		
		if (text != null && !text.isEmpty())
		{
			String identifier = getClueIdentifier();
			if (identifier == null || identifier.isEmpty())
			{
				identifier = "text:" + text.hashCode();
			}
			
			// Remove from good list (by identifier and text as fallback)
			boolean wasInGood = removeFromListByIdentifierAndText(goodStepsModel, identifier, text);
			if (wasInGood)
			{
				goodClueIdentifiers.remove(identifier);
			}
			
			// Remove from bad list (by identifier and text as fallback)
			boolean wasInBad = removeFromListByIdentifierAndText(badStepsModel, identifier, text);
			if (wasInBad)
			{
				badClueIdentifiers.remove(identifier);
			}
			
			// Remove from other custom lists
			boolean customListModified = false;
			for (ClueList otherList : customLists)
			{
				if (otherList != list)
				{
					boolean wasInOtherList = otherList.getClueIdentifiers().containsKey(identifier) || 
						(text != null && otherList.getClues().contains(text));
					if (wasInOtherList)
					{
						otherList.getClues().remove(text);
						otherList.getClueIdentifiers().remove(identifier);
						customListModified = true;
						String otherListKey = otherList.getName();
						DefaultTableModel otherModel = customListModels.get(otherListKey);
						if (otherModel != null)
						{
							for (int i = otherModel.getRowCount() - 1; i >= 0; i--)
							{
								String rowIdentifier = (String) otherModel.getValueAt(i, 1);
								String rowText = (String) otherModel.getValueAt(i, 0);
								if ((identifier != null && identifier.equals(rowIdentifier)) || 
									(text != null && text.equals(rowText)))
								{
									otherModel.removeRow(i);
									break;
								}
							}
						}
					}
				}
			}
			if (customListModified)
			{
				customListsLoaded = true; // Mark as loaded since we modified it
				saveCustomLists();
			}
			
			// Check if already in this list - if so, don't add again and update button states
			boolean alreadyInList = list.getClueIdentifiers().containsKey(identifier) || 
				(text != null && list.getClues().contains(text));
			if (alreadyInList)
			{
				updateButtonStates(text);
				return;
			}
			
			// Now add to the list (only if not already there)
			list.getClues().add(text);
			list.getClueIdentifiers().put(identifier, text);
			// Mark as loaded since we just modified it
			customListsLoaded = true;
			saveLists();
			saveCustomLists();
			
			String listKey = list.getName();
			DefaultTableModel model = customListModels.get(listKey);
			if (model != null)
			{
				model.addRow(new Object[]{text, identifier});
			}
			else
			{
				String viewKey = "CUSTOM_LIST_" + list.getName();
				Component existingPanel = null;
				for (Component comp : cardPanel.getComponents())
				{
					if (comp instanceof JPanel)
					{
						JPanel panel = (JPanel) comp;
						if (panel.getName() != null && panel.getName().equals(viewKey))
						{
							existingPanel = comp;
							break;
						}
					}
				}
				
				if (existingPanel != null)
				{
					cardPanel.remove(existingPanel);
				}
				
				JPanel listPanel = createCustomListPanel(list);
				listPanel.setName(viewKey);
				cardPanel.add(listPanel, viewKey);
			}
			
			if (plugin != null)
			{
				plugin.ensureClueTracked();
				// Clear old cache entries first to avoid conflicts
				if (identifier != null && !identifier.isEmpty())
				{
					plugin.clearClueStatusCacheEntry(identifier); // Clear good/bad status
				}
				// Cache by identifier first (always available)
				if (identifier != null && !identifier.isEmpty())
				{
					plugin.cacheClueStatus("customList:" + identifier, list);
				}
				
				// For beginner/master clues, set position immediately (doesn't require itemId)
				net.runelite.client.plugins.cluescrolls.ClueScrollPlugin clueScrollPlugin = plugin.getClueScrollPlugin();
				boolean isBeginnerOrMaster = false;
				if (clueScrollPlugin != null)
				{
					net.runelite.client.plugins.cluescrolls.clues.ClueScroll clue = clueScrollPlugin.getClue();
					if (clue != null)
					{
						isBeginnerOrMaster = clue instanceof net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue ||
							clue instanceof net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
					}
				}
				// Also check by itemId if we're on client thread (more reliable)
				if (!isBeginnerOrMaster && plugin.getClient().isClientThread())
				{
					int itemId = plugin.getCurrentClueItemId();
					if (itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_BEGINNER ||
						itemId == net.runelite.api.gameval.ItemID.TRAIL_CLUE_MASTER ||
						itemId == 23182)
					{
						isBeginnerOrMaster = true;
					}
				}
				
				if (isBeginnerOrMaster)
				{
					String finalIdentifier = identifier;
					if (finalIdentifier == null || finalIdentifier.isEmpty())
					{
						finalIdentifier = "text:" + text.hashCode();
					}
					// Set position immediately for beginner/master clues
					// The setBeginnerMasterCluePosition will overwrite any old position
					plugin.setBeginnerMasterCluePosition(text, finalIdentifier, list);
				}
				
				// Also try to cache by itemId if available (must be on client thread)
				if (plugin.getClient().isClientThread())
				{
					int itemId = plugin.getCurrentClueItemId();
					if (itemId > 0)
					{
						// Clear old cache entries for this itemId (good/bad status)
						plugin.clearClueStatusCacheEntry(itemId);
						// Cache the custom list status for this itemId so the overlay can use it when dropped
						plugin.cacheClueStatus(itemId, 0); // 0 = unsure (not good/bad, but in custom list)
						plugin.cacheClueStatus("customList:" + itemId, list);
						// Store mapping from itemId to identifier for dropped clues (CRITICAL for overlay)
						plugin.cacheItemIdToIdentifier(itemId, identifier);
						// Position already set above for beginner/master clues
						// Just cache itemId here if available
					}
				}
				else
				{
					// Not on client thread - invoke to get itemId and cache it
					final String finalIdentifier = identifier;
					plugin.getClientThread().invokeLater(() -> {
						int itemId = plugin.getCurrentClueItemId();
						if (itemId > 0)
						{
							// Clear old cache entries for this itemId (good/bad status)
							plugin.clearClueStatusCacheEntry(itemId);
							plugin.cacheClueStatus(itemId, 0); // 0 = unsure (not good/bad, but in custom list)
							plugin.cacheClueStatus("customList:" + itemId, list);
							// Store mapping from itemId to identifier for dropped clues (CRITICAL for overlay)
							plugin.cacheItemIdToIdentifier(itemId, finalIdentifier);
							// Position already set above for beginner/master clues
							// Just cache itemId here if available
						}
					});
				}
			}
			
			updateButtonStates(text);
		}
	}
}
