/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 09-Oct-2007
 */
package uk.me.parabola.mkgmap.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.*;
import javax.swing.table.TableColumn;

/**
 * This is the main file list of input files that will be converted to the
 * Garmin .img format.
 *
 * @author Steve Ratcliffe
 */
@SuppressWarnings({"UnusedDeclaration"})
public class MainFileList {
	//private static final Logger log = Logger.getLogger(MainFileList.class);

	private JPanel panel1;
	private JTable inputFiles;
	private JButton addButton;
	private JButton removeButton;

	private final FileModel files = new FileModel();

	/**
	 * Build up the file input part of the interface.
	 */
	public MainFileList() {

		initTable();

		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("hello"); // debug
				JFileChooser fc = new JFileChooser();
				int val = fc.showOpenDialog(panel1);
				if (val == JFileChooser.APPROVE_OPTION) {
					File file = fc.getSelectedFile();

					System.out.println("file added is" + file); //debug
					files.addFile(file);
				}
			}
		});
	}

	public JPanel getRoot() {
		return panel1;
	}

	public JTable getInputTable() {
		return inputFiles;
	}

	public List<InputFile> getInputFiles() {
		return files.getInputFiles();
	}

	private void initTable() {
		inputFiles.setModel(files);
		TableColumn col = inputFiles.getColumnModel().getColumn(0);
		col.setPreferredWidth(40);
		col.setMaxWidth(50);

		col = inputFiles.getColumnModel().getColumn(2);
		col.setPreferredWidth(200);
	}


	{
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
		$$$setupUI$$$();
	}

	/**
	 * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT
	 * edit this method OR call it in your code!
	 *
	 * @noinspection ALL
	 */
	private void $$$setupUI$$$() {
		panel1 = new JPanel();
		panel1.setLayout(new GridBagLayout());
		panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "Files to convert to Garmin format"));
		final JScrollPane scrollPane1 = new JScrollPane();
		scrollPane1.setVerticalScrollBarPolicy(22);
		GridBagConstraints gbc;
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 7;
		gbc.gridheight = 4;
		gbc.weightx = 0.5;
		gbc.weighty = 0.1;
		gbc.fill = GridBagConstraints.BOTH;
		panel1.add(scrollPane1, gbc);
		inputFiles = new JTable();
		inputFiles.setIntercellSpacing(new Dimension(1, 4));
		inputFiles.setPreferredScrollableViewportSize(new Dimension(400, 200));
		inputFiles.setShowVerticalLines(false);
		inputFiles.putClientProperty("Table.isFileList", Boolean.FALSE);
		scrollPane1.setViewportView(inputFiles);
		final JLabel label1 = new JLabel();
		this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("uk/me/parabola/mkgmap/gui/MainFileList").getString("label.create.map"));
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 3;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(0, 0, 10, 0);
		panel1.add(label1, gbc);
		final JPanel spacer1 = new JPanel();
		gbc = new GridBagConstraints();
		gbc.gridx = 7;
		gbc.gridy = 4;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel1.add(spacer1, gbc);
		addButton = new JButton();
		this.$$$loadButtonText$$$(addButton, ResourceBundle.getBundle("uk/me/parabola/mkgmap/gui/MainFileList").getString("button.add.file"));
		gbc = new GridBagConstraints();
		gbc.gridx = 8;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel1.add(addButton, gbc);
		final JPanel spacer2 = new JPanel();
		gbc = new GridBagConstraints();
		gbc.gridx = 8;
		gbc.gridy = 3;
		gbc.fill = GridBagConstraints.VERTICAL;
		panel1.add(spacer2, gbc);
		removeButton = new JButton();
		removeButton.setText("Remove");
		gbc = new GridBagConstraints();
		gbc.gridx = 8;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(10, 0, 0, 0);
		panel1.add(removeButton, gbc);
		label1.setLabelFor(scrollPane1);
	}

	/**
	 * @noinspection ALL
	 */
	private void $$$loadLabelText$$$(JLabel component, String text) {
		StringBuffer result = new StringBuffer();
		boolean haveMnemonic = false;
		char mnemonic = '\0';
		int mnemonicIndex = -1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '&') {
				i++;
				if (i == text.length()) break;
				if (!haveMnemonic && text.charAt(i) != '&') {
					haveMnemonic = true;
					mnemonic = text.charAt(i);
					mnemonicIndex = result.length();
				}
			}
			result.append(text.charAt(i));
		}
		component.setText(result.toString());
		if (haveMnemonic) {
			component.setDisplayedMnemonic(mnemonic);
			component.setDisplayedMnemonicIndex(mnemonicIndex);
		}
	}

	/**
	 * @noinspection ALL
	 */
	private void $$$loadButtonText$$$(AbstractButton component, String text) {
		StringBuffer result = new StringBuffer();
		boolean haveMnemonic = false;
		char mnemonic = '\0';
		int mnemonicIndex = -1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '&') {
				i++;
				if (i == text.length()) break;
				if (!haveMnemonic && text.charAt(i) != '&') {
					haveMnemonic = true;
					mnemonic = text.charAt(i);
					mnemonicIndex = result.length();
				}
			}
			result.append(text.charAt(i));
		}
		component.setText(result.toString());
		if (haveMnemonic) {
			component.setMnemonic(mnemonic);
			component.setDisplayedMnemonicIndex(mnemonicIndex);
		}
	}

	/**
	 * @noinspection ALL
	 */
	public JComponent $$$getRootComponent$$$() {
		return panel1;
	}
}
