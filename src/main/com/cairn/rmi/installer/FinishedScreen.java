package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.cairn.common.ModelException;
import com.cairn.rmi.installer.InstallerModel.InstallType;
import com.cairn.rmi.installer.InstallerModel.Os;

/**
 * Creates a Component to display that the install process has ended.
 * 
 * @author gjones
 * 
 */
class FinishedScreen {
	private final InstallerModel installerModel;

	public FinishedScreen(InstallerModel installerModel) {
		super();
		this.installerModel = installerModel;
	}

	/**
	 * Creates the finish screen
	 * 
	 * @return
	 * @throws ModelException
	 */
	public JComponent finishScreen() throws ModelException {
		String text = null;
		String configFile = installerModel.serverConfigFile();
		String logFile = installerModel.rmiLogFile().toString();
		StringBuilder extra = new StringBuilder();
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.ALL) {
			if (installerModel.isLoadNciCompounds())
				extra.append("<p>NCI test compounds have been loaded.</p>");
			if (installerModel.isBuildTestIndex())
				extra.append("<p>Domain index built on NCI test data.</p>");
		}

		if (installType == InstallType.RMI || installType == InstallType.ALL) {
			if (installerModel.getOs() == Os.WINDOWS) {
				String serviceGui = new File(installerModel.cartridgeHome(),
						"bin" + File.separatorChar + "monitorService.bat")
						.toString();
				text = "<html><p><b>Successfully installed data cartridge.</b></p><p>"
						+ "The configuration file for the cartridge is located at<br/><i>"
						+ configFile
						+ "</i></p><p>The rmi server has been installed as a service.<br />"
						+ "You can control the service and edit service settings<br />"
						+ "(for example the memory assigned to Java) using this command:<br/><i>"
						+ serviceGui
						+ "</i><br />(It is already running minimized in the windows taskbar.)</p>"
						+ "<p>Cartridge log messages may be found at<br/><i>"
						+ logFile + "</i></p>" + extra + "</html>";
			} else {
				String command = installerModel.getLinuxCommandFile()
						.toString();
				text = "<html><p><b>Successfully installed data cartridge.</b></p><p>"
						+ "The configuration file for the cartridge is located at<br/><i>"
						+ configFile
						+ "</i></p><p>The cartridge is manually started using the script located at<br/><i>"
						+ command
						+ "</i><br/>(it is currently running)</p>"
						+ "<p>Cartridge log messages may be found at<br/><i>"
						+ logFile + "</i></p>" + extra + "</html>";
			}
		}

		if (installType == InstallType.ORACLE) {
			text = "<html><p><b>Successfully installed Oracle components.</b></p><p>"
					+ "You need to install an RMI server before you can use the cartridge.</p>"
					+ extra + "</html>";

		}

		JPanel panel = new JPanel(new BorderLayout());
		JPanel panel2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JEditorPane editorPane = new JEditorPane();
		editorPane.setEditable(false);
		editorPane.setOpaque(false);
		editorPane.setContentType("text/html");
		editorPane.setText(text);
		panel2.add(new JScrollPane(editorPane));
		panel.add(panel2, BorderLayout.CENTER);
		final JButton exit = new JButton("Finish");
		panel.add(exit, BorderLayout.SOUTH);
		exit.addActionListener(arg0 -> {
            System.out.println("Exiting..");
            System.exit(1);
        });

		return panel;
	}
}
