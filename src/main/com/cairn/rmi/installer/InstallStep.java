package com.cairn.rmi.installer;

import javax.swing.JComponent;

import com.cairn.common.ModelException;

/**
 * Class for an install step. The step performs pre-dialog operations, displays
 * a dialog and performs post-dialog operations.
 * 
 * @author gjones
 * 
 */
public abstract class InstallStep {
	volatile InstallerModel installerModel;
	private volatile InstallStepDialogFinishedListener dialogFinishedListener;

	/**
	 * Performs any actions that are required before the dialog is submitted
	 * 
	 * @return
	 * @throws ModelException
	 */
	public abstract boolean preDialogActions() throws ModelException;

	/**
	 * Returns the dialog component.
	 * 
	 * @return
	 * @throws ModelException
	 */
	public abstract JComponent dialog() throws ModelException;

	/**
	 * Performs any actions that are required before the dialog is submitted
	 * 
	 * @return
	 * @throws ModelException
	 */
	public abstract boolean postDialogActions() throws ModelException;

	/**
	 * A class that needs to be notified that the dialog has finished needs to
	 * implement this
	 */
	public interface InstallStepDialogFinishedListener {
		void dialogFinished();
	}

	/**
	 * Tell another class that the dialog has been submitted
	 */
    void dialogFinished() {
		dialogFinishedListener.dialogFinished();
	}

	/**
	 * @return the installerModel
	 */
	public InstallerModel getInstallerModel() {
		return installerModel;
	}

	/**
	 * @param installerModel
	 *            the installerModel to set
	 */
	public void setInstallerModel(InstallerModel installerModel) {
		this.installerModel = installerModel;
	}

	/**
	 * @return the dialogFinishedListener
	 */
	public InstallStepDialogFinishedListener getDialogFinishedListener() {
		return dialogFinishedListener;
	}

	/**
	 * @param dialogFinishedListener
	 *            the dialogFinishedListener to set
	 */
	public void setDialogFinishedListener(
			InstallStepDialogFinishedListener dialogFinishedListener) {
		this.dialogFinishedListener = dialogFinishedListener;
	}

}
