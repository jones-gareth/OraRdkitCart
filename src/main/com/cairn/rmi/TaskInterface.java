package com.cairn.rmi;

/**
 * @author Gareth Jones
 * 
 * General Interface for tasks passed to RMI services
 *
 */
public interface TaskInterface {
		
	/**
	 * Run a task and return some result 
	 */
	Object submit(Object settings) throws TaskException;
	
	/**
	 * @return the settings that were used to initialize the task
	 */
	Object getSettings();
	
	/**
	 * @return the results from applying the task
	 */
	Object getResult();
}
