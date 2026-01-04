package com.cluejuggler.ui.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

public class ClueTableComponent extends JPanel
{
	private final JTable table;
	private final DefaultTableModel model;
	private BiConsumer<String, String> onDeleteClue;
	private BiConsumer<String, String> onMoveClue;
	
	public ClueTableComponent()
	{
		setLayout(new BorderLayout());
		setBackground(new Color(26, 26, 26));
		
		model = new DefaultTableModel(new String[]{"Clue", "Identifier"}, 0);
		table = new JTable(model);
		table.setBackground(new Color(20, 20, 20));
		table.setForeground(Color.WHITE);
		table.setGridColor(new Color(40, 40, 40));
		table.getTableHeader().setBackground(new Color(30, 30, 30));
		table.getTableHeader().setForeground(Color.WHITE);
		table.setRowHeight(25);
		
		TableColumn identifierColumn = table.getColumnModel().getColumn(1);
		identifierColumn.setPreferredWidth(0);
		identifierColumn.setMinWidth(0);
		identifierColumn.setMaxWidth(0);
		identifierColumn.setWidth(0);
		
		setupContextMenu();
		
		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBackground(new Color(26, 26, 26));
		scrollPane.getViewport().setBackground(new Color(20, 20, 20));
		add(scrollPane, BorderLayout.CENTER);
	}
	
	private void setupContextMenu()
	{
		JPopupMenu contextMenu = new JPopupMenu();
		
		JMenuItem deleteItem = new JMenuItem("Delete");
		deleteItem.addActionListener(e -> {
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0)
			{
				String text = (String) model.getValueAt(selectedRow, 0);
				String identifier = (String) model.getValueAt(selectedRow, 1);
				if (onDeleteClue != null)
				{
					onDeleteClue.accept(text, identifier);
				}
				model.removeRow(selectedRow);
			}
		});
		contextMenu.add(deleteItem);
		
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showPopup(e);
				}
			}
			
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showPopup(e);
				}
			}
			
			private void showPopup(MouseEvent e)
			{
				int row = table.rowAtPoint(e.getPoint());
				if (row >= 0 && row < table.getRowCount())
				{
					table.setRowSelectionInterval(row, row);
					contextMenu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
	}
	
	public void addRow(String text, String identifier)
	{
		model.addRow(new Object[]{text, identifier});
	}
	
	public void removeRow(int index)
	{
		if (index >= 0 && index < model.getRowCount())
		{
			model.removeRow(index);
		}
	}
	
	public void clear()
	{
		model.setRowCount(0);
	}
	
	public int getRowCount()
	{
		return model.getRowCount();
	}
	
	public String getTextAt(int row)
	{
		return (String) model.getValueAt(row, 0);
	}
	
	public String getIdentifierAt(int row)
	{
		return (String) model.getValueAt(row, 1);
	}
	
	public DefaultTableModel getModel()
	{
		return model;
	}
	
	public JTable getTable()
	{
		return table;
	}
	
	public void setOnDeleteClue(BiConsumer<String, String> onDeleteClue)
	{
		this.onDeleteClue = onDeleteClue;
	}
	
	public void setOnMoveClue(BiConsumer<String, String> onMoveClue)
	{
		this.onMoveClue = onMoveClue;
	}
}

