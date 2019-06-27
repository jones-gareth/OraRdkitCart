package com.cairn.rmi.installer;

import com.cairn.common.Util;
import com.cairn.common.ModelException;
import com.cairn.rmi.installer.InstallStep.InstallStepDialogFinishedListener;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.swing.*;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Installer for the data cartridge.
 *
 * @author gjones
 */
public class InstallerSupervisor implements InstallStepDialogFinishedListener {
    private volatile InstallerModel installerModel;
    private volatile InstallerWindow installerWindow;
    private final ExecutorService installerThread = Executors
            .newSingleThreadExecutor();
    private static final Logger logger = Logger
            .getLogger(InstallerSupervisor.class);

    private InstallerSupervisor() {
        super();

        Runtime.getRuntime().addShutdownHook(
                new Thread("installerThreadShutdown") {
                    @Override
                    public void run() {
                        installerThread.shutdownNow();
                    }
                });
    }

    private final InstallStep[] steps = new InstallStep[]{
            new SelectInstallTypeStep(),
            new CheckConnectionStep(),
            new CreateCartridgeUserStep(),
            new UserPermissionsStep(),
            new ConfigureRmiStep(), new LoadNCIStep()};

    // private final InstallStep[] steps = new InstallStep[] { new
    // ConfigureRmiStep() };

    private volatile InstallStep currentStep;
    private volatile int stepNo;

    /*
     * (non-Javadoc)
     *
     * @see
     * com.cairn.rmi.installer.InstallStep.InstallStepDialogFinishedListener
     * #dialogFinished()
     */
    public void dialogFinished() {
        finishCurrentStep();

    }

    private void run() throws ModelException {
        installerWindow = new InstallerWindow();
        installerModel = new InstallerModel();
        installerModel.addListener(installerWindow);

        stepNo = 0;
        startStep();
    }

    private void startStep() throws ModelException {
        Runnable runnable = () -> {
            try {
                if (stepNo == steps.length) {
                    logger.info("Install finished showing summary");
                    final FinishedScreen finishedScreen = new FinishedScreen(
                            installerModel);
                    SwingUtilities.invokeLater(() -> installerWindow.setDialog(finishedScreen
                            .finishScreen()));
                    return;
                }
                currentStep = null;
                while (currentStep == null)
                    currentStep = steps[stepNo++];
                currentStep.setInstallerModel(installerModel);
                currentStep
                        .setDialogFinishedListener(InstallerSupervisor.this);
                installerWindow.setBusy();
                boolean ok = currentStep.preDialogActions();
                installerWindow.clearBusy();
                if (!ok)
                    return;
                dialog();
            } catch (ModelException e) {
                throw new RuntimeException("ModelException starting step ",
                        e);
            }
        };
        installerThread.execute(runnable);
    }

    private void dialog() {
        SwingUtilities.invokeLater(() -> {
            try {
                JComponent dialog = currentStep.dialog();

                if (dialog == null) {
                    finishCurrentStep();
                } else {
                    installerWindow.setDialog(dialog);
                }
            } catch (ModelException e) {
                throw new RuntimeException(
                        "Model exception creating dialog", e);
            }
        });
    }

    private void finishCurrentStep() {
        Runnable runnable = () -> {
            try {
                installerWindow.removeDialog();
                installerWindow.setBusy();
                boolean ok = currentStep.postDialogActions();
                installerWindow.clearBusy();
                if (ok) {
                    int progress = (100 * stepNo) / steps.length;
                    installerWindow.setProgress(progress);
                    startStep();
                } else
                    dialog();
            } catch (ModelException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };
        installerThread.execute(runnable);
    }

    public static void main(String[] args) {

        String log4jFile = "log4j_console.properties";
        if (!(new File(log4jFile).exists()))
            System.err.println("Unable to find log4j config file " + log4jFile);
        else
            PropertyConfigurator.configure(log4jFile);

        final InstallerSupervisor installerSupervisor = new InstallerSupervisor();
        SwingUtilities.invokeLater(() -> {
            try {

                /*
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                    ex.printStackTrace();
                }
                */

                if (Util.isHiDpi()) {
                    Util.setDefaultFonts(24);
                }

                installerSupervisor.run();
            } catch (ModelException e) {
                throw new RuntimeException("Failed to run installer.", e);
            }
        });
    }
}
