package com.cairn.rmi.installer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.sql.Connection;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import com.cairn.common.ModelException;
import com.cairn.common.SqlUtil;
import com.cairn.common.UtilLayout;
import com.cairn.rmi.installer.InstallerModel.Arch;
import com.cairn.rmi.installer.InstallerModel.InstallType;
import com.cairn.rmi.installer.InstallerModel.Os;

public class ConfigureRmiStep extends InstallStep {
	private volatile int maxMemoryMB;
	private volatile int availableMemoryMB;

	@Override
	public boolean preDialogActions() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.ORACLE)
			return true;

		maxMemoryMB = installerModel.availableMemoryMb();
		availableMemoryMB = maxMemoryMB;
		// 1300M seems to be the max heap size on win32
		if (installerModel.getVmArch() == Arch.ARCH32 && maxMemoryMB > 1300)
			availableMemoryMB = 1300;
		return true;
	}

	@Override
	public JComponent dialog() throws ModelException {
		InstallType installType = installerModel.getInstallType();

		if (installType == InstallType.ORACLE)
			return null;

		JPanel dialog = new JPanel();
		dialog.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(new EtchedBorder(),
				"Configure RMI Server"));
		UtilLayout layout = new UtilLayout(panel);
		dialog.add(BorderLayout.CENTER, panel);
		int y = 0;

		JLabel label = new JLabel("No RMI task threads");
		int nProcessors = Runtime.getRuntime().availableProcessors();
		int max = nProcessors * 2 + 2;
		if (max > 64)
			max = 64;
		final JSlider nThreadsSlider = createThreadsSlider(max,
				installerModel.getnThreads());
		Dimension size = nThreadsSlider.getPreferredSize();
		size.width = 400;
		nThreadsSlider.setPreferredSize(size);

		layout.add(label, 0, y);
		layout.add(nThreadsSlider, 1, y++);
		final JCheckBox useCacheCb = new JCheckBox("Use molecule cache",
				installerModel.isUseCache());
		final JTextField cacheSizeField = new JTextField("500000", 20);
		if (installerModel.getCacheSize() > 0)
			cacheSizeField
					.setText(String.valueOf(installerModel.getCacheSize()));
		cacheSizeField.setEnabled(useCacheCb.isSelected());
		useCacheCb.addActionListener(arg0 -> cacheSizeField.setEnabled(useCacheCb.isSelected()));

		layout.add(useCacheCb, 0, y++, 2, 1);
		label = new JLabel("Molecule Cache Size");
		layout.add(label, 0, y);
		layout.add(cacheSizeField, 1, y++);
		label = new JLabel("Java VM size (MB)");
		layout.add(label, 0, y);
		int memDefault = maxMemoryMB > 1024 ? (maxMemoryMB / 2 > availableMemoryMB ? availableMemoryMB
				: maxMemoryMB / 2)
				: availableMemoryMB;
		// turn off 2GB memory defatult
		// if (memDefault > 2048)
		// memDefault = 2048;
		final JSlider memorySlider = new JSlider(256, availableMemoryMB,
				memDefault);
		if (installerModel.getJavaVmMemory() > 0)
			memorySlider.setValue(installerModel.getJavaVmMemory());
		size = memorySlider.getPreferredSize();
		size.width = 400;
		memorySlider.setPreferredSize(size);
		final JLabel memoryLabel = new JLabel();
		memoryLabel.setText(memorySlider.getValue() + " MB");
		memorySlider.setMinorTickSpacing(1024);
		memorySlider.setPaintTicks(true);
		memorySlider.addChangeListener(e -> memoryLabel.setText(memorySlider.getValue() + " MB"));
		layout.add(memorySlider, 1, y++);
		layout.add(memoryLabel, 1, y++);

		JCheckBox useSearchPoolCb = null;
		JSlider nSearchThreadsSlider = null;

		if (nProcessors >= 2) {
			useSearchPoolCb = new JCheckBox("Use substructure search pool",
					installerModel.isUseSubstructureSearchPool());
			layout.add(useSearchPoolCb, 0, y++, 2, 1);

			label = new JLabel("No subsearch threads");
			max = nProcessors;
			if (max > 64)
				max = 64;
			nSearchThreadsSlider = createThreadsSlider(max,
					installerModel.getnSubsearchThreads());
			size = nSearchThreadsSlider.getPreferredSize();
			size.width = 400;
			nSearchThreadsSlider.setPreferredSize(size);

			layout.add(label, 0, y);
			layout.add(nSearchThreadsSlider, 1, y++);

			nSearchThreadsSlider.setEnabled(useSearchPoolCb.isSelected());

		}

		final JCheckBox useSubsearchPoolCb = useSearchPoolCb;
		final JSlider nSubsearchThreadsSlider = nSearchThreadsSlider;

		if (useSubsearchPoolCb != null) {
			useSubsearchPoolCb.addActionListener(e -> nSubsearchThreadsSlider.setEnabled(useSubsearchPoolCb
                    .isSelected()));
		}

		JCheckBox useIndexPoolCb = null;
		JSlider nIndexThreadsSlider = null;

		if (nProcessors >= 2) {
			useIndexPoolCb = new JCheckBox("Use index build pool",
					installerModel.isUseIndexBuildThreadPool());
			layout.add(useIndexPoolCb, 0, y++, 2, 1);

			label = new JLabel("No 2D index build threads");
			max = nProcessors;
			if (max > 64)
				max = 64;
			nIndexThreadsSlider = createThreadsSlider(max,
					installerModel.getnSubsearchThreads());
			size = nIndexThreadsSlider.getPreferredSize();
			size.width = 400;
			nIndexThreadsSlider.setPreferredSize(size);

			layout.add(label, 0, y);
			layout.add(nIndexThreadsSlider, 1, y++);

			nIndexThreadsSlider.setEnabled(useIndexPoolCb.isSelected());

		}

		final JCheckBox useIndexBuildPoolCb = useIndexPoolCb;
		final JSlider nIndexBuildThreadsSlider = nIndexThreadsSlider;

		if (useIndexBuildPoolCb != null) {
			useIndexBuildPoolCb.addActionListener(e -> nIndexBuildThreadsSlider.setEnabled(useIndexBuildPoolCb
                    .isSelected()));
		}

		JButton next = new JButton("Next Step");
		next.addActionListener(arg0 -> {
            // installerModel.setHost(hostEntry.getText());
            installerModel.setJavaVmMemory(memorySlider.getValue());
            installerModel.setUseCache(useCacheCb.isSelected());
            installerModel.setnThreads(nThreadsSlider.getValue());
            if (useSubsearchPoolCb != null) {
                installerModel
                        .setUseSubstructureSearchPool(useSubsearchPoolCb
                                .isSelected());
                installerModel.setnSubsearchThreads(nSubsearchThreadsSlider
                        .getValue());
            } else {
                installerModel.setUseSubstructureSearchPool(false);
            }
            if (useIndexBuildPoolCb != null) {
                installerModel
                        .setUseIndexBuildThreadPool(useIndexBuildPoolCb
                                .isSelected());
                installerModel
                        .setnIndexBuildThreads(nIndexBuildThreadsSlider
                                .getValue());
            }
            boolean ok = true;
            try {
                int cacheSize = Integer.parseInt(cacheSizeField.getText());
                installerModel.setCacheSize(cacheSize);
            } catch (Exception e) {
                installerModel.error("Cache size is not an integer", false);
                ok = false;
            }
            if (ok)
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

		if (installType == InstallType.ORACLE)
			return true;

		if (!installerModel.createServerConfig())
			return false;
		Os os = installerModel.getOs();
		if (os == Os.LINUX) {
			if (!installerModel.createLinuxCommand())
				return false;
			if (!installerModel.runLinuxServer())
				return false;
		} else if (os == Os.WINDOWS) {
			if (!installerModel.createWindowsCommand())
				return false;
			if (!installerModel.installService())
				return false;
		}
		Connection connection = installerModel.getTestConnection();
		if (!installerModel.checkCartridgeWorking(connection))
			return false;
		SqlUtil.closeConnection(connection);
		return true;
	}

	private JSlider createThreadsSlider(int max, int def) {
		final JSlider nThreadsSlider = new JSlider(2, max, def);
		if (max < 10)
			nThreadsSlider.setMajorTickSpacing(1);
		else if (max < 20)
			nThreadsSlider.setMajorTickSpacing(2);
		else if (max < 40)
			nThreadsSlider.setMajorTickSpacing(4);
		else if (max < 80)
			nThreadsSlider.setMajorTickSpacing(8);
		else
			nThreadsSlider.setMajorTickSpacing(16);
		nThreadsSlider.setMinorTickSpacing(1);
		nThreadsSlider.setPaintTicks(true);
		nThreadsSlider.setPaintLabels(true);
		return nThreadsSlider;
	}

}
