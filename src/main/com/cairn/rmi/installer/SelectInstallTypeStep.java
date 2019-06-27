package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import com.cairn.common.ModelException;
import com.cairn.common.UtilLayout;
import com.cairn.rmi.installer.InstallerModel.InstallType;

/**
 * Selects the current install type.
 * 
 * @author gjones
 * 
 */
public class SelectInstallTypeStep extends InstallStep {

	@Override
	public boolean preDialogActions() throws ModelException {
		return true;
	}

	@Override
	public JComponent dialog() throws ModelException {

		final JPanel dialog = new JPanel();
		dialog.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(new EtchedBorder(),
				"Choose Install Type"));

		dialog.add(panel, BorderLayout.CENTER);
		UtilLayout layout = new UtilLayout(panel);

		final JRadioButton bothButton = new JRadioButton(
				"Install both Oracle components and RMI server", true);
		final JRadioButton oracleButton = new JRadioButton(
				"Install Oracle components only");
		final JRadioButton rmiButton = new JRadioButton(
				"Install RMI server only");

		int y = 0;
		layout.add(bothButton, 0, y++);
		layout.add(oracleButton, 0, y++);
		layout.add(rmiButton, 0, y++);

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(bothButton);
		buttonGroup.add(oracleButton);
		buttonGroup.add(rmiButton);

		JButton next = new JButton("Next Step");
		next.addActionListener(arg0 -> {
            if (bothButton.isSelected())
                installerModel.setInstallType(InstallType.ALL);
            if (oracleButton.isSelected())
                installerModel.setInstallType(InstallType.ORACLE);
            if (rmiButton.isSelected())
                installerModel.setInstallType(InstallType.RMI);
            dialogFinished();
        });
		dialog.add(next, BorderLayout.SOUTH);

		JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
		wrapper.add(dialog);
		return wrapper;
	}

	@Override
	public boolean postDialogActions() throws ModelException {
		return true;
	}

}
