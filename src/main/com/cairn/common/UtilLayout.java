package com.cairn.common;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Utility class for helping with GridBagLayout
 * 
 * @author Gareth Jones
 * 
 */
public class UtilLayout {
	final Container container;
	public final GridBagLayout gb;
	public final GridBagConstraints c;

	public UtilLayout(Container cn) {
		container = cn;
		gb = new GridBagLayout();
		c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);
		container.setLayout(gb);
		c.anchor = GridBagConstraints.NORTHWEST;
		c.weightx = 50;
		c.weighty = 50;
		c.gridwidth = 1;
		c.gridheight = 1;
	}

	/**
	 * Add a component at coordinates x, y.
	 * 
	 * @param component
	 * @param x
	 * @param y
	 */
	public void add(Component component, int x, int y) {
		c.gridx = x;
		c.gridy = y;
		gb.setConstraints(component, c);
		container.add(component);
	}

	/**
	 * Add a component at coordinates x, y. Allow for cell spanning
	 * 
	 * @param component
	 * @param x
	 * @param y
	 * @param xSpan
	 * @param ySpan
	 */
	public void add(Component component, int x, int y, int xSpan, int ySpan) {
		c.gridx = x;
		c.gridy = y;
		c.gridwidth = xSpan;
		c.gridheight = ySpan;
		gb.setConstraints(component, c);
		container.add(component);
		c.gridwidth = 1;
		c.gridheight = 1;
	}
}
