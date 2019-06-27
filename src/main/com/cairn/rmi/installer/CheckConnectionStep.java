package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.Connection;
import java.util.List;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.cairn.common.ModelException;
import com.cairn.common.SqlUtil;
import com.cairn.common.UtilLayout;
import com.cairn.rmi.installer.InstallerModel.InstallType;

public class CheckConnectionStep extends InstallStep {

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.installer.InstallStep#preDialogActions()
     */
    @Override
    public boolean preDialogActions() throws ModelException {
        InstallType installType = installerModel.getInstallType();

        if (installType == InstallType.RMI || installType == InstallType.ALL) {
            // OS does not matter for Oracle only install
            if (!installerModel.checkOs())
                return false;
        }
        if (!installerModel.checkJavaVersion())
            return false;
        if (!installerModel.checkCartridgeHome())
            return false;

        if (installType == InstallType.ORACLE || installType == InstallType.ALL) {
            if (!installerModel.checkOracleHome())
                return false;
        }

        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.installer.InstallStep#dialog()
     */
    @Override
    public JComponent dialog() throws ModelException {
        final InstallType installType = installerModel.getInstallType();

        List<String> databaseGuesses = null;
        if (installType == InstallType.ALL || installType == InstallType.ORACLE)
            databaseGuesses = installerModel.possibleDatabases();

        final JPanel dialog = new JPanel();
        dialog.setLayout(new BorderLayout());
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(new EtchedBorder(), "Oracle Connection Options"));

        dialog.add(panel, BorderLayout.CENTER);
        UtilLayout layout = new UtilLayout(panel);
        int y = 0;

        final JTextField databaseEntry = new JTextField(20);
        JLabel label;

        if (installType == InstallType.ALL || installType == InstallType.ORACLE) {
            if (CollectionUtils.isNotEmpty(databaseGuesses)) {
                final JComboBox<String> comboBox = new JComboBox<>(new Vector<>(
                        databaseGuesses));
                comboBox.addActionListener(e -> databaseEntry.setText((String) comboBox.getSelectedItem()));
                label = new JLabel("Database Guess");
                layout.add(label, 0, y);
                layout.add(comboBox, 1, y);
                y++;
            }
        }

        String databaseGuess = "";
        if (CollectionUtils.isNotEmpty(databaseGuesses))
            databaseGuess = databaseGuesses.get(0);
        if (StringUtils.isNotEmpty(installerModel.getJdbcDatabase()))
            databaseEntry.setText(installerModel.getJdbcDatabase());
        else
            databaseEntry.setText(databaseGuess);

        final JTextField portEntry = new JTextField(String.valueOf(installerModel
                .getPortNo()), 20);

        label = new JLabel("JDBC database name (Oracle SID)");
        layout.add(label, 0, y);
        layout.add(databaseEntry, 1, y);
        y++;

        label = new JLabel("JDBC Port No");
        layout.add(label, 0, y);
        layout.add(portEntry, 1, y);
        y++;

        final JTextField hostEntry = new JTextField(installerModel.getHost(), 30);
        label = new JLabel("JDBC Hostname");
        layout.add(label, 0, y);
        layout.add(hostEntry, 1, y);
        y++;

        final JPasswordField passwordEntry = new JPasswordField(20);
        label = new JLabel("Oracle Sys Password");
        if (StringUtils.isNotEmpty(installerModel.getAdminPasswd()))
            passwordEntry.setText(installerModel.getAdminPasswd());
        layout.add(label, 0, y);
        layout.add(passwordEntry, 1, y);
        y++;

        JButton next = new JButton("Next Step");
        next.addActionListener((arg) -> {
            String portNoStr = portEntry.getText();
            int portNo;
            try {
                portNo = Integer.parseInt(portNoStr);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(dialog, "JDBC port must be an integer",
                        "Port No Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            installerModel.setPortNo(portNo);
            installerModel.setJdbcDatabase(databaseEntry.getText());
            installerModel.setHost(hostEntry.getText());
            installerModel.setAdminPasswd(new String(passwordEntry.getPassword()));

            dialogFinished();
        });
        dialog.add(next, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrapper.add(dialog);
        return wrapper;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.installer.InstallStep#postDialogActions()
     */
    @Override
    public boolean postDialogActions() throws ModelException {

        if (!installerModel.testJDBCConnection())
            return false;
        Connection connection = null;
        try {
            connection = installerModel.getAdminConnection();
            if (!installerModel.checkOracleVersion(connection))
                return false;
        } finally {
            SqlUtil.closeConnection(connection);
        }

        return true;
    }

}
