package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import com.cairn.common.ModelException;
import com.cairn.common.UtilLayout;
import com.cairn.rmi.installer.InstallerModel.InstallType;

/**
 * A dialog to load the NCI compound database and build a domain index on the
 * test data.
 * 
 * @author gjones
 * 
 */
public class LoadNCIStep extends InstallStep {
	private volatile boolean loadNCI = false, buildIndex = false;

	@Override
	public boolean preDialogActions() throws ModelException {
		return true;
	}

	@Override
	public JComponent dialog() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType != InstallType.ALL)
			return null;

		final JPanel dialog = new JPanel();
		dialog.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(new EtchedBorder(),
				"Test user options"));

		dialog.add(panel, BorderLayout.CENTER);
		UtilLayout layout = new UtilLayout(panel);

		int y = 0;
		final JCheckBox loadNCICheckBox = new JCheckBox("Load NCI test data",
				installerModel.isLoadNciCompounds());
		layout.add(loadNCICheckBox, 0, y++);
		final JCheckBox buildIndexCheckBox = new JCheckBox(
				"Build Index on NCI test data",
				installerModel.isBuildTestIndex());
		buildIndexCheckBox.setEnabled(buildIndexCheckBox.isSelected());
		layout.add(buildIndexCheckBox, 0, y++);
		loadNCICheckBox.addActionListener(e -> {
            if (loadNCICheckBox.isSelected()) {
                buildIndexCheckBox.setEnabled(true);
            } else {
                buildIndexCheckBox.setEnabled(false);
                buildIndexCheckBox.setSelected(false);
            }
        });

		JButton next = new JButton("Next Step");
		next.addActionListener(arg0 -> {
            loadNCI = loadNCICheckBox.isSelected();
            buildIndex = buildIndexCheckBox.isSelected();
            dialogFinished();
        });
		dialog.add(next, BorderLayout.SOUTH);

		JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
		wrapper.add(dialog);
		return wrapper;
	}

	@Override
	public boolean postDialogActions() throws ModelException {
		if (loadNCI) {
			if (!installerModel.loadNCI())
				return false;
			if (buildIndex)
				if (!installerModel.buildTestIndex())
					return false;
		}

		return true;
	}
}
