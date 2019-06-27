package com.cairn.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface for a remote service for handling tasks.
 * 
 * @author Gareth Jones
 * 
 */
public interface TaskManagerInterface extends Remote {
	// service name
	String NAME = "TaskManager";
	int PORT = 1099;
	String DEFAULT_RMI_SERVER_HOST = "localhost";

	/**
	 * Runs a task on the RMI server. In this case the task class must be on the
	 * client.
	 * 
	 * @param task
	 * @param settings
	 *            contains initialization settings for the task
	 * @return results of running the task
	 * @throws RemoteException
	 */
	Object submitTask(TaskInterface task, Object settings) throws RemoteException, TaskException;

	/**
	 * Runs a task on the server. In this case we pass the class name to the
	 * server and the task is created on the server, the task run and the
	 * results sent back to the client. In this case the task class is on the
	 * server and need not be on the client.
	 * 
	 * @param className
	 * @param settings
	 *            initialization settings for the task
	 * @return results
	 * @throws RemoteException
	 */
	Object submitTask(String className, Object settings) throws RemoteException, TaskException;

}
