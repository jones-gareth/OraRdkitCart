package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.apache.commons.lang3.StringUtils;

import com.cairn.common.ModelException;
import com.cairn.common.SqlUtil;
import com.cairn.common.UtilLayout;
import com.cairn.rmi.installer.InstallerModel.InstallType;

public class CreateCartridgeUserStep extends InstallStep {
	private volatile boolean userExists;
	private volatile boolean setDefaultRmiHost = false;
	private volatile List<String> userTablespaces, tmpTablespaces;

	@Override
	public boolean preDialogActions() throws ModelException {

		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.ALL || installType == InstallType.ORACLE) {
			Connection connection = null;

			try {
				connection = installerModel.getAdminConnection();
				userTablespaces = installerModel.retrieveTablespaces(connection);
				tmpTablespaces = installerModel.retrieveTmpTablespaces(connection);
				userExists = installerModel.checkUserPresent(connection, "C$CSCHEM1");
			} finally {
				SqlUtil.closeConnection(connection);
			}

		} else {
			userTablespaces = Collections.emptyList();
			tmpTablespaces = Collections.emptyList();
		}

		return true;
	}

	@Override
	public JComponent dialog() throws ModelException {
		final InstallType installType = installerModel.getInstallType();

		final JComboBox<String> userTablespaceComboBox = new JComboBox<>(
				new Vector<>(userTablespaces));
		final JComboBox<String> tmpTablespaceComboBox = new JComboBox<>(
				new Vector<>(tmpTablespaces));
		JLabel label;
		final JCheckBox deleteUserCheckBox = new JCheckBox(
				"Remove existing cartridge user and tester ?");
		deleteUserCheckBox.setSelected(true);

		final JPanel dialog = new JPanel();
		dialog.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(new EtchedBorder(),
				"Cartridge user and tester options"));

		dialog.add(panel, BorderLayout.CENTER);
		UtilLayout layout = new UtilLayout(panel);
		int y = 0;

		if (installType == InstallType.ALL || installType == InstallType.ORACLE) {
			if (StringUtils.isNotEmpty(installerModel.getUserTablespace()))
				userTablespaceComboBox
						.setSelectedItem(installerModel.getUserTablespace());
			else
				userTablespaceComboBox.setSelectedItem("USERS");
			label = new JLabel("Cartridge user tablespace");
			layout.add(label, 0, y);
			layout.add(userTablespaceComboBox, 1, y);
			y++;

			if (StringUtils.isNotEmpty(installerModel.getTmpTablespace()))
				tmpTablespaceComboBox.setSelectedItem(installerModel.getTmpTablespace());
			else
				tmpTablespaceComboBox.setSelectedItem("TEMP");
			label = new JLabel("Cartridge temporary tablespace");
			layout.add(label, 0, y);
			layout.add(tmpTablespaceComboBox, 1, y);
			y++;

			if (userExists) {
				layout.add(deleteUserCheckBox, 0, y, 2, 1);
				y++;
			}
		}

		final JPasswordField passwordField = new JPasswordField(20);
		label = new JLabel("Cartridge password");
		layout.add(label, 0, y);
		layout.add(passwordField, 1, y);
		y++;

		final JPasswordField confirmPasswordField = new JPasswordField(20);

		if (installType == InstallType.ALL || installType == InstallType.ORACLE) {

			label = new JLabel("Confirm cartridge password");
			layout.add(label, 0, y);
			layout.add(confirmPasswordField, 1, y);
			y++;
		}

		final JCheckBox setRmiHostnameCheckBox = new JCheckBox(
				"Make this RMI host the default on the server");
		if (installType == InstallType.RMI) {
			layout.add(setRmiHostnameCheckBox, 0, y, 2, 1);
			y++;
		}

		final JTextField rmiHostEntry = new JTextField(
				installerModel.getDefaultRmiHost(), 40);
		label = new JLabel("RMI Hostname");
		layout.add(label, 0, y);
		layout.add(rmiHostEntry, 1, y);
		y++;

		JButton next = new JButton("Next Step");
		next.addActionListener(arg0 -> {
            if (installType == InstallType.ALL || installType == InstallType.ORACLE) {
                if (userExists && !deleteUserCheckBox.isSelected()) {
                    JOptionPane.showMessageDialog(dialog,
                            "You need to delete the existing user\n"
                                    + "to continue the install.", "Installer Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            String passwd = new String(passwordField.getPassword());
            if (installType == InstallType.ALL || installType == InstallType.ORACLE) {
                String confirmPasswd = new String(confirmPasswordField.getPassword());
                if (StringUtils.isBlank(passwd)
                        || !StringUtils.equals(passwd, confirmPasswd)) {
                    JOptionPane.showMessageDialog(dialog,
                            "Passwords do not match (or are empty)",
                            "Installer Error", JOptionPane.ERROR_MESSAGE);

                    return;
                }
            }
            installerModel.setRmiHost(rmiHostEntry.getText());
            installerModel.setCartridgePasswd(passwd);

            if (installType == InstallType.ALL || installType == InstallType.ORACLE) {
                installerModel.setTmpTablespace(tmpTablespaceComboBox
                        .getSelectedItem().toString());
                installerModel.setUserTablespace(userTablespaceComboBox
                        .getSelectedItem().toString());
            }

            if (installType == InstallType.RMI)
                setDefaultRmiHost = setRmiHostnameCheckBox.isSelected();

            dialogFinished();
        });

		dialog.add(next, BorderLayout.SOUTH);

		JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
		wrapper.add(dialog);
		return wrapper;
	}

	@Override
	public boolean postDialogActions() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.ORACLE || installType == InstallType.ALL) {
			if (!installerModel.createUsers())
				return false;
			if (!installerModel.loadCartridge())
				return false;
			if (!installerModel.grantCartridge("CSCHEM1_TEST"))
				return false;
		}

		if (installType == InstallType.RMI) {
			if (!installerModel.testCartridgeConnection())
				return false;
			if (setDefaultRmiHost) {
				if (!installerModel.updateDefaultRmiHost())
					return false;
			}
		}

		if (installType == InstallType.ORACLE || installType == InstallType.ALL) {
			if (!installerModel.loadJarFiles())
				return false;
		}

		return true;
	}

}
