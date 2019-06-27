package com.cairn.common;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A window monitor to exit when this window is closed.
 * 
 * @author Gareth Jones
 *
 */
public class BasicWindowMonitor extends WindowAdapter {
	@Override
	public void windowClosing(WindowEvent e) {
		Window w = e.getWindow();
		// w.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		w.setVisible(false);
		w.dispose();
		try {
			System.out.println("Exiting..");
			System.exit(0);
		} catch (java.lang.SecurityException ex) {
		}
	}
}
