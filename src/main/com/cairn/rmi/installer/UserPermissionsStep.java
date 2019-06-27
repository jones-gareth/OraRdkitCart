package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.apache.commons.collections.CollectionUtils;

import com.cairn.common.ModelException;
import com.cairn.common.SqlUtil;
import com.cairn.common.UtilLayout;
import com.cairn.rmi.installer.InstallerModel.InstallType;

public class UserPermissionsStep extends InstallStep {
	private volatile List<String> allUsers;

	@Override
	public boolean preDialogActions() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.RMI)
			return true;

		Connection connection = installerModel.getAdminConnection();
		allUsers = installerModel.availableOracleUsers(connection);
		SqlUtil.closeConnection(connection);

		return true;
	}

	@Override
	public JComponent dialog() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.RMI)
			return null;

		final JPanel dialog = new JPanel();
		dialog.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(new EtchedBorder(),
				"Allow these users to use the cartridge"));

		dialog.add(new JScrollPane(panel), BorderLayout.CENTER);
		UtilLayout layout = new UtilLayout(panel);
		int y = 0;

		final List<JCheckBox> jCheckBoxes = new ArrayList<>();
		List<String> extraUsers = installerModel.getExtraUsers();
		for (String user : allUsers) {
			JCheckBox jCheckBox = new JCheckBox(user, false);
			jCheckBoxes.add(jCheckBox);
			if (CollectionUtils.isNotEmpty(extraUsers)
					&& extraUsers.contains(user))
				jCheckBox.setSelected(true);
			layout.add(jCheckBox, 0, y++);
		}

		JButton next = new JButton("Next Step");
		next.addActionListener(arg0 -> {
            List<String> extraUsers1 = new ArrayList<>();
            for (JCheckBox jCheckBox : jCheckBoxes)
                if (jCheckBox.isSelected())
                    extraUsers1.add(jCheckBox.getText());
            installerModel.setExtraUsers(extraUsers1);
            dialogFinished();
        });

		dialog.add(next, BorderLayout.SOUTH);

		// JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
		// wrapper.add(dialog);
		return dialog;

	}

	@Override
	public boolean postDialogActions() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.RMI)
			return true;
		if (!installerModel.grantExtraUsers())
			return false;

		return true;
	}

}
