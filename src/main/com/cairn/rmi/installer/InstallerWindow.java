package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import com.cairn.common.Util;
import com.cairn.common.BasicWindowMonitor;

/**
 * Overall window for the installer.
 * 
 * @author gjones
 * 
 */
public class InstallerWindow implements InstallerModel.InstallerMessageListener {
	private volatile JFrame frame;
	private volatile BorderLayout layout;
	private volatile JPanel mainPanel;
	private volatile JTextArea messagePane;
	private volatile JComponent dialog;
	private volatile JProgressBar progressBar;

	public InstallerWindow() {
		createWindow();
	}

	/**
	 * Creates the main install window
	 */
	private void createWindow() {
		SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Install Oracle Cartridge");
            frame.addWindowListener(new BasicWindowMonitor());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            int width = Util.isHiDpi() ? 1600 : 800;
            int height = Util.isHiDpi() ? 1400 : 700;
            Dimension size = new Dimension(width, height);
            frame.setSize(size);
            frame.setPreferredSize(size);

            frame.getContentPane().setLayout(new BorderLayout());
            mainPanel = new JPanel();
            layout = new BorderLayout();
            mainPanel.setLayout(layout);
            frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

            messagePane = new JTextArea();
            messagePane.setRows(10);
            mainPanel.add(new JScrollPane(messagePane), BorderLayout.SOUTH);
            frame.pack();

            progressBar = new JProgressBar(0, 100);
            progressBar.setForeground(Color.ORANGE);
            progressBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
            mainPanel.add(progressBar, BorderLayout.NORTH);

            frame.setVisible(true);
        });
	}

	/**
	 * Set percent complete in the progress bar
	 * 
	 * @param percent
	 */
	public void setProgress(final int percent) {
		SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
	}

	private volatile Cursor cursor;

	/**
	 * Show the busy cursor in the main window.
	 */
	public void setBusy() {
		SwingUtilities.invokeLater(() -> {
            cursor = mainPanel.getCursor();
            mainPanel.setCursor(Cursor
                    .getPredefinedCursor(Cursor.WAIT_CURSOR));
        });
	}

	/**
	 * Restore the cursor.
	 */
	public void clearBusy() {
		SwingUtilities.invokeLater(() -> mainPanel.setCursor(cursor));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.cairn.rmi.installer.InstallerModel.InstallerMessageListener#
	 * installerMessage(java.lang.String)
	 */
	public void installerMessage(String message) {

		if (!message.endsWith("\n"))
			message = message + "\n";
		final String msg = message;
		SwingUtilities.invokeLater(() -> {
            messagePane.append(msg);
            messagePane.setCaretPosition(messagePane.getDocument()
                    .getLength());
            messagePane.revalidate();
        });
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.cairn.rmi.installer.InstallerModel.InstallerMessageListener#
	 * installerError(java.lang.String, boolean)
	 */
	public void installerError(final String message, final boolean fatal) {
		SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message,
                    "Installer Error", JOptionPane.ERROR_MESSAGE);
            if (fatal) {
                // throw new RuntimeException("Fatal installer error");
                System.exit(0);
            }
        });
	}

	/**
	 * Sets the main dialog panel
	 * 
	 * @param dialog
	 */
	public void setDialog(final JComponent dialog) {
		// this should always be called from the AWT event loop- but just in
		// case:
		SwingUtilities.invokeLater(() -> {
            if (InstallerWindow.this.dialog != null)
                mainPanel.remove(InstallerWindow.this.dialog);
            mainPanel.add(dialog, BorderLayout.CENTER);
            mainPanel.revalidate();
            InstallerWindow.this.dialog = dialog;
        });
	}

	/**
	 * Removes the main dialog panel.
	 */
	public void removeDialog() {
		// if (this.dialog != null)
		// mainPanel.remove(this.dialog);
		// this.dialog = null;
		// mainPanel.revalidate();

		// it seems to blank the dialog its not sufficient to remove the dialog-
		// you need to replace the dialog with a blank panel
		SwingUtilities.invokeLater(() -> setDialog(new JPanel()));
	}

}
